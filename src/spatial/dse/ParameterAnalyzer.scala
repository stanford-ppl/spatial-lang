package spatial.dse

import spatial.analysis.SpatialTraversal

trait ParameterAnalyzer extends SpatialTraversal {
  import IR._

  private type RRange = scala.collection.immutable.Range

  override val name = "Parameter Analyzer"
  override val recurse = Always

  // Sort of arbitrary limits right now
  val MIN_TILE_SIZE = 96    // words
  val MAX_TILE_SIZE = 96000 // words
  val MAX_TILE      = 51340 // words, unused

  val MAX_PAR_FACTOR = 192  // duplications
  val MAX_OUTER_PAR  = 15

  var tileSizes  = Set[Param[Index]]()
  var parFactors = Set[Param[Index]]()
  var restrict   = Set[Restrict]()
  var innerLoop  = false

  var ignoreParams = Set[Param[Index]]()

  def collectParams(x: Exp[_]): Seq[Param[_]] = {
    def dfs(frontier: Seq[Exp[_]]): Seq[Param[_]] = frontier.flatMap{
      case s: Param[_] => Seq(s)
      case Def(d) => dfs(d.inputs)
      case _ => Nil
    }
    dfs(Seq(x))
  }
  def onlyIndex(x: Seq[Const[_]]): Seq[Param[Index]] = {
    x.collect{case p: Param[_] if isInt32Type(p.tp) => p.asInstanceOf[Param[Index]] }
  }

  def setRange(x: Param[Index], min: Int, max: Int, stride: Int = 1): Unit = domainOf(x) match {
    case Some((start,end,step)) =>
      domainOf(x) = (Math.max(min,start), Math.min(max,end), Math.max(stride,step))
    case None =>
      domainOf(x) = (min,max,stride)
  }

  // TODO: Should have better analysis for this
  def isParallelizableLoop(e: Exp[_]): Boolean = {
    (isInnerPipe(e) || isMetaPipe(e)) && !childrenOf(e).exists(isDRAMTransfer)
  }

  override protected def postprocess[S: Staged](block: Block[S]) = {
    val params = tileSizes ++ parFactors
    params.foreach{p => if (domainOf(p).isEmpty) domainOf(p) = (1, 1, 1) }
    super.postprocess(block)
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]) = rhs match {
    case FIFONew(c@Exact(_)) =>
      warn(u"FIFO $lhs has parametrized size. Tuning of FIFO depths is not yet supported.")
      ignoreParams ++= onlyIndex(collectParams(c))

    case SRAMNew(dims) =>
      val params = dims.flatMap(collectParams)
      val tsizes = onlyIndex(params)
      dbg(u"Found SRAM with parametrized in dimensions: $tsizes")

      (tsizes intersect dims).foreach{p =>
        if (dims.indexOf(p) == dims.length - 1) setRange(p, 1, MAX_TILE_SIZE, MIN_TILE_SIZE)
        else setRange(p, 1, MAX_TILE_SIZE)
      }
      tileSizes ++= tsizes

    case CounterNew(_,_,_,par) =>
      val pars = onlyIndex(collectParams(par))
      pars.foreach{p => setRange(p, 1, MAX_PAR_FACTOR) }
      // TODO: Restrictions on counter parallelization
      parFactors ++= pars

    case e: OpForeach =>
      val pars = onlyIndex(parFactorsOf(e.cchain))
      if (!isParallelizableLoop(lhs)) pars.foreach{p => setRange(p, 1, 1) }
      else if (!isInnerPipe(lhs)) pars.foreach{p => setRange(p, 1, MAX_OUTER_PAR) }

    case e: OpReduce[_] =>
      val pars = onlyIndex(parFactorsOf(e.cchain))
      if (!isParallelizableLoop(lhs)) pars.foreach{p => setRange(p, 1, 1) }
      else if (!isInnerPipe(lhs)) pars.foreach{p => setRange(p, 1, MAX_OUTER_PAR) }

    case e: OpMemReduce[_,_] =>
      val opars = onlyIndex(parFactorsOf(e.cchainMap))
      val ipars = onlyIndex(parFactorsOf(e.cchainRed))
      if (!isParallelizableLoop(lhs)) opars.foreach{p => setRange(p, 1, 1) }

    case _ => super.visit(lhs, rhs)
  }

}
