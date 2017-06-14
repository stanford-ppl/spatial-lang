package spatial.transform

import argon.transform.ForwardTransformer
import spatial.compiler._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

import scala.collection.mutable

trait RegisterCleanup extends ForwardTransformer {
  override val name = "Register Cleanup"

  private case object FakeSymbol { override def toString = "\"You done goofed\"" }

  // User specific substitutions
  private var statelessSubstRules = Map[(Exp[_],Exp[_]), Seq[(Exp[_], () => Exp[_])]]()

  val completedMirrors = mutable.HashMap[(Exp[_],Exp[_]), Exp[_]]()

  def delayedMirror[T:Type](lhs: Sym[T], rhs:Op[T], ctrl: Exp[_])(implicit ctx: SrcCtx) = () => {
    val key = (lhs, ctrl)

    // scala bug? getOrElseUpdate always creates the value???

    /*completedMirrors.getOrElseUpdate(key, {
      withCtrl(ctrl){ mirrorWithDuplication(lhs, rhs) }
    })*/

    if (completedMirrors.contains(key)) {
      completedMirrors(key)
    }
    else {
      val lhs2 = withCtrl(ctrl){ mirrorWithDuplication(lhs, rhs) }
      completedMirrors += key -> lhs2
      lhs2
    }

  }

  var ctrl: Exp[_] = _
  def withCtrl[A](c: Exp[_])(blk: => A): A = {
    var prev = ctrl
    ctrl = c
    val result = blk
    ctrl = prev
    result
  }

  override def transform[T:Type](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = rhs match {
    case node if isStateless(node) && shouldDuplicate(lhs) =>
      dbg("")
      dbg("[stateless]")
      dbg(c"$lhs = $rhs")
      dbg(c"users: ${usersOf(lhs)}")

      if (usersOf(lhs).isEmpty) {
        dbg(c"REMOVING stateless $lhs")
        constant(typ[T])(FakeSymbol)  // Shouldn't be used
      }
      else {
        // For all uses within a single control node, create a single copy of this node
        // Then associate all uses within that control with that copy
        val users = usersOf(lhs).groupBy(_.ctrl).mapValues(_.map(_.node))

        val reads = users.map{case (parent, uses) =>
          val read = delayedMirror(lhs, rhs, parent.node)

          dbg(c"ctrl: $parent")

          uses.foreach { use =>
            val subs = (lhs -> read) +: statelessSubstRules.getOrElse((use,parent.node), Nil)
            dbg(s"  $use: $lhs -> $read")
            statelessSubstRules += (use,parent.node) -> subs
          }

          read
        }
        constant(typ[T])(FakeSymbol) // mirror(lhs, rhs)
      }

    case RegWrite(reg,value,en) =>
      dbg("")
      dbg("[reg write]")
      dbg(c"$lhs = $rhs")
      if (readersOf(reg).isEmpty && !isOffChipMemory(reg)) {
        dbg(c"REMOVING register write $lhs")
        constant(typ[T])(FakeSymbol)  // Shouldn't be used
      }
      else mirrorWithDuplication(lhs, rhs)

    case RegNew(_) =>
      dbg("")
      dbg("[reg new]")
      dbg(c"$lhs = $rhs")
      if (readersOf(lhs).isEmpty) {
        dbg(c"REMOVING register $lhs")
        constant(typ[T])(FakeSymbol)  // Shouldn't be used
      }
      else mirrorWithDuplication(lhs, rhs)

    case node if isControlNode(lhs) => withCtrl(lhs){ mirrorWithDuplication(lhs, rhs) }
    case _ => mirrorWithDuplication(lhs, rhs)
  }

  private def mirrorWithDuplication[T:Type](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = {
    if ( statelessSubstRules.contains((lhs,ctrl)) ) {
      dbg("")
      dbg(c"[external user, ctrl = $ctrl]")
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

  override protected def blockToFunction0[T:Type](b: Block[T]): Exp[T] = inlineBlock(b, {stms =>
    visitStms(stms)
    if (ctrl != null && statelessSubstRules.contains((ctrl,ctrl))) {
      val rules = statelessSubstRules((ctrl, ctrl)).map { case (s, s2) => s -> s2() }
      withSubstScope(rules: _*) { f(b.result) }
    }
    else f(b.result)
  })

  override protected def transformBlock[T:Type](b: Block[T]): Block[T] = transformBlock(b, {stms =>
    stms.foreach(stm => dbg(c"$stm"))
    visitStms(stms)
    if (ctrl != null && statelessSubstRules.contains((ctrl,ctrl))) {
      val rules = statelessSubstRules((ctrl, ctrl)).map { case (s, s2) => s -> s2() }
      withSubstScope(rules: _*) { f(b.result) }
    }
    else f(b.result)
  })

}
