package spatial.api

import org.virtualized.CurriedUpdate
import org.virtualized.SourceContext
import argon.core.Staging
import spatial.SpatialExp

trait RegisterFileApi extends RegisterFileExp {
  this: SpatialExp =>
}

trait RegisterFileExp extends Staging with SRAMExp {
  this: SpatialExp =>

  case class RegFile[T:Staged:Bits](s: Exp[RegFile[T]]) {
    def apply(indices: Index*)(implicit ctx: SrcCtx): T = wrap(regfile_load(s, unwrap(indices), bool(true)))

    @CurriedUpdate
    def update(indices: Index*)(data: T): Void = {
      Void(regfile_store(s, unwrap(indices), data.s, bool(true)))
    }

    def <<=(data: T)(implicit ctx: SrcCtx): Void = wrap(regfile_shiftin(s, Seq(int32(0)), 0, data.s, bool(true)))
    def <<=(data: Vector[T])(implicit ctx: SrcCtx): Void = wrap(par_regfile_shiftin(s, Seq(int32(0)), 0, data.s, bool(true)))

    def apply(i: Index, y: Wildcard)(implicit ctx: SrcCtx) = {
      if (stagedDimsOf(s).length != 2) error(ctx, s"Cannot view a ${stagedDimsOf(s).length}-dimensional register file in 2 dimensions.")
      RegFileView(s, Seq(i,lift[Int,Index](0)), 1)
    }
    def apply(y: Wildcard, i: Index)(implicit ctx: SrcCtx) = {
      if (stagedDimsOf(s).length != 2) error(ctx, s"Cannot view a ${stagedDimsOf(s).length}-dimensional register file in 2 dimensions.")
      RegFileView(s, Seq(lift[Int,Index](0),i), 0)
    }
  }

  case class RegFileView[T:Staged:Bits](s: Exp[RegFile[T]], i: Seq[Index], dim: Int) {
    def <<=(data: T)(implicit ctx: SrcCtx): Void = wrap(regfile_shiftin(s, unwrap(i), dim, data.s, bool(true)))
    def <<=(data: Vector[T])(implicit ctx: SrcCtx): Void = wrap(par_regfile_shiftin(s, unwrap(i), dim, data.s, bool(true)))
  }


  object RegFile {
    def apply[T:Staged:Bits](dims: Index*)(implicit ctx: SrcCtx): RegFile[T] = wrap(regfile_new[T](unwrap(dims)))
  }

  /** Type classes **/
  // Staged
  case class RegFileType[T:Bits](child: Staged[T]) extends Staged[RegFile[T]] {
    override def unwrapped(x: RegFile[T]) = x.s
    override def wrapped(x: Exp[RegFile[T]]) = RegFile(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[RegFile[T]]
    override def isPrimitive = false
  }
  implicit def regFileType[T:Staged:Bits]: Staged[RegFile[T]] = RegFileType(typ[T])


  // Mem
  class RegFileIsMemory[T:Staged:Bits] extends Mem[T, RegFile] {
    def load(mem: RegFile[T], is: Seq[Index], en: Bool)(implicit ctx: SrcCtx): T = {
      wrap(regfile_load(mem.s, unwrap(is), en.s))
    }

    def store(mem: RegFile[T], is: Seq[Index], data: T, en: Bool)(implicit ctx: SrcCtx): Void = {
      wrap(regfile_store(mem.s, unwrap(is), data.s, en.s))
    }
    def iterators(mem: RegFile[T])(implicit ctx: SrcCtx): Seq[Counter] = {
      stagedDimsOf(mem.s).map{d => Counter(0, wrap(d), 1, 1) }
    }
  }
  implicit def regfileIsMemory[T:Staged:Bits]: Mem[T, RegFile] = new RegFileIsMemory[T]



  /** IR Nodes **/
  case class RegFileNew[T:Staged:Bits](dims: Seq[Exp[Index]]) extends Op[RegFile[T]] {
    def mirror(f:Tx) = regfile_new[T](f(dims))
    val mT = typ[T]
  }

  case class RegFileLoad[T:Staged:Bits](
    reg:  Exp[RegFile[T]],
    inds: Seq[Exp[Index]],
    en:   Exp[Bool]
  ) extends EnabledOp[T](en) {
    def mirror(f:Tx) = regfile_load(f(reg),f(inds),f(en))
    override def aliases = Nil
    val mT = typ[T]
    val bT = bits[T]
  }

  case class RegFileStore[T:Staged:Bits](
    reg:  Exp[RegFile[T]],
    inds: Seq[Exp[Index]],
    data: Exp[T],
    en:   Exp[Bool]
  ) extends EnabledOp[Void](en) {
    def mirror(f:Tx) = regfile_store(f(reg),f(inds),f(data),f(en))
    val mT = typ[T]
    val bT = bits[T]
  }

  case class RegFileShiftIn[T:Staged:Bits](
    reg:  Exp[RegFile[T]],
    inds: Seq[Exp[Index]],
    dim:  Int,
    data: Exp[T],
    en:   Exp[Bool]
  ) extends EnabledOp[Void](en) {
    def mirror(f:Tx) = regfile_shiftin(f(reg),f(inds),dim,f(data),f(en))
    val mT = typ[T]
    val bT = bits[T]
  }

  case class ParRegFileShiftIn[T:Staged:Bits](
    reg:  Exp[RegFile[T]],
    inds: Seq[Exp[Index]],
    dim:  Int,
    data: Exp[Vector[T]],
    en:   Exp[Bool]
  ) extends EnabledOp[Void](en) {
    def mirror(f:Tx) = par_regfile_shiftin(f(reg),f(inds),dim,f(data),f(en))
  }


  /** Constructors **/
  private[spatial] def regfile_new[T:Staged:Bits](dims: Seq[Exp[Index]])(implicit ctx: SrcCtx) = {
    stageMutable(RegFileNew[T](dims))(ctx)
  }

  private[spatial] def regfile_load[T:Staged:Bits](
    reg:  Exp[RegFile[T]],
    inds: Seq[Exp[Index]],
    en:   Exp[Bool]
  )(implicit ctx: SrcCtx) = {
    stageCold(RegFileLoad(reg, inds, en))(ctx)
  }

  private[spatial] def regfile_store[T:Staged:Bits](
    reg:  Exp[RegFile[T]],
    inds: Seq[Exp[Index]],
    data: Exp[T],
    en:   Exp[Bool]
  )(implicit ctx: SrcCtx) = {
    stageWrite(reg)(RegFileStore(reg, inds, data, en))(ctx)
  }

  private[spatial] def regfile_shiftin[T:Staged:Bits](
    reg:  Exp[RegFile[T]],
    inds: Seq[Exp[Index]],
    dim:  Int,
    data: Exp[T],
    en:   Exp[Bool]
  )(implicit ctx: SrcCtx) = {
    stageWrite(reg)(RegFileShiftIn(reg, inds, dim, data, en))(ctx)
  }

  private[spatial] def par_regfile_shiftin[T:Staged:Bits](
    reg:  Exp[RegFile[T]],
    inds: Seq[Exp[Index]],
    dim:  Int,
    data: Exp[Vector[T]],
    en:   Exp[Bool]
  )(implicit ctx: SrcCtx) = {
    stageWrite(reg)(ParRegFileShiftIn(reg, inds, dim, data, en))(ctx)
  }


  /** Internal **/
  override def stagedDimsOf(x: Exp[_]): Seq[Exp[Index]] = x match {
    case Def(RegFileNew(dims)) => dims
    case _ => super.stagedDimsOf(x)
  }

}
