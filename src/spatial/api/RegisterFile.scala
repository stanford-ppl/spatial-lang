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
    def apply(indices: Index*)(implicit ctx: SrcCtx): T = wrap(regfile_load(s, unwrap(indices)))

    @CurriedUpdate
    def update(indices: Index*)(data: T): Void = {
      Void(regfile_store(s, unwrap(indices), data.s))
    }

    def :=(data: Vector[T])(implicit ctx: SrcCtx): Void = wrap(regfile_shiftin(s, data.s))
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
    def load(mem: RegFile[T], is: Seq[Index], en: Bool)(implicit ctx: SrcCtx): T = mem.apply(is:_*)

    def store(mem: RegFile[T], is: Seq[Index], data: T, en: Bool)(implicit ctx: SrcCtx): Void = {
      wrap(regfile_store(mem.s, unwrap(is), data.s))
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

  case class RegFileLoad[T:Staged:Bits](reg: Exp[RegFile[T]], i: Seq[Exp[Index]]) extends Op[T] {
    def mirror(f:Tx) = regfile_load(f(reg),f(i))
    override def aliases = Nil
    val mT = typ[T]
  }

  case class RegFileStore[T:Staged:Bits](reg: Exp[RegFile[T]], i: Seq[Exp[Index]], data: Exp[T]) extends Op[Void] {
    def mirror(f:Tx) = regfile_store(f(reg),f(i),f(data))
    val mT = typ[T]
  }

  case class RegFileShiftIn[T:Staged:Bits](reg: Exp[RegFile[T]], data: Exp[Vector[T]]) extends Op[Void] {
    def mirror(f:Tx) = regfile_shiftin(f(reg),f(data))
  }


  /** Constructors **/
  private[spatial] def regfile_new[T:Staged:Bits](dims: Seq[Exp[Index]])(implicit ctx: SrcCtx) = {
    stageMutable(RegFileNew[T](dims))(ctx)
  }

  private[spatial] def regfile_load[T:Staged:Bits](reg: Exp[RegFile[T]], i: Seq[Exp[Index]])(implicit ctx: SrcCtx) = {
    stageCold(RegFileLoad(reg, i))(ctx)
  }

  private[spatial] def regfile_store[T:Staged:Bits](reg: Exp[RegFile[T]], i: Seq[Exp[Index]], data: Exp[T])(implicit ctx: SrcCtx) = {
    stageWrite(reg)(RegFileStore(reg, i, data))(ctx)
  }

  private[spatial] def regfile_shiftin[T:Staged:Bits](reg: Exp[RegFile[T]], data: Exp[Vector[T]])(implicit ctx: SrcCtx) = {
    stageWrite(reg)(RegFileShiftIn(reg, data))(ctx)
  }


  /** Internal **/
  override def stagedDimsOf(x: Exp[_]): Seq[Exp[Index]] = x match {
    case Def(RegFileNew(dims)) => dims
    case _ => super.stagedDimsOf(x)
  }

}
