package spatial.interpreter

import java.util.concurrent.{ LinkedBlockingQueue => Queue }
import spatial.SpatialConfig
import argon.core._
import spatial.nodes._
import spatial.utils._
import spatial.metadata._
import argon.interpreter.{Interpreter => AInterpreter}

trait Controllers extends AInterpreter {
  self: FIFOs =>

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

  override def matchNode(lhs: Sym[_]) = super.matchNode(lhs).orElse {
    case Forever() =>
      ForeverC()

    case Switch(body, selects, cases) =>
      interpretBlock(body)
      val i = selects.map(eval[Boolean]).indexOf(true)
      interpretBlock(eval[Block[_]](cases(i)))

    case SwitchCase(body) =>
      body

    case UnrolledForeach(ens, cchain, func, iters, valids) =>
      val ense    = ens.map(eval[Boolean])
      val cchaine = eval[Seq[Counterlike]](cchain)
      cchaine.indices.foreach(i => {
        val mems = getReadStreamsAndFIFOs(lhs).map(eval[Any])
        def moreData = () => mems.forall( x => x match {
          case x: Queue[_] => !x.isEmpty
          case x: FIFO => !x.v.isEmpty
          case _ =>
            println(x);
            ???
        })
        cchaine(i).foreach(
          moreData, {
            case (itera, valida) => {
              iters(i).zip(itera).foreach { case (b, v)   => updateBound(b, v) }
              valids(i).zip(valida).foreach { case (b, v) => updateBound(b, v) }
              interpretBlock(func)
            }
          }
        )
        iters(i).foreach(removeBound)
        valids(i).foreach(removeBound)
      })

    case UnitPipe(ens, func) =>
      val ense = ens.map(eval[Boolean])
      if (ense.forall(x => x))
        interpretBlock(func)

    case Hwblock(block, isForever) =>
      if (isForever)
        while (!Config.forceExit()) interpretBlock(block)
      else
        interpretBlock(block)
  }

}
