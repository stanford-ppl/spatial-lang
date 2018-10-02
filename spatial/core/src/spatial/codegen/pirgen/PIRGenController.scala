package spatial.codegen.pirgen

import argon.core._
import spatial.nodes._
import spatial.utils._
import spatial.metadata._
import scala.collection.mutable

trait PIRGenController extends PIRCodegen {

  val controlStack = mutable.Stack[Lhs]()
  def currCtrl = controlStack.top
  def inHwBlock = controlStack.nonEmpty
  def withCtrl[T](ctrl:Exp[_])(block: => T):T = {
    controlStack.push(ctrl)
    val res = block
    controlStack.pop
    res
  }

  def emitIters(cchain:Exp[_], iters:Seq[Seq[Exp[_]]], valids:Seq[Seq[Exp[_]]], isInnerControl:Boolean) = {
    val Def(CounterChainNew(counters)) = cchain
    counters.zip(iters.zip(valids)).foreach { case (counter, (iters, valids)) =>
      iters.zip(valids).zipWithIndex.foreach { case ((iter, valid), i) =>
        val offset = if (isInnerControl && counter == counters.last) None else Some(i)
        val Def(CounterNew(start, end, step, par)) = counter
        val parInt = getConstant(par).get.asInstanceOf[Int]
        emit(DefRhs(iter, s"CounterIter", counter, offset))
        emit(DefRhs(valid, s"Const[Boolean]", "true"))
      }
    }
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    if (isControlNode(lhs)) {
      rhs match {
        case UnrolledForeach(en, cchain, func, iters, valids) => 
          emit(DefRhs(lhs, s"LoopController", "style" -> styleOf(lhs), "level"-> levelOf(lhs), "cchain" -> cchain))
          withCtrl(lhs) {
            emitIters(cchain, iters, valids, isInnerControl(lhs))
            emitBlock(func)
          }
        case UnrolledReduce(en, cchain, accum, func, iters, valids) => 
          emit(DefRhs(lhs, s"LoopController", "style"->styleOf(lhs), "level"-> levelOf(lhs), "cchain"->cchain))
          withCtrl(lhs) {
            emitIters(cchain, iters, valids, isInnerControl(lhs))
            emitBlock(func)
          }
        case StateMachine(en, start, notDone, action, nextState, state) =>
          emit(DefRhs(lhs, s"UnitController", "style"->styleOf(lhs), "level"->levelOf(lhs)).comment("TODO"))
          withCtrl(lhs) {
            emit(s"// $lhs.notDone")
            emitBlock(notDone)
            emit(s"// $lhs.action")
            emitBlock(action)
            emit(s"// $lhs.nextState")
            emitBlock(nextState)
          }
        case UnitPipe(en, func) =>
          emit(DefRhs(lhs, s"UnitController", "style"->styleOf(lhs), "level"->levelOf(lhs)))
          withCtrl(lhs) {
            emitBlock(func)
          }
        case Switch(body, selects, cases) =>
          emit(DefRhs(lhs, s"UnitController", "style"->styleOf(lhs), "level"->levelOf(lhs)).comment("TODO"))
          withCtrl(lhs) {
            cases.collect{case s: Sym[_] => stmOf(s)}.foreach(visitStm)
          }
        case SwitchCase(block) =>
          emit(DefRhs(lhs, s"UnitController", "style"->styleOf(lhs), "level"->levelOf(lhs)).comment("TODO"))
          withCtrl(lhs) {
            emitBlock(block)
          }
        case Hwblock(block, isForever) if isForever=>
          emit(DefRhs(lhs, s"ForeverController"))
          withCtrl(lhs) {
            emitBlock(block)
          }
        case Hwblock(block, isForever) =>
          emit(DefRhs(lhs, s"UnitController", "style"->styleOf(lhs), "level"->levelOf(lhs)))
          withCtrl(lhs) {
            emitBlock(block)
          }
        case ParallelPipe(en, block) =>
          emit(DefRhs(lhs, s"UnitController", "style"->styleOf(lhs), "level"->levelOf(lhs)))
          withCtrl(lhs) {
            emitBlock(block)
          }
        case _ =>
          emit(DefRhs(lhs, s"UnitController", "style"->styleOf(lhs), "level"->levelOf(lhs)).comment("TODO"))
          super.emitNode(lhs, rhs)
          withCtrl(lhs) {
            rhs.blocks.foreach(emitBlock)
          }
      }
    } else {
      super.emitNode(lhs, rhs)
    }
  }

  override protected def quoteRef(x:Any):String = x match {
    case x:ControlStyle => x.toString
    case x:ControlLevel => x.toString
    case x => super.quoteRef(x)
  }

  override protected def quoteOrRemap(arg: Any): String = arg match {
    case x@DefRhs(op, name,_*) => 
      val q = super.quoteOrRemap(x)
      val ctrl = controlStack.headOption.map { quoteRef(_) }.getOrElse(s"design.top.topController")
      src"withCtrl($ctrl) { $q }"
    case x => 
      super.quoteOrRemap(x)
  }


}

