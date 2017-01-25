package spatial.transform

import argon.transform.ForwardTransformer
import spatial.SpatialExp

import scala.collection.mutable.ArrayBuffer

/**
  * Inserts UnitPipe wrappers for primitive nodes in outer control nodes, along with registers for communication
  */
trait UnitPipeTransformer extends ForwardTransformer {
  val IR: SpatialExp
  import IR._

  override val name = "Unit Pipe Transformer"
  override val allowPretransform = true

  private class PipeStage(val isControl: Boolean) {
    val allocs = ArrayBuffer[Stm]()
    val nodes  = ArrayBuffer[Stm]()
    val regReads = ArrayBuffer[Stm]()

    def dynamicAllocs = allocs.filter{case TP(s,d) => isDynamicAllocation(s) }
    def staticAllocs  = allocs.filter{case TP(s,d) => !isDynamicAllocation(s) }

    def allocDeps = allocs.flatMap{case TP(s,d) => d.inputs }.toSet
    def deps = allocDeps ++ nodes.flatMap{case TP(s,d) => d.inputs }.toSet

    def dump(i: Int): Unit = {
      if (isControl) debugs(s"$i. Control Stage") else debugs(s"$i. Primitive Stage")
      debugs("Allocations: ")
      allocs.foreach{case TP(s,d) => debugs(c"  $s = $d [dynamic: ${isDynamicAllocation(d)}]")}
      debugs("Nodes: ")
      nodes.foreach{case TP(s,d) => debugs(c"  $s = $d")}
      debugs("Register reads: ")
      regReads.foreach{case TP(s,d) => debugs(c"  $s = $d")}
    }
  }
  private object PipeStage { def empty(isControl: Boolean) = new PipeStage(isControl) }

  private def regFromSym[T](s: Exp[T])(implicit ctx: SrcCtx): Exp[Reg[T]] = s.tp match {
    case x: Bits[_] =>
      val bits: Bits[T] = x.asInstanceOf[Bits[T]]
      val init = unwrap(bits.zero)(bits)
      reg_alloc[T](init)(bits, ctx)
    case _ => throw new UndefinedZeroException(s, s.tp)
  }
  private def regWrite[T](reg: Exp[Reg[T]], s: Exp[T])(implicit ctx: SrcCtx): Exp[Void] = s.tp match {
    case x: Bits[_] =>
      val bits: Bits[T] = x.asInstanceOf[Bits[T]]
      reg_write(reg, s, bool(true))(bits, ctx)
    case _ => throw new UndefinedZeroException(s, s.tp)
  }
  private def regRead[T](reg: Exp[Reg[T]])(implicit ctx: SrcCtx): Exp[T] = reg.tp.typeArguments.head match {
    case x: Bits[_] =>
      val bits: Bits[T] = x.asInstanceOf[Bits[T]]
      reg_read(reg)(bits, ctx)
    case _ => throw new UndefinedZeroException(reg, reg.tp.typeArguments.head)
  }

  private def wrapBlock[T:Staged](block: Block[T])(implicit ctx: SrcCtx): Exp[T] = inlineBlock(block, {stms =>
    debugs(s"Wrapping block with type ${typ[T]}")
    val stages = ArrayBuffer[PipeStage]()
    def curStage = stages.last
    stages += PipeStage.empty(true)

    stms foreach {case stm@TP(s,d) =>
      if (isPrimitiveNode(s)) {
        if (curStage.isControl) stages += PipeStage.empty(false)
        curStage.nodes += stm
      }
      else if (isRegisterRead(s)) {
        if (!curStage.isControl) curStage.nodes += stm
        curStage.regReads += stm
      }
      else if (isAllocation(s) || isGlobal(s)) {
        if (isPrimitiveAllocation(s) && !curStage.isControl) curStage.nodes += stm
        else curStage.allocs += stm
      }
      else {
        stages += PipeStage.empty(true)
        curStage.nodes += stm
      }
    }
    val deps = stages.toList.map(_.deps)

    stages.zipWithIndex.foreach{
      case (stage,i) if !stage.isControl =>
        val calculated = stage.nodes.map{case TP(s,d) => s}
        val needed = deps.drop(i+1).flatten
        val escaping = calculated.filter{sym => needed.contains(sym) && !isRegisterRead(sym) }
        val (escapingUnits, escapingValues) = escaping.partition{_.tp == VoidType}

        // Create registers for escaping primitive values
        val regs = escapingValues.map{sym => regFromSym(sym) }

        stage.staticAllocs.foreach(visitStm)
        Pipe {
          stage.nodes.foreach(visitStm)
          escapingValues.zip(regs).foreach{case (sym, reg) => regWrite(reg, f(sym)) }
          ()
        }

        // Outside inserted pipe, replace original escaping values with register reads
        escapingValues.zip(regs).foreach{case (sym,reg) => register(sym, regRead(reg)) }

        // Add (possibly redundant/unused) register reads
        stage.regReads.foreach(visitStm)

        // Add allocations which are known not to be used in the primitive logic in the inserted unit pipe
        stage.dynamicAllocs.foreach(visitStm)

      case (stage, i) if stage.isControl =>
        stage.nodes.foreach(visitStm)           // Zero or one control nodes
        stage.staticAllocs.foreach(visitStm)    // Allocations which cannot rely on reg reads
        stage.regReads.foreach(visitStm)        // Register reads
        stage.dynamicAllocs.foreach(visitStm)   // Allocations which can rely on reg reads
    }
    val result = typ[T] match {
      case VoidType => void
      case _ => f(block.result)
    }
    result.asInstanceOf[Exp[T]]
  })

  // All blocks requiring unit pipe insertion happen to be the first for that node.
  // If other cases should arise, may need to change this to a bitmask or an Int
  var wrapBlocks: Boolean = false
  var ctx: Option[SrcCtx] = None
  def withWrap[A](srcCtx: SrcCtx)(x: => A) = {
    val prevWrap = wrapBlocks
    val prevCtx = ctx
    wrapBlocks = true
    ctx = Some(srcCtx)
    val result = x
    wrapBlocks = prevWrap
    ctx = prevCtx
    result
  }

  override def apply[T:Staged](b: Block[T]): Exp[T] = {
    if (wrapBlocks) {
      wrapBlocks = false
      val result = wrapBlock(b)(mtyp(b.tp),ctx.get)
      result
    }
    else super.apply(b)
  }

  override def transform[T:Staged](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = {
    if (isOuterControl(lhs))
      withWrap(ctx){ super.transform(lhs, rhs) } // Mirror with wrapping enabled for the first block
    else
      super.transform(lhs, rhs)
  }
}
