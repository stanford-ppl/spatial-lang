package spatial.transform

import argon.core._
import argon.nodes._
import argon.transform.ForwardTransformer
import spatial.aliases._
import spatial.nodes._
import spatial.metadata._
import spatial.utils._

trait AffineAccessTransformer extends ForwardTransformer {
  override val name = "Affine Access Transformer"

  override val allowUnsafeSubst: Boolean = true // Required to allow, e.g. SRAM1 -> SRAM2
  override def shouldRun = spatialConfig.useAffine

  def allocSRAM[T:Type:Bits](dims: Seq[Exp[Index]])(implicit ctx: SrcCtx): Exp[SRAM[T]] = dims.length match {
    case 1 => SRAM.alloc[T,SRAM1](dims:_*)
    case 2 => SRAM.alloc[T,SRAM2](dims:_*)
    case 3 => SRAM.alloc[T,SRAM3](dims:_*)
    case 4 => SRAM.alloc[T,SRAM4](dims:_*)
    case 5 => SRAM.alloc[T,SRAM5](dims:_*)
    case _ =>
      implicit val sramType: Type[SRAM5[T]] = new Type[SRAM5[T]] with SRAMType[T] {
        override def wrapped(x: Exp[SRAM5[T]]) = throw new Exception("Cannot wrap internal SRAM type")
        override def typeArguments = List(child)
        override def stagedClass = classOf[SRAM5[T]]
        override def child: Type[T] = typ[T]
      }
      SRAM.alloc[T,SRAM5](dims:_*)(typ[T],bits[T],sramType,ctx,state)
  }
  def loadSRAM[T:Type:Bits](sram: Exp[SRAM[_]], dims: Seq[Exp[Index]], is: Seq[Exp[Index]], en: Exp[Bit])(implicit ctx: SrcCtx) = {
    SRAM.load(sram.asInstanceOf[Exp[SRAM[T]]],dims,is,FixPt.int32s(0),en)
  }
  def storeSRAM[T:Type:Bits](sram: Exp[SRAM[_]], dims: Seq[Exp[Index]], is: Seq[Exp[Index]], data: Exp[T], en: Exp[Bit])(implicit ctx: SrcCtx): Exp[MUnit] = {
    SRAM.store(sram.asInstanceOf[Exp[SRAM[T]]],dims,is,FixPt.int32s(0),data,en)
  }

  def convertDims(is: Seq[Exp[Index]], origDims: Seq[Exp[Index]], newDims: Seq[Exp[Index]])(implicit ctx: SrcCtx): Seq[Exp[Index]] = {
    val flat = flatIndex(wrap(is), wrap(origDims))
    val dims2 = wrap(newDims)
    val strides2 = dimsToStrides(dims2)

    val is2 = dims2.zip(strides2).map{case (d,s) => (flat/s) % d }
    unwrap(is2)
  }

  var dups: Map[(Exp[_],Int), Exp[SRAM[_]]] = Map.empty
  var dispatchSubst: Map[Exp[SRAM[_]], Map[Int,Int]] = Map.empty

  override def transform[T:Type](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = rhs match {
    case op @ SRAMNew(dims) =>

      val duplicates = duplicatesOf(lhs).zipWithIndex
                .groupBy(_._1.strides)
                .toList.zipWithIndex
                .flatMap{case ((strides,dispatches),i) =>
                  val dims = stridesToDims(lhs, strides).map{x => FixPt.int32s(x) }
                  val sram = allocSRAM(dims)(mtyp(op.mT),mbits(op.bT),lhs.ctx)
                  lhs.name.foreach{n => sram.name = Some(n + s"_dup$i") }
                  duplicatesOf(sram) = dispatches.map(_._1)

                  dispatchSubst += sram -> dispatches.map(_._2).zipWithIndex.toMap

                  dispatches.map{case (_,d) => (lhs,d) -> sram }
                }

      dups ++= duplicates
      duplicates.head._2.asInstanceOf[Exp[T]]

    case op @ SRAMLoad(mem, _, is, _, en) =>
      val dispatches = dispatchOf(lhs,mem)
      if (dispatches.size > 1 || dispatches.isEmpty) {
        bug(s"Number of duplicates for load was != 1")
        bug(s"${str(lhs)}")
      }
      val dispatch = dispatches.headOption.getOrElse(0)
      val mem2 = dups.getOrElse((mem,dispatch), f(mem))
      val dims2 = stagedDimsOf(mem2)

      val lhs2 = if (is.length != dims2.length) {
        val is2 = convertDims(f(is), f(stagedDimsOf(mem)), dims2)
        val lhs2 = loadSRAM(mem2,dims2,is2,f(en))(mtyp(op.mT),mbits(op.bT),ctx).asInstanceOf[Exp[T]]
        transferMetadata(lhs, lhs2)
        lhs2
      }
      else {
        // Use standard mirroring rules if the dimensions match
        withSubstScope(mem -> mem2){ super.transform(lhs, rhs) }
      }

      remapDispatches(lhs2, mem2, dispatchSubst(mem2))
      lhs2

    case op@SRAMStore(mem, _, is, _, data, en) =>
      val duplicates = dispatchOf(lhs,mem).map{i => (dups.getOrElse((mem,i), f(mem)), i) }.map{case (mem2,i) =>
        val dims2 = stagedDimsOf(mem2)
        val lhs2 = if (is.length != dims2.length) {
          val is2 = convertDims(f(is),f(stagedDimsOf(mem)),dims2)
          val lhs2 = storeSRAM(mem2,dims2,is2,f(data),f(en))(mtyp(op.mT),mbits(op.bT),ctx)
          transferMetadata(lhs, lhs2)
          lhs2
        }
        else {
          withSubstScope(mem -> mem2){ super.transform(lhs, rhs) }
        }
        remapDispatches(lhs2, mem2, dispatchSubst(mem2))
        lhs2
      }
      duplicates.head.asInstanceOf[Exp[T]]

    case _ => super.transform(lhs, rhs)
  }
}
