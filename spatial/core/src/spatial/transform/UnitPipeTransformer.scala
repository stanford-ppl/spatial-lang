package spatial.transform

import argon.transform.ForwardTransformer
import spatial.SpatialExp
import spatial.api.ControllerApi

import scala.collection.mutable.ArrayBuffer

/**
  * Inserts UnitPipe wrappers for primitive nodes in outer control nodes, along with registers for communication
  */
trait UnitPipeTransformer extends ForwardTransformer {
  val IR: SpatialExp with ControllerApi
  import IR._

  override val name = "Unit Pipe Transformer"
  //override val allowPretransform = true

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

  private def regFromSym[T](s: Exp[T])(implicit ctx: SrcCtx): Exp[Reg[T]] = s.tp match {
    case Bits(bits) =>
      val init = unwrap(bits.zero)(s.tp)
      reg_alloc[T](init)(s.tp, bits, ctx)
    case _ => throw new UndefinedZeroException(s, s.tp)
  }
  private def regWrite[T](reg: Exp[Reg[T]], s: Exp[T])(implicit ctx: SrcCtx): Exp[Void] = s.tp match {
    case Bits(bits) =>
      reg_write(reg, s, bool(true))(s.tp, bits, ctx)
    case _ => throw new UndefinedZeroException(s, s.tp)
  }
  private def regRead[T](reg: Exp[Reg[T]])(implicit ctx: SrcCtx): Exp[T] = reg.tp.typeArguments.head match {
    case tp@Bits(bits) =>
      reg_read(reg)(mtyp(tp), mbits(bits), ctx)
    case _ => throw new UndefinedZeroException(reg, reg.tp.typeArguments.head)
  }

  private def wrapBlock[T:Type](block: Block[T])(implicit ctx: SrcCtx): Exp[T] = inlineBlock(block, {stms =>
    dbgs(s"Wrapping block with type ${typ[T]}")
    val stages = ArrayBuffer[PipeStage]()
    def curStage = stages.last
    stages += PipeStage.empty(true)

    stms foreach {case stm@TP(s,d) =>
      dbgs(c"$s = $d [primitive:${isPrimitiveNode(s)}, regRead:${isRegisterRead(s)}, alloc:${isAllocation(s)}, primAlloc:${isPrimitiveAllocation(s)}]")
      if (isPrimitiveNode(s)) {
        if (curStage.isControl) stages += PipeStage.empty(false)
        curStage.nodes += stm
      }
      else if (isStateless(s) && !isAllocation(s)) {
        if (!curStage.isControl) curStage.nodes += stm
        curStage.regReads += stm
      }
      else if (isStateless(s) || isAllocation(s) || isGlobal(s)) {
        if (isPrimitiveAllocation(s) && !curStage.isControl) curStage.nodes += stm
        else curStage.allocs += stm
      }
      else {
        stages += PipeStage.empty(true)
        curStage.nodes += stm
      }
    }
    val deps = stages.toList.map(_.deps)

    stages.zipWithIndex.foreach{case (stage,i) => stage.dump(i) }
    dbgs("")

    stages.zipWithIndex.foreach{
      case (stage,i) if !stage.isControl =>
        val calculated = stage.nodes.map{case TP(s,d) => s}
        val innerDeps = calculated ++ deps.take(i).flatten // Things in this Unit Pipe
        val escaping = calculated.filter{sym => (sym.dependents diff innerDeps).nonEmpty && !isRegisterRead(sym) }
        val (escapingUnits, escapingValues) = escaping.partition{_.tp == VoidType}

        dbgs(c"Stage #$i: ")
        dbgs(c"  Escaping symbols: ")
        escapingValues.foreach{e => dbgs(c"    ${str(e)}: ${e.dependents diff innerDeps}")}

        // Create registers for escaping primitive values
        val regs = escapingValues.map{sym => regFromSym(sym) }

        stage.staticAllocs.foreach(visitStm)
        val pipe = op_unit_pipe(Nil, {
          isolateSubstScope { // We shouldn't be able to see any substitutions in here from the outside by default
            stage.nodes.foreach(visitStm)
            escapingValues.zip(regs).foreach { case (sym, reg) => regWrite(reg, f(sym)) }
            void
          }
        })
        levelOf(pipe) = InnerControl
        styleOf(pipe) = InnerPipe

        // Outside inserted pipe, replace original escaping values with register reads
        escapingValues.zip(regs).foreach{case (sym,reg) => register(sym, regRead(reg)) }

        // Add (possibly redundant/unused) register reads
        stage.regReads.foreach(visitStm)

        // Add allocations which are known not to be used in the primitive logic in the inserted unit pipe
        stage.dynamicAllocs.foreach(visitStm)

        dbgs(c"  Created registers: $regs")


      case (stage, i) if stage.isControl =>
        stage.nodes.foreach(visitStm)           // Zero or one control nodes
        stage.staticAllocs.foreach(visitStm)    // Allocations which cannot rely on reg reads (and occur AFTER nodes)
        stage.regReads.foreach(visitStm)        // Register reads
        stage.dynamicAllocs.foreach(visitStm)   // Allocations which can rely on reg reads
    }
    val result = typ[T] match {
      case VoidType => void
      case _ => f(block.result)
    }
    result.asInstanceOf[Exp[T]]
  })


  var wrapBlocks: List[Boolean] = Nil
  var ctx: Option[SrcCtx] = None

  def withWrap[A](wrap: List[Boolean], srcCtx: SrcCtx)(x: => A) = {
    val prevWrap = wrapBlocks
    val prevCtx = ctx

    wrapBlocks = wrap
    ctx = Some(srcCtx)
    val result = x

    wrapBlocks = prevWrap
    ctx = prevCtx
    result
  }

  override def apply[T:Type](b: Block[T]): Exp[T] = {
    val doWrap = wrapBlocks.headOption.getOrElse(false)
    if (wrapBlocks.nonEmpty) wrapBlocks = wrapBlocks.drop(1)
    dbgs(c"Transforming Block $b [$wrapBlocks]")
    if (doWrap) {
      val result = wrapBlock(b)(mtyp(b.tp),ctx.get)
      result
    }
    else super.apply(b)
  }

  override def transform[T:Type](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = rhs match {
    // Only insert Unit Pipes into bodies of switch cases in outer scope contexts
    case op@SwitchCase(cond,body) if isOuterControl(lhs) =>
      withWrap(List(true),ctx){ super.transform(lhs,rhs) }

    case StateMachine(en,start,notDone,action,nextState,state) if isOuterControl(lhs) =>
      withWrap(List(false,true,false),ctx){ super.transform(lhs,rhs) } // Wrap the second block only

    case _ if isOuterControl(lhs) =>
      withWrap(List(true),ctx){ super.transform(lhs, rhs) } // Mirror with wrapping enabled for the first block

    case _ =>
      withWrap(Nil, ctx){ super.transform(lhs, rhs) } // Disable wrapping at this level
  }
}
