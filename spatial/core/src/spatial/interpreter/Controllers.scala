package spatial.interpreter

import java.util.concurrent.{ LinkedBlockingQueue => Queue }
import argon.core._
import spatial.nodes._
import spatial.utils._
import spatial.metadata._
import argon.interpreter.{Interpreter => AInterpreter}

trait Controllers extends AInterpreter {
  self: FIFOs with Regs =>

  def getReadStreamsAndFIFOs(ctrl: Exp[_]): List[Exp[_]] = {
    (variables
      .map(_._1)
      .filter { mem =>
        readersOf(mem).exists(_.ctrlNode == ctrl)
      }
      .filter { mem =>
        isStreamIn(mem) || isFIFO(mem)
      } ++ childrenOf(ctrl).flatMap(getReadStreamsAndFIFOs)).toList
  }

  def isMoreDataFromMems(mems: Seq[Any]) =
    mems.forall( x => x match {
      case x: Queue[_] => !x.isEmpty
      case x: IFIFO => !x.v.isEmpty
      case _ =>
        println(x);
        ???
    })

  def interpretNestedLoop(lhs: Sym[_], cchaine: Seq[Counterlike], func: Block[_], iters: Seq[Seq[Bound[_]]], valids: Seq[Seq[Bound[_]]]) = {
     val mems = getReadStreamsAndFIFOs(lhs).map(eval[Any])
        def isMoreData = () => isMoreDataFromMems(mems)
        def f(i: Int): Unit =
          if (i == cchaine.length)
            interpretBlock(func)
          else
            cchaine(i).foreach(
              isMoreData, {
                case (itera, valida) => {
                  iters(i).zip(itera).foreach { case (b, v)   => updateBound(b, v) }
                  valids(i).zip(valida).foreach { case (b, v) => updateBound(b, v) }
                  f(i + 1)
                }
              })
        f(0)
        iters.flatten.foreach(removeBound)
        valids.flatten.foreach(removeBound)
  }


  object ESeqCL {
    def unapply(x: Exp[_]) = Some(eval[Seq[Counterlike]](x))
  }
  

  override def matchNode(lhs: Sym[_]) = super.matchNode(lhs).orElse {
    case Forever() =>
      ForeverC()

    case Switch(body, selects, cases) =>
      interpretBlock(body)
      val i = selects.map(eval[Boolean]).indexOf(true)
      val cblock = eval[Block[_]](cases(i))
      eval[Any](interpretBlock(cblock))

    case SwitchCase(body) =>
      body

    case UnrolledForeach(SeqEB(ens), ESeqCL(cchaine), func, iters, valids) =>
      if (ens.forall(x => x)) {
        interpretNestedLoop(lhs, cchaine, func, iters, valids)
      }

    case UnrolledReduce(SeqEB(ens), ESeqCL(cchaine), accum, func, iters, valids) =>
      if (ens.forall(x => x)) {
        interpretNestedLoop(lhs, cchaine, func, iters, valids)        
        accum
      }
      
    case ParallelPipe(SeqEB(ens), block) => {
      if (ens.forall(x => x))
        interpretBlock(block)
    }

//    case OpReduce(

    case UnitPipe(SeqEB(ens), func) =>
      if (ens.forall(x => x))
        interpretBlock(func)

    case Hwblock(block, isForever) =>
      val mems = getReadStreamsAndFIFOs(lhs).map(eval[Any])
      def isMoreData = () => isMoreDataFromMems(mems)
      
      if (isForever)
        while (isMoreData())
          interpretBlock(block)
      else
        interpretBlock(block)
  }

}
