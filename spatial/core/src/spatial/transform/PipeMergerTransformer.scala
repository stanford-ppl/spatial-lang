package spatial.transform

import argon.core._
import argon.nodes._
import argon.transform.ForwardTransformer
import spatial.analysis.SpatialTraversal
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

/**
  * Merges pipe hierarchies that do not contain counters to avoid unit pipe and parallel pipe overheads
  */
case class PipeMergerTransformer(var IR: State) extends ForwardTransformer with SpatialTraversal {
  override val name = "Pipe Merger Transformer"
  //override val allowPretransform = true
  var enable: Option[Exp[Bit]] = None

  var consumedBy = HashMap[Exp[_], Exp[_]]() // Map for mapping a controller that was consumed to a controller who consumed it

  private def trace(ctrl: Exp[_]): Exp[_] = {
    if (consumedBy.contains(ctrl)) {
      dbgs(s"$ctrl -> ${consumedBy(ctrl)}")
      trace(consumedBy(ctrl))
    } else {
      ctrl
    }
  }
  private def consume(food: Exp[_], eater: Exp[_]): Unit = {
    dbgs(s"Rewriting $food to be part of $eater... \nTraceback :")
    consumedBy += (food -> trace(eater))
  }

  private def withEnable[T](en: Exp[Bit])(blk: => T)(implicit ctx: SrcCtx): T = {
    var prevEnable = enable
    dbgs(s"Enable was $enable")
    enable = Some(en) //Some(enable.map(bool_and(_,en)).getOrElse(en) )   TODO: Should this use ANDs?
    dbgs(s"Enable is now $enable")
    val result = blk
    enable = prevEnable
    result
  }

  private class PipeStage(val isControl: Boolean) {
    val allocs = ArrayBuffer[Stm]()
    val nodes  = ArrayBuffer[Stm]()
    val regReads = ArrayBuffer[Stm]()

    def dynamicAllocs = allocs.filter{case TP(s,d) => isDynamicAllocation(s) }
    def staticAllocs  = allocs.filter{case TP(s,d) => !isDynamicAllocation(s) }

    def allocDeps = allocs.flatMap{case TP(s,d) => d.inputs }.toSet
    def deps = allocDeps ++ nodes.flatMap{case TP(s,d) => d.inputs }.toSet

    def dump(i: Int): Unit = {
      if (isControl) dbgs(s"$i. Control Stage") else dbgs(s"$i. Primitive Stage")
      dbgs("Allocations: ")
      allocs.foreach{case TP(s,d) => dbgs(c"  $s = $d [dynamic: ${isDynamicAllocation(d)}]")}
      dbgs("Nodes: ")
      nodes.foreach{case TP(s,d) => dbgs(c"  $s = $d")}
      dbgs("Register reads: ")
      regReads.foreach{case TP(s,d) => dbgs(c"  $s = $d")}
    }
  }
  private object PipeStage { def empty(isControl: Boolean) = new PipeStage(isControl) }

  private def mergeBlock[T:Type](block: Block[T])(implicit ctx: SrcCtx): Exp[T] = inlineBlockWith(block, {stms =>
    dbgs(s"Wrapping block with type ${typ[T]}")
    val stages = ArrayBuffer[PipeStage]()
    def curStage = stages.last
    stages += PipeStage.empty(true)

    stms foreach {case stm@TP(s,d) =>
      dbgs(c"$s = $d [primitive:${isPrimitiveNode(s) || isInnerSwitch(s)}, regRead:${isRegisterRead(s)}, alloc:${isAllocation(s)}, primAlloc:${isPrimitiveAllocation(s)}]")
    }
    val deps = stages.toList.map(_.deps)

    dbgs("")

    stages.zipWithIndex.foreach{
      case (stage,i) if !stage.isControl =>
        val calculated = stage.nodes.map{case TP(s,d) => s}
        val innerDeps = calculated ++ deps.take(i).flatten // Things in this Unit Pipe
        val escaping = calculated.filter{sym => (sym == block.result || (sym.dependents diff innerDeps).nonEmpty) && !isRegisterRead(sym) }
        val (escapingUnits, escapingValues) = escaping.partition{_.tp == UnitType}

        val (escapingBits, escapingVars) = escapingValues.partition{sym => Bits.unapply(sym.tp).isDefined }

        dbgs(c"Stage #$i: ")
        dbgs(c"  Escaping symbols: ")
        escapingValues.foreach{e => dbgs(c"    ${str(e)}: ${e.dependents diff innerDeps}")}


        stage.staticAllocs.foreach(visitStm)
        val pipe = Pipe.op_unit_pipe(enable.toList, () => {
          isolateSubstScope { // We shouldn't be able to see any substitutions in here from the outside by default
            stage.nodes.foreach{q => 
              dbgs(s" PIPING OFF $q")
              visitStm(q)
            }

            // escapingBits.zip(regs).foreach { case (sym, reg) => regWrite(reg, f(sym)) }
            // escapingVars.zip(vars).foreach { case (sym, varr) => varWrite(varr, f(sym)) }
            unit
          }
        })
        levelOf(pipe) = InnerControl
        styleOf(pipe) = SeqPipe



      case (stage, i) if stage.isControl =>
        // stage.nodes.foreach(visitStm)           // Zero or one control nodes
        // stage.staticAllocs.foreach(visitStm)    // Allocations which cannot rely on reg reads (and occur AFTER nodes)
        // stage.regReads.foreach(visitStm)        // Register reads
        // stage.dynamicAllocs.foreach(visitStm)   // Allocations which can rely on reg reads
    }
    val result = typ[T] match {
      case UnitType => unit
      case _ => f(block.result)
    }
    result.asInstanceOf[Exp[T]]
  })


  var mergeBlocks: List[Boolean] = Nil
  var ctx: Option[SrcCtx] = None
  var inAccel = false
  var controlStyle: Option[ControlStyle] = None
  var controlLevel: Option[ControlLevel] = None
  def inControl[T](lhs: Exp[_])(block: => T): T = {
    val prevStyle = controlStyle
    val prevLevel = controlLevel
    controlStyle = styleOf.get(lhs)
    controlLevel = levelOf.get(lhs)
    val result = block
    controlStyle = prevStyle
    controlLevel = prevLevel
    result
  }

  def withMerge[A](wrap: List[Boolean], srcCtx: SrcCtx)(x: => A) = {
    val prevWrap = mergeBlocks
    val prevCtx = ctx

    mergeBlocks = wrap
    ctx = Some(srcCtx)
    val result = x

    mergeBlocks = prevWrap
    ctx = prevCtx
    result
  }

  // we visit and reflect everything, and any node reflected that is not inside a block is put into the parent block.  This encloses things in a new scope 
  override protected def inlineBlock[T](b: Block[T]): Exp[T] = {
    val doWrap = mergeBlocks.headOption.getOrElse(false)
    if (mergeBlocks.nonEmpty) mergeBlocks = mergeBlocks.drop(1)
    dbgs(c"Transforming Block $b [$mergeBlocks]")
    if (doWrap) {
      mergeBlock(b)(mtyp(b.tp),ctx.get)
    }
    else super.inlineBlock(b)
  }


  override def transform[T:Type](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = rhs match {
    // Only insert Unit Pipes into bodies of switch cases in outer scope contexts
    case Hwblock(body,isForever) => inControl(lhs) {
      dbgs(s" TEST: entering $lhs")
      inAccel = true
      val wrapEnables = if (isOuterControl(lhs)) List(true) else Nil
      val lhs2 = withMerge(wrapEnables, ctx) { super.transform(lhs, rhs) }
      inAccel = false
      lhs2
    }

    case op@UnitPipe(ens, func) if isOuterControl(lhs) => 
      dbgs(s" TEST: entering $lhs")
      if (childrenOf(lhs).length == 1) { // Vertical collapse
        childrenOf(lhs).head match {
          case Def(ParallelPipe(ens, func)) => 
            dbgs(s"Vertical collapse of $lhs")
            consume(childrenOf(lhs).head, lhs)
            withMerge(Nil, ctx){ super.transform(lhs, rhs) }
          case Def(UnitPipe(ens, func)) => 
            dbgs(s"Vertical collapse of $lhs")
            consume(childrenOf(lhs).head, lhs)
            withMerge(Nil, ctx){ super.transform(lhs, rhs) }
          case _ => withMerge(Nil, ctx){ super.transform(lhs, rhs) }
        }
      } else { // Need to look inside blocks
        withMerge(Nil, ctx){ super.transform(lhs, rhs) }
      }
      

    case op@ParallelPipe(ens, func) if isOuterControl(lhs) => 
      dbgs(s" TEST: entering $lhs")
      val mergeOK = childrenOf(lhs).map{ child => 
        child match {
          case Def(ParallelPipe(ens, func)) => true
          case Def(UnitPipe(ens, func)) => true
          case _ => false
        }
      }.reduce{_&&_}
      if (mergeOK) { // Horizontal collapse
        dbgs(s"Horizontal collapse of $lhs")
        childrenOf(lhs).map{ child => consume(child, lhs)}
      }
      // Convert lhs to unit pipe
      withMerge(Nil, ctx){ super.transform(lhs, rhs) }

    // // Add enables to unit pipes inserted inside of switches
    // case op@Switch(body,selects,cases) if isOuterControl(lhs) => inControl(lhs) {
    //   val selects2 = f(selects)
    //   val body2 = stageHotBlock {
    //     selects2.zip(cases).foreach {
    //       case (en, s: Sym[_]) => withEnable(en){ visitStm(stmOf(s)) }
    //       case (en, c) => f(c)
    //     }
    //     f(body.result)
    //   }
    //   val cases2 = f(cases)
    //   val lhs2 = Switches.op_switch(body2, selects2, cases2)
    //   transferMetadata(lhs, lhs2)
    //   lhs2
    // }

    // // Insert unit pipes in outer switch cases with multiple controllers
    // case op@SwitchCase(body) if isOuterControl(lhs) => inControl(lhs) {
    //   val controllers = getControlNodes(body)
    //   val primitives = getPrimitiveNodes(body)
    //   if (controllers.length > 1 || (primitives.nonEmpty && controllers.nonEmpty)) {
    //     wrapSwitchCase(lhs, body)(mtyp(op.mT),ctx)
    //   }
    //   else {
    //     withMerge(List(true), ctx){ super.transform(lhs, rhs) }
    //   }
    // }



    // // Only insert unit pipes in if-then-else statements if in Accel and in an outer controller
    // /*case op @ IfThenElse(cond,thenp,elsep) if inAccel && controlLevel.contains(OuterControl) =>
    //   withMerge(List(true,true), ctx) { super.transform(lhs, rhs) }*/

    // case _:StateMachine[_] if isOuterControl(lhs) => inControl(lhs) {
    //   withMerge(List(false, true, false), ctx) { super.transform(lhs, rhs) } // Wrap the second block only
    // }

    case _ if isOuterControl(lhs) => inControl(lhs) {
      Console.println(s"visiting outer control $lhs")
      withMerge(List(true), ctx) { super.transform(lhs, rhs) } // Mirror with wrapping enabled for the first block
    }

    case _ if isControlNode(lhs) => inControl(lhs) {
      Console.println(s"visiting ner control in$lhs")
      withMerge(Nil, ctx){ super.transform(lhs, rhs) }
    }

    case _ =>
      withMerge(Nil, ctx){ super.transform(lhs, rhs) } // Disable wrapping at this level
  }
}
