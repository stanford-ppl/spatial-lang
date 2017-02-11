package spatial.transform

import argon.transform.ForwardTransformer
import spatial.SpatialExp

trait RegReadCSE extends ForwardTransformer {
  val IR: SpatialExp
  import IR._

  override val name = "Register Read CSE"

  var inInnerCtrl: Boolean = false
  def inInner[A](x: => A): A = {
    val prev = inInnerCtrl
    inInnerCtrl = true
    val result = x
    inInnerCtrl = prev
    result
  }

  override def transform[T:Staged](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx) = rhs match {
    case e@RegRead(reg) if inInnerCtrl =>
      dbg(c"Found reg read $lhs = $rhs")
      val rhs2 = RegRead(f(reg))(typ[T],mbits(e.bT)) // Note that this hasn't been staged yet, only created the node
      val effects = effectsOf(lhs).mirror(f)
      val deps = depsOf(lhs).map(f(_))

      dbg(c"  rhs2 = $rhs2")
      dbg(c"  effects = $effects")
      dbg(c"  deps = $deps")

      val symsWithSameDef = defCache.getOrElse(rhs2, Nil) intersect context
      val symsWithSameEffects = symsWithSameDef.find{case Effectful(u2, es) => u2 == effects && es == deps }

      dbg(c"  def cache: ${defCache.getOrElse(rhs2,Nil)}")
      dbg(c"  context:")
      context.foreach{s => dbg(c"    ${str(s)} [effects = ${effectsOf(s)}, deps = ${depsOf(s)}]")}
      dbg(c"  syms with same def: $symsWithSameDef")
      dbg(c"  syms with same effects: $symsWithSameEffects")

      symsWithSameEffects match {
        case Some(lhs2) =>
          lhs2.addCtx(ctx)
          lhs2.asInstanceOf[Exp[T]]

        case None =>
          val lhs2 = mirror(lhs,rhs)
          getDef(lhs2).foreach{d => defCache(d) = onlySyms(lhs2).toList }
          lhs2
      }

    case _ if isInnerControl(lhs) => inInner{ super.transform(lhs,rhs) }
    case _ => super.transform(lhs,rhs)
  }
}
