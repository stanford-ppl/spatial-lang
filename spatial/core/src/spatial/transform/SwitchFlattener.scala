package spatial.transform

import argon.transform.ForwardTransformer
import spatial.SpatialExp

/*trait SwitchFlattener extends ForwardTransformer {
  val IR: SpatialExp
  import IR._
  override val name = "Switch Flattener"

  var enable: Option[Exp[Bool]] = None

  override def transform[T:Type](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = rhs match {
    case Switch(blk,selects,cases) => typ[T] match {
      case Bits(bits) =>
        implicit val bT: Bits[T] = bits.asInstanceOf[Bits[T]]
        inlineBlock(blk)
        val selects = cases.map{case Op(SwitchCase(blk)) => f(cond) }
        val datas   = cases.map{case Op(SwitchCase(blk)) => f(blk.result) }
        onehot_mux(selects, datas)
      case _ =>
        inlineBlock(blk)
    }
    case SwitchCase(cond,blk) => withEnable(f(cond)){ inlineBlock(blk) }
    case _ => super.transform(lhs, rhs)
  }
}*/