package spatial.api

import org.virtualized.CurriedUpdate
import org.virtualized.SourceContext
import argon.core.Staging
import spatial.SpatialExp

// TODO: Should RegFile and ShiftReg actually be the same type?

trait RegisterFileApi extends RegisterFileExp {
  this: SpatialExp =>


}

trait RegisterFileExp extends Staging {
  this: SpatialExp =>

  case class RegFile[T:Staged:Bits](s: Exp[RegFile[T]]) {
    def apply(indices: Index*)(implicit ctx: SrcCtx): T = wrap(regfile_apply(s, unwrap(indices)))

    @CurriedUpdate
    def update(indices: Index*)(data: T): Void = {
      Void(regfile_update(s, unwrap(indices), data.s))
    }
  }

  object RegFile {
    def apply[T:Staged:Bits](dims: Index*)(implicit ctx: SrcCtx): RegFile[T] = wrap(regfile_new[T](unwrap(dims)))
  }

  /** Type classes **/
  case class RegFileType[T:Bits](child: Staged[T]) extends Staged[RegFile[T]] {
    override def unwrapped(x: RegFile[T]) = x.s
    override def wrapped(x: Exp[RegFile[T]]) = RegFile(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[RegFile[T]]
    override def isPrimitive = false
  }
  implicit def regFileType[T:Staged:Bits]: Staged[RegFile[T]] = RegFileType(typ[T])


  /** IR Nodes **/
  case class RegFileNew[T:Staged:Bits](dims: Seq[Exp[Index]]) extends Op[RegFile[T]] {
    def mirror(f:Tx) = regfile_new[T](f(dims))
  }

  case class RegFileApply[T:Staged:Bits](reg: Exp[RegFile[T]], i: Seq[Exp[Index]]) extends Op[T] {
    def mirror(f:Tx) = regfile_apply(f(reg),f(i))
    override def aliases = Nil
  }

  case class RegFileUpdate[T:Staged:Bits](reg: Exp[RegFile[T]], i: Seq[Exp[Index]], data: Exp[T]) extends Op[Void] {
    def mirror(f:Tx) = regfile_update(f(reg),f(i),f(data))
  }

  /** Constructors **/
  private[spatial] def regfile_new[T:Staged:Bits](dims: Seq[Exp[Index]])(implicit ctx: SrcCtx) = {
    stageMutable(RegFileNew[T](dims))(ctx)
  }

  private[spatial] def regfile_apply[T:Staged:Bits](reg: Exp[RegFile[T]], i: Seq[Exp[Index]])(implicit ctx: SrcCtx) = {
    stageCold(RegFileApply(reg, i))(ctx)
  }

  private[spatial] def regfile_update[T:Staged:Bits](reg: Exp[RegFile[T]], i: Seq[Exp[Index]], data: Exp[T])(implicit ctx: SrcCtx) = {
    stageWrite(reg)(RegFileUpdate(reg, i, data))(ctx)
  }

}
