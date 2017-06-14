/*package spatial.spec

import org.virtualized.CurriedUpdate

trait SRAMViewOps extends SRAMOps { this: SpatialOps =>
  type SRAMView[T] <: SRAMViewOps[T]

  protected trait SRAMViewOps[T] {
    def apply(indices: Index*)(implicit ctx: SrcCtx): T
    @CurriedUpdate
    def update(indices: Index*)(value: T): MUnit

    def load(dram: DRAMDenseTile[T])(implicit ctx: SrcCtx): MUnit
    def gather(dram: DRAMSparseTile[T])(implicit ctx: SrcCtx): MUnit
  }

  implicit def sram2sramview[T:Bits](sram: SRAM[T])(implicit ctx: SrcCtx): SRAMView[T]
  implicit def sramViewIsMemory[T:Bits]: Mem[T, SRAMView]
}

trait SRAMViewApi extends SRAMViewOps with SRAMApi { this: SpatialApi => }

trait SRAMViewExp extends SRAMViewOps with SRAMExp { this: SpatialExp =>

  case class SRAMView[T:Bits](sram: Sym[SRAMView[T]]) extends SRAMViewOps[T] {
    /*private[spec] def ofsAndDims(indices: Seq[Index])(implicit ctx: SrcCtx): (Index, Seq[Index]) = {
      if (indices.length != ranges.length) new DimensionMismatchError(sram, ranges.length, indices.length)(ctx)

      val as: Seq[Index] = ranges.map(_.step.getOrElse(lift[Int,Index](1)))
      val bs: Seq[Index] = ranges.map(_.start.getOrElse(lift[Int,Index](0)))
      val ds: Seq[Index] = wrap(stagedDimsOf(sram))
      val ofs: Index = flatIndex(bs, ds)

      val dims = as.zip(ds).map{case (a,b) => a * b}
      (ofs, dims)
    }*/

    def apply(indices: Index*)(implicit ctx: SrcCtx): T = {
      val (ofs, dims) = ofsAndDims(indices)
      wrap(sram_load(this.sram, unwrap(dims), unwrap(indices), ofs.s))
    }
    @CurriedUpdate
    override def update(indices: Index*)(value: T): MUnit = {
      val (ofs, dims) = ofsAndDims(indices)
      wrap(sram_store(this.sram, unwrap(dims), unwrap(indices), ofs.s, value.s, bool(true)))
    }

    def load(dram: DRAMDenseTile[T])(implicit ctx: SrcCtx): MUnit = copy_burst(dram, this, isLoad = true)
    def gather(dram: DRAMSparseTile[T])(implicit ctx: SrcCtx): MUnit = copy_sparse(dram, this, isLoad = true)
  }

  implicit def sram2sramview[T:Bits](sram: SRAM[T])(implicit ctx: SrcCtx): SRAMView[T] = {
    SRAMView(sram.s, dimsOf(sram.s).map{d => Range(0, d, 1, 1) } )
  }


  case class SRAMViewIsMemory[T]()(implicit val bits: Bits[T]) extends Mem[T, SRAMView] {
    def load(mem: SRAMView[T], is: Seq[Index], en: Bit)(implicit ctx: SrcCtx): T = mem.apply(is: _*)
    def store(mem: SRAMView[T], is: Seq[Index], v: T, en: Bit)(implicit ctx: SrcCtx): MUnit = {
      val (ofs, dims) = mem.ofsAndDims(is)
      wrap(sram_store(mem.sram, unwrap(dims), unwrap(is), ofs.s, bits.unwrapped(v), en.s))
    }
    def iterators(mem: SRAMView[T])(implicit ctx: SrcCtx): Seq[Counter] = mem.ranges.map(range2counter)
    //def empty(dims: Seq[Index])(implicit ctx: SrcCtx, nT: Bits[T]): SRAMView[T]
  }
  implicit def sramViewIsMemory[T:Bits]: Mem[T, SRAMView] = SRAMViewIsMemory[T]()(bits[T])

  /** IR Nodes **/
  case class SRAMViewNew[T:Bits](sram: Sym[SRAM[T]], ranges: Seq[Range]) extends Op[SRAMView[T]] {
    def mirror(f:Tx) =
  }
  case class SRAMViewLoad[T:Bits](view: Sym[SRAMView[T]], indices: Seq[Sym[Index]]) extends Op[T] {
    def mirror(f:Tx) =
  }
  case class SRAMViewStore[T:Bits](view: Sym[SRAMView[T]], indices: Seq[Sym[Index]], value: Sym[T], en: Sym[Bit]) extends Op[MUnit] {
    def mirror(f:Tx) =
  }

  def sram_view_alloc[T:Bits](sram: Sym[SRAM[T]], ranges: Seq[Range])(implicit ctx: SrcCtx) = {
    stageMutable(SRAMViewNew,
  }
}*/
