package spatial.codegen.pirgen

import argon.core._
import spatial.nodes._
import spatial.utils._
import spatial.metadata._

trait PIRGenController extends PIRCodegen {

  def emitIters(cchain:Exp[_], iters:Seq[Seq[Exp[_]]], valids:Seq[Seq[Exp[_]]], isInnerControl:Boolean) = {
    val Def(CounterChainNew(counters)) = cchain
    counters.zip(iters.zip(valids)).foreach { case (counter, (iters, valids)) =>
      iters.zip(valids).zipWithIndex.foreach { case ((iter, valid), i) =>
        val offset = if (isInnerControl && counter == counters.last) None else Some(i)
        val Def(CounterNew(start, end, step, par)) = counter
        val parInt = getConstant(par).get.asInstanceOf[Int]
        //(boundOf.get(start), boundOf.get(end), boundOf.get(step)).zipped.foreach { case (bstart, bend, bstep) =>
          //dbg(s"$counter, bstart=$bstart, bend=$bend, bstep=$bstep, par=$parInt")
          //assert((bend - bstart) % (bstep * parInt) == 0, 
            //s"Cannot handle unaligned iterator range: $cchain \n" + 
            //s"(end=$bend - start=$bstart) % (step=$bstep * par=$parInt) != 0\n" + 
            //s"${cchain.ctx}"
          //)
        //}
        emit(iter, s"CounterIter($counter, $offset)", iter)
        emit(valid, s"Const(true)", valid)
      }
    }
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    if (isControlNode(lhs)) {
      rhs match {
        case UnrolledForeach(en, cchain, func, iters, valids) => 
          emit(lhs, s"LoopController(style=${styleOf(lhs)}, level=${levelOf(lhs)}, cchain=$cchain)", rhs)
          controlStack.push(lhs)
          emitIters(cchain, iters, valids, isInnerControl(lhs))
          emitBlock(func)
          controlStack.pop
        case UnrolledReduce(en, cchain, accum, func, iters, valids) => 
          emit(lhs, s"LoopController(style=${styleOf(lhs)}, level=${levelOf(lhs)}, cchain=$cchain)", rhs)
          controlStack.push(lhs)
          emitIters(cchain, iters, valids, isInnerControl(lhs))
          emitBlock(func)
          controlStack.pop
        case StateMachine(en, start, notDone, action, nextState, state) =>
          emit(lhs, s"UnitController(style=${styleOf(lhs)}, level=${levelOf(lhs)})", s"//TODO $rhs")
          controlStack.push(lhs)
          emit(s"// $lhs.notDone")
          emitBlock(notDone)
          emit(s"// $lhs.action")
          emitBlock(action)
          emit(s"// $lhs.nextState")
          emitBlock(nextState)
          controlStack.pop
        case UnitPipe(en, func) =>
          emit(lhs, s"UnitController(style=${styleOf(lhs)}, level=${levelOf(lhs)})", rhs)
          controlStack.push(lhs)
          emitBlock(func)
          controlStack.pop
        case Switch(body, selects, cases) =>
          emit(lhs, s"UnitController(style=${styleOf(lhs)}, level=${levelOf(lhs)})", s"//TODO $rhs")
          controlStack.push(lhs)
          cases.collect{case s: Sym[_] => stmOf(s)}.foreach(visitStm)
          controlStack.pop
        case SwitchCase(block) =>
          emit(lhs, s"UnitController(style=${styleOf(lhs)}, level=${levelOf(lhs)})", s"//TODO $rhs")
          controlStack.push(lhs)
          emitBlock(block)
          controlStack.pop
        case Hwblock(block, isForever) if isForever=>
          emit(lhs, s"ForeverController()", rhs)
          controlStack.push(lhs)
          emitBlock(block)
          controlStack.pop
        case Hwblock(block, isForever) =>
          emit(lhs, s"UnitController(style=${styleOf(lhs)}, level=${levelOf(lhs)})", rhs)
          controlStack.push(lhs)
          emitBlock(block)
          controlStack.pop
        case ParallelPipe(en, block) =>
          emit(lhs, s"UnitController(style=${styleOf(lhs)}, level=${levelOf(lhs)})", rhs)
          controlStack.push(lhs)
          emitBlock(block)
          controlStack.pop
        case _ =>
          emit(lhs, s"UnitController(style=${styleOf(lhs)}, level=${levelOf(lhs)})", s"//TODO $rhs")
          super.emitNode(lhs, rhs)
          controlStack.push(lhs)
          rhs.blocks.foreach(emitBlock)
          controlStack.pop
      }
    } else {
      super.emitNode(lhs, rhs)
    }
  }
}

