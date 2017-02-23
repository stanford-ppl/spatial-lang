package spatial.transform

import argon.transform.ForwardTransformer
import spatial.SpatialExp
import scala.collection.mutable

trait RegisterCleanup extends ForwardTransformer {
  val IR: SpatialExp
  import IR._

  override val name = "Register Cleanup"

  private case object FakeSymbol { override def toString = "\"You done goofed\"" }

  // User specific substitutions
  private var statelessSubstRules = Map[Access, Seq[(Exp[_], () => Exp[_])]]()

  val completedMirrors = mutable.HashMap[Access, Exp[_]]()
  def delayedMirror[T:Staged](lhs: Sym[T], rhs:Op[T], ctrl: Ctrl)(implicit ctx: SrcCtx) = () => {
    completedMirrors.getOrElseUpdate( (lhs,ctrl), {
      withCtrl(ctrl){ mirrorWithDuplication(lhs, rhs) }
    })
  }

  var ctrl: Ctrl = _
  def withCtrl[A](c: Ctrl)(blk: => A): A = {
    var prev = ctrl
    ctrl = c
    val result = blk
    ctrl = prev
    result
  }

  override def transform[T: Staged](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = rhs match {
    case node if isStateless(node) && shouldDuplicate(lhs) =>
      dbg("")
      dbg("[stateless]")
      dbg(c"$lhs = $rhs")
      dbg(c"users: ${usersOf(lhs)}")

      if (usersOf(lhs).isEmpty) {
        dbg(c"REMOVING stateless $lhs")
        constant[T](FakeSymbol)  // Shouldn't be used
      }
      else {
        // For all uses within a single control node, create a single copy of this node
        // Then associate all uses within that control with that copy
        val users = usersOf(lhs).groupBy(_.ctrl).mapValues(_.map(_.node))

        val reads = users.map{case (parent, uses) =>
          val read = delayedMirror(lhs, rhs, parent)

          dbg(c"ctrl: $parent")

          uses.foreach { use =>
            val subs = (lhs -> read) +: statelessSubstRules.getOrElse((use,parent), Nil)
            dbg(s"  $use: $lhs -> $read")
            statelessSubstRules += (use,parent) -> subs
          }

          read
        }
        constant[T](FakeSymbol) // mirror(lhs, rhs)
      }

    case RegWrite(reg,value,en) =>
      dbg("")
      dbg("[reg write]")
      dbg(c"$lhs = $rhs")
      if (readersOf(reg).isEmpty && !isArgOut(reg)) {
        dbg(c"REMOVING register write $lhs")
        constant[T](FakeSymbol)  // Shouldn't be used
      }
      else mirrorWithDuplication(lhs, rhs)

    case RegNew(_) =>
      dbg("")
      dbg("[reg new]")
      dbg(c"$lhs = $rhs")
      if (readersOf(lhs).isEmpty) {
        dbg(c"REMOVING register $lhs")
        constant[T](FakeSymbol)  // Shouldn't be used
      }
      else mirrorWithDuplication(lhs, rhs)

    case node if isControlNode(lhs) => withCtrl((lhs,false)) { mirrorWithDuplication(lhs, rhs) }
    case _ => mirrorWithDuplication(lhs, rhs)
  }

  private def mirrorWithDuplication[T:Staged](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = {
    if ( statelessSubstRules.contains((lhs,ctrl)) ) {
      dbg("")
      dbg("[external user]")
      dbg(c"$lhs = $rhs")
      // Activate / lookup duplication rules
      val rules = statelessSubstRules((lhs,ctrl)).map{case (s,s2) => s -> s2()}
      val lhs2 = withSubstScope(rules: _*){ mirror(lhs, rhs) }
      dbg(c"${str(lhs2)}")
      lhs2
    }
    else mirror(lhs, rhs)
  }

  /** These require slight tweaks to make sure we transform block results properly, primarily for OpReduce **/

  override protected def inlineBlock[T:Staged](b: Block[T]): Exp[T] = inlineBlock(b, {stms =>
    visitStms(stms)
    if (ctrl != null && statelessSubstRules.contains((ctrl.node,ctrl))) {
      val rules = statelessSubstRules((ctrl.node, ctrl)).map { case (s, s2) => s -> s2() }
      withSubstScope(rules: _*) { f(b.result) }
    }
    else f(b.result)
  })

  override protected def transformBlock[T:Staged](b: Block[T]): Block[T] = transformBlock(b, {stms =>
    visitStms(stms)
    if (ctrl != null && statelessSubstRules.contains((ctrl.node,ctrl))) {
      val rules = statelessSubstRules((ctrl.node, ctrl)).map { case (s, s2) => s -> s2() }
      withSubstScope(rules: _*) { f(b.result) }
    }
    else f(b.result)
  })

}
