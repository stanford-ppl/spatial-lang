package spatial.transform

import argon.transform.ForwardTransformer
import spatial.SpatialExp

trait SwitchFlattener extends ForwardTransformer {
  val IR: SpatialExp
  import IR._
  override val name = "Switch Flattener"

  var enable: Option[Exp[Bool]] = None

  def withEnable[T](en: Exp[Bool])(blk: => T)(implicit ctx: SrcCtx): T = {
    var prevEnable = enable
    enable = Some(enable.map(bool_and(_,en)).getOrElse(en) )
    val result = blk
    enable = prevEnable
    result
  }

  var canInline = Map[Exp[_], Boolean]()

  /**
    * Since only some effectful primitive nodes currently allow enables, currently only unrolling inner switch statements
    * when all contained primitive nodes have explicit enables.
    */
  def canInlineSwitch(c: Exp[_]): Boolean = if (canInline.contains(c)) canInline(c) else {

    def canInlineCase(c: Exp[_]): Boolean = c match {
      case Def(SwitchCase(cond,blk)) =>
        val contents = blockContents(blk)
        contents.flatMap(_.lhs).forall{
          case LocalReader(reads)  => reads.forall(_.en.isDefined)
          case LocalWriter(writes) => writes.forall(_.en.isDefined)
          case _ => false
        }
      case _ => false
    }

    val can = isOuterControl(c) || childrenOf(c).forall(canInlineCase)
    canInline += c -> can
    can
  }

  override def transform[T: Staged](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = rhs match {
    case Switch(blk) if canInlineSwitch(lhs) => inlineBlock(blk)
    case SwitchCase(cond,blk) if canInlineSwitch(parentOf(lhs).get) => withEnable(f(cond)){ inlineBlock(blk) }

    case op: EnabledController =>
      // TODO: Could potentially make this simpler if default node mirroring could be overridden
      transferMetadataIfNew(lhs){ op.mirrorWithEn(f, enable.toSeq).asInstanceOf[Exp[T]] }._1

    case LocalReader(reads) =>
      val ens = reads.flatMap(_.en)
      val ens2 = ens.map{en => enable.map{gen => bool_and(f(en), gen) }.getOrElse(f(en)) }
      withSubstScope(ens.zip(ens2):_*){ super.transform(lhs,rhs) }

    case LocalWriter(writes) =>
      val ens = writes.flatMap(_.en)
      val ens2 = ens.map{en => enable.map{gen => bool_and(f(en), gen) }.getOrElse(f(en)) }
      withSubstScope(ens.zip(ens2):_*){ super.transform(lhs,rhs) }

    case _ => super.transform(lhs, rhs)

  }


}