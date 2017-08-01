package spatial.transform

import argon.core._
import argon.transform.ForwardTransformer
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

import scala.collection.mutable

case class RegisterCleanup(var IR: State) extends ForwardTransformer {
  override val name = "Register Cleanup"

  private case object MissingReg { override def toString = "\"Used register was removed\"" }

  // User specific substitutions
  private var statelessSubstRules = Map[(Exp[_],Blk), Seq[(Exp[_], () => Exp[_])]]()

  private val completedMirrors = mutable.HashMap[(Exp[_],Blk), Exp[_]]()

  private def delayedMirror[T:Type](lhs: Sym[T], rhs:Op[T], ctrl: Blk)(implicit ctx: SrcCtx) = () => {
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

  var blk: Blk = _
  var blockCount: Int = 0
  var inHw: Boolean = false
  def withCtrl[A](c: Exp[_])(func: => A): A = withCtrl((c,-1))(func)
  def withCtrl[A](b: Blk)(func: => A): A = {
    val prevBlk = blk
    val prevCount = blockCount
    blk = b
    blockCount = -1
    val result = func
    blk = prevBlk
    blockCount = prevCount
    result
  }

  override def transform[T:Type](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = rhs match {
    case Hwblock(func,_) =>
      inHw = true
      val result = withCtrl(lhs){ mirrorWithDuplication(lhs, rhs) }
      inHw = false
      result

    case node if ((inHw && isStateless(node)) || isRegisterRead(node)) && shouldDuplicate(lhs) =>
      dbgs("")
      dbgs("[stateless]")
      dbgs(c"$lhs = $rhs")
      dbgs(c"users: ${usersOf(lhs)}")

      if (usersOf(lhs).isEmpty) {
        dbgs(c"REMOVING stateless $lhs")
        constant(typ[T])(MissingReg)  // Shouldn't be used
      }
      else {
        // For all uses within a single control node, create a single copy of this node
        // Then associate all uses within that control with that copy
        val users = usersOf(lhs).groupBy(_.ctrl).mapValues(_.map(_.node))

        users.foreach{case (parent, uses) =>
          val read = delayedMirror(lhs, rhs, parent)

          dbgs(c"ctrl: $parent")

          uses.foreach { use =>
            val subs = (lhs -> read) +: statelessSubstRules.getOrElse((use,parent), Nil)
            dbgs(s"  ($use, $parent): $lhs -> $read")
            statelessSubstRules += (use,parent) -> subs
          }

        }
        constant(typ[T])(MissingReg) // mirror(lhs, rhs)
      }

    case RegWrite(reg,value,en) =>
      dbgs("")
      dbgs("[reg write]")
      dbgs(c"$lhs = $rhs")
      if (readersOf(reg).isEmpty && !isOffChipMemory(reg)) {
        dbgs(c"REMOVING register write $lhs")
        MUnit.const().asInstanceOf[Exp[T]]
      }
      else mirrorWithDuplication(lhs, rhs)

    case RegNew(_) =>
      dbgs("")
      dbgs("[reg new]")
      dbgs(c"$lhs = $rhs")
      if (readersOf(lhs).isEmpty) {
        dbgs(c"REMOVING register $lhs")
        constant(typ[T])(MissingReg)  // Shouldn't be used
      }
      else mirrorWithDuplication(lhs, rhs)

    case _ if isControlNode(lhs) => withCtrl(lhs){ mirrorWithDuplication(lhs, rhs) }
    case _ => mirrorWithDuplication(lhs, rhs)
  }

  private def mirrorWithDuplication[T:Type](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = {
    dbgs(c"${str(lhs)} [$blk]")
    if ( statelessSubstRules.contains((lhs,blk)) ) {
      dbgs("")
      dbgs(c"[external user, blk = $blk]")
      dbgs(c"$lhs = $rhs")
      // Activate / lookup duplication rules
      val rules = statelessSubstRules((lhs,blk)).map{case (s,s2) => s -> s2()}
      rules.foreach{case (s,s2) => dbgs(s"  $s -> ${str(s2)}") }
      val lhs2 = withSubstScope(rules: _*){ mirror(lhs, rhs) }
      dbgs(c"${str(lhs2)}")
      lhs2
    }
    else mirror(lhs, rhs)
  }

  /** Requires slight tweaks to make sure we transform block results properly, primarily for OpReduce **/
  override protected def inlineBlock[T](b: Block[T]): Exp[T] = inlineBlockWith(b, {stms =>
    blockCount = blockCount + 1     // Advance to the next block for the current node being mirrored
    blk = (blk.node, blockCount)    // Used to track which duplicates should be made in which blocks
    if (blk.node == null) blk = null
    visitStms(stms)
    // Add substitutions for this node (ctrl.node, -1) and for the current block (ctrl)
    val curNode: Access = (blk.node, (blk.node, -1))
    val curBlock: Access = (blk.node, blk)
    if (blk != null && (statelessSubstRules.contains(curBlock) || statelessSubstRules.contains(curNode)) ) {
      val rules = statelessSubstRules.getOrElse(curNode, Nil).map{case (s, s2) => s -> s2() } ++
                  statelessSubstRules.getOrElse(curBlock, Nil).map{case (s, s2) => s -> s2() }
      withSubstScope(rules: _*) { f(b.result) }
    }
    else f(b.result)
  })

}
