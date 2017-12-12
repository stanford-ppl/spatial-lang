package spatial.transform

import argon.core._
import argon.lang.Func
import argon.nodes._
import argon.transform.ForwardTransformer
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

case class FunctionUnrolling(var IR: State) extends ForwardTransformer {

  var modules: Map[(Exp[_],Int),Exp[_]] = Map.empty
  var trace: Seq[Ctrl] = Nil
  var inHw: Boolean = false
  def inTrace[R](lhs: Exp[_])(block: => R): R = {
    val oldTrace = trace
    val blk = (lhs,0)
    val ctrl = blkToCtrl(blk)
    trace = if (inHw) ctrl +: trace else Nil
    val result = block
    trace = oldTrace
    result
  }

  case object MissingFunctionDecl { override def toString = "Missing function declaration" }

  def newFresh[T:Type] = fresh[T]

  override def transform[T:Type](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = rhs match {
    case Hwblock(_,_) =>
      inHw = true
      val lhs2 = inTrace(lhs){ super.transform(lhs,rhs) }
      inHw = false
      lhs2

    case _ if isControlNode(rhs) => inTrace(lhs){ super.transform(lhs,rhs) }

    case op @ UnrolledForeach(en,cchain,func,iters,valids) =>
      val iters2  = iters.map{is => is.map{i => fresh[Index] }}
      val valids2 = valids.map{vs => vs.map{v => fresh[Bit] }}
      val substs = iters.flatten.zip(iters2.flatten) ++ valids.flatten.zip(valids2.flatten)
      val func2 = () => inTrace(lhs){ withSubstScope(substs:_*){ inlineBlock(func) } }
      Foreach.op_unrolled_foreach(f(en),f(cchain),func2,iters2,valids2).asInstanceOf[Exp[T]]

    case op @ UnrolledReduce(en,cchain,accum,func,iters,valids) =>
      val iters2  = iters.map{is => is.map{i => fresh[Index] }}
      val valids2 = valids.map{vs => vs.map{v => fresh[Bit] }}
      val substs = iters.flatten.zip(iters2.flatten) ++ valids.flatten.zip(valids2.flatten)
      val func2 = () => inTrace(lhs){ withSubstScope(substs:_*){ inlineBlock(func) } }
      Reduce.op_unrolled_reduce(f(en),f(cchain),f(accum),func2,iters2,valids2)(op.mT,op.mC,ctx,state).asInstanceOf[Exp[T]]

    case op @ StateMachine(en,start,notDone,action,nextState,state) =>
      implicit val mT: Type[Any] = op.mT
      implicit val bT: Bits[Any] = op.bT
      val state2 = newFresh(op.mT)
      FSM.op_state_machine(f(en),f(start),f(notDone),f(action),f(nextState),state2).asInstanceOf[Exp[T]]

    case op @ FuncDecl(ins,block) =>
      val calls = callsTo(lhs)
      val copies = funcInstances(lhs)
      dbgs(s"${str(lhs)}")
      dbgs(s"  Creating $copies copies of function: ")
      (0 until copies).foreach { i =>
        dbgs(s"    Copy #$i:")
        val dispatchCalls = calls.filter{call => funcDispatch(call) == i }
        val dispatches = dispatchCalls.length
        val isHostCall = dispatchCalls.forall{call => call.trace.isEmpty }

        dbgs(s"    Calls: ")
        dispatchCalls.foreach{call =>
          dbgs(s"      ${call.node} [${call.trace}]")
        }

        if ((dispatches > 1 || isHostCall) && !spatialConfig.inline) {
          val ins2 = ins.map{in => newFresh(in.tp) }
          val oldTrace = trace
          val newTrace = dispatchCalls.head.trace.dropWhile{c => c != (lhs,0) }
          dbgs(s"      Using trace: $trace")
          trace = newTrace
          val copy = Func.decl(ins2, () => withSubstScope(ins.zip(ins2):_*){ inlineBlock(block) })(op.mRet,ctx,state)
          trace = oldTrace
          isHWModule(copy) = !isHostCall
          levelOf(copy) = levelOf(lhs)
          styleOf(copy) = FuncBody
          modules += (lhs, i) -> copy
          dbgs(s"      ${str(copy)}")
        }
        else {
          dbgs(s"      $dispatchCalls calls - will inline at call site")
        }
      }
      constant(typ[T])(MissingFunctionDecl)

    case FuncCall(func,inputs) =>
      dbgs(s"${str(lhs)}")
      dbgs(s"  Trace: $trace")
      val call = (lhs,trace)
      val dispatch = funcDispatch(call)
      val dispatches = callsTo(func).count{call => funcDispatch(call) == dispatch }
      val isHostCall = trace.isEmpty
      dbgs(s"  Dispatch: $dispatch")
      dbgs(s"  Other calls w/ this dispatch: $dispatches")

      // Inline function calls with only one dispatch at the call site
      if ((dispatches > 1 || isHostCall) && !spatialConfig.inline) {
        dbgs(s"Creating function dispatch to #$dispatch")
        val copy = modules((func, dispatch))
        withSubstScope(func -> copy) { inTrace(func){ super.transform(lhs, rhs) }}
      }
      else {
        dbgs(s"Inlining single function call: ")
        val Op(FuncDecl(l,block)) = func
        withSubstScope(l.zip(f.tx(inputs)):_*){
          inTrace(func){ inlineBlock(block) }
        }
      }

    case _ => super.transform(lhs,rhs)
  }

}
