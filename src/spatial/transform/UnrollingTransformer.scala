package spatial.transform

import argon.transform.ForwardTransformer
import spatial.SpatialExp
import org.virtualized.SourceContext

trait UnrollingTransformer extends ForwardTransformer {
  val IR: SpatialExp
  import IR._

  override val name = "Unrolling Transformer"

  var cloneFuncs: List[Exp[_] => Unit] = Nil
  def duringClone[T](func: Exp[_] => Unit)(blk: => T): T = {
    val prevCloneFuncs = cloneFuncs
    cloneFuncs ::= func
    val result = blk
    cloneFuncs = prevCloneFuncs
    result
  }
  def inReduction[T](blk: => T): T = duringClone{e => /*if (SpatialConfig.genCGRA) reduceType(e) = None*/ () }{ blk }

  var validBit: Option[Exp[Bool]] = None
  def withValid[T](valid: Exp[Bool])(blk: => T): T = {
    val prevValid = validBit
    validBit = Some(valid)
    val result = blk
    validBit = prevValid
    result
  }
  def globalValid = validBit.getOrElse(bool(true))

  /**
    * Helper class for unrolling
    * Tracks multiple substitution contexts in 'contexts' array
    **/
  case class Unroller(cchain: Exp[CounterChain], inds: List[Exp[Index]], isInnerLoop: Boolean) {
    // Don't unroll inner loops for CGRA generation
    val Ps = parFactorsOf(cchain).map{case Exact(c) => c.toInt } /*if (isInnerLoop && SpatialConfig.genCGRA) inds.map{i => 1} */
    val P = Ps.product
    val N = Ps.length
    val prods = List.tabulate(N){i => Ps.slice(i+1,N).product }
    val indices = Ps.map{p => List.fill(p){fresh[Index]}}
    val indexValids = Ps.map{p => List.fill(p){fresh[Bool]}}

    // Valid bits corresponding to each lane
    lazy val valids = List.tabulate(P){p =>
      val laneIdxValids = indexValids.zip(parAddr(p)).map{case (vec,i) => vec(i)}
      (laneIdxValids ++ validBit).reduce{(a,b) => bool_and(a,b) }
    }

    def size = P

    def parAddr(p: Int) = List.tabulate(N){d => (p / prods(d)) % Ps(d) }

    // Substitution for each duplication "lane"
    val contexts = Array.tabulate(P){p =>
      val inds2 = indices.zip(parAddr(p)).map{case (vec, i) => vec(i) }
      Map.empty[Exp[Any],Exp[Any]] ++ inds.zip(inds2)
    }

    def inLane[A](i: Int)(block: => A): A = {
      val save = subst
      withSubstRules(contexts(i)) {
        withValid(valids(i)) {
          val result = block
          // Retain only the substitutions added within this scope
          contexts(i) ++= subst.filterNot(save contains _._1)
          result
        }
      }
    }

    def map[A](block: Int => A): List[A] = List.tabulate(P){p => inLane(p){ block(p) } }

    def foreach(block: Int => Unit) { map(block) }

    def vectorize[T:Bits](block: Int => Exp[T]): Exp[Vector[T]] = IR.vectorize(map(block))

    // --- Each unrolling rule should do at least one of three things:
    // 1. Split a given vector as the substitution for the single original symbol
    /*def duplicate(s: Sym[Any], d: Def[Any]): List[Exp[Any]] = map{p =>
      val s2 = self_clone(s,d)
      register(s -> s2)
      s2
    }*/
    // 2. Make later stages depend on the given substitution across all lanes
    // NOTE: This assumes that the node has no meaningful return value (i.e. all are Pipeline or Unit)
    // Bad things can happen here if you're not careful!
    def split[T:Bits](orig: Sym[_], vec: Exp[Vector[T]]): List[Exp[T]] = map{p =>
      val element = vector_apply[T](vec, p)
      register(orig -> element)
      element
    }
    // 3. Create an unrolled clone of symbol s for each lane
    def unify(orig: Exp[_], unrolled: Exp[_]): List[Exp[_]] = {
      foreach{p => register(orig -> unrolled) }
      List(unrolled)
    }

    // Same symbol for all lanes
    def isCommon(e: Exp[_]) = contexts.map{p => f(e)}.forall{e2 => e2 == f(e)}
  }



}
