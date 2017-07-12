package spatial.interpreter

import spatial.SpatialConfig
import argon.core._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}

trait Controllers extends AInterpreter {

  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {
    case Forever() =>
      ForeverC()

    case Switch(body, selects, cases) =>
      interpretBlock(body)
      val i = selects.map(eval[Boolean]).indexOf(true)
      interpretBlock(eval[Block[_]](cases(i)))

    case SwitchCase(body) =>
      body

    case UnrolledForeach(ens, cchain, func, iters, valids) =>
      val ense = ens.map(eval[Boolean])
      val cchaine = eval[Seq[Counterlike]](cchain)
      cchaine.indices.foreach( i => {
        cchaine(i).foreach { case (itera, valida) => {
          iters(i).zip(itera).foreach { case (b, v) => updateBound(b, v) }
          valids(i).zip(valida).foreach { case (b, v) => updateBound(b, v) }
          interpretBlock(func)
        } }
        iters(i).foreach(removeBound)
        valids(i).foreach(removeBound)        
      })



    case UnitPipe(ens, func) =>
      val ense = ens.map(eval[Boolean])
      if (ense.forall(x => x))
        interpretBlock(func)


    case Hwblock(block, isForever) =>
      if (isForever)
        while (!Config.forceExit()) 
          interpretBlock(block)        
      else
          interpretBlock(block)        
  }

}
