package spatial.transform

import argon.transform.ForwardTransformer
import spatial.SpatialExp

trait RegisterCleanup extends ForwardTransformer {
  val IR: SpatialExp
  import IR._

  override val name = "Register Cleanup"

  // User specific substitutions
  private var statelessSubstRules = Map[Access, Seq[(Exp[_], Exp[_])]]()

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
        constant[T](666)  // Shouldn't be used
      }
      else {
        // For all uses within a single control node, create a single copy of this node
        // Then associate all uses within that control with that copy
        val reads = usersOf(lhs).groupBy(_.ctrl).mapValues(_.map(_.node)).map{case (parent, uses) =>
          val read = withCtrl(parent){ mirrorWithDuplication(lhs, rhs) }

          dbg(c"ctrl: $parent")

          uses.foreach { use =>
            val subs = (lhs -> read) +: statelessSubstRules.getOrElse((use,parent), Nil)
            dbg(s"  $use: $lhs -> $read")
            statelessSubstRules += (use,parent) -> subs
          }

          read
        }
        reads.head
      }

    case RegWrite(reg,value,en) =>
      dbg("")
      dbg("[reg write]")
      dbg(c"$lhs = $rhs")
      if (readersOf(reg).isEmpty && !isArgOut(reg)) {
        dbg(c"REMOVING register write $lhs")
        constant[T](666)  // Shouldn't be used
      }
      else mirrorWithDuplication(lhs, rhs)

    case RegNew(_) =>
      dbg("")
      dbg("[reg new]")
      dbg(c"$lhs = $rhs")
      if (readersOf(lhs).isEmpty) {
        dbg(c"REMOVING register $lhs")
        constant[T](666)  // Shouldn't be used
      }
      else mirror(lhs, rhs)

    case node if isControlNode(lhs) => withCtrl((lhs,false)) { mirrorWithDuplication(lhs, rhs) }
    case _ => mirrorWithDuplication(lhs, rhs)
  }

  private def mirrorWithDuplication[T:Staged](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = {
    if ( statelessSubstRules.contains((lhs,ctrl)) ) {
      dbg("")
      dbg("[external user]")
      dbg(c"$lhs = $rhs")
      val lhs2 = withSubstScope(statelessSubstRules((lhs,ctrl)) : _*){ mirror(lhs, rhs) }
      dbg(c"${str(lhs2)}")
      lhs2
    }
    else mirror(lhs, rhs)
  }

}
