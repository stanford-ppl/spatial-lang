package spatial.interpreter

import argon.interpreter.{Interpreter => AInterpreter}
import argon.nodes._
import spatial.nodes._
import argon.core._
import scala.math.BigDecimal
import spatial.SpatialConfig

trait Interpreter extends AInterpreter with Controller {


  override protected def interpretNode(lhs: Sym[_], rhs: Op[_]): Unit = {

    rhs match {

      case ArgInNew(a) =>
        val x = eval[BigDecimal](a)
        variables += ((lhs, x))

      case ArgOutNew(a) =>
        val x = eval[BigDecimal](a)
        variables += ((lhs, x))

      case ArrayApply(a, i) =>
        val x =  eval[Array[_]](a)(eval[BigDecimal](i).toInt)
        variables += ((lhs, x))

      case InputArguments() =>
        val x = Array.tabulate(10)(x => (x + 1).toString)
        variables += ((lhs, x))

      case StringToFixPt(a) =>
        val x = BigDecimal(eval[String](a))
        variables += ((lhs, x))

      case SetArg(a: Sym[_], b) =>
        val x = eval[Any](b)
        variables += ((a, x))
        
      case GetArg(a) =>
        val x = eval[Any](a)        
        variables += ((lhs, x))

      case RegWrite(a: Sym[_], b, c) =>
        if (eval[Boolean](c)) {
          val x = eval[Any](b)
          variables += ((a, x))
        }

      case StringConcat(a, b) =>
        val x = eval[String](a) + eval[String](b)
        variables += ((lhs, x))

      case RegRead(a: Sym[_]) =>
        val x = variables(a)
        variables += ((lhs, x))        

      case FixAdd(a, b) =>
        val x = eval[BigDecimal](a) + eval[BigDecimal](b)
        variables += ((lhs, x))

      case ToString(a) =>
        val x = eval[Any](a).toString
        variables += ((lhs, x))

      case PrintlnIf(a, b) => 
          if (eval[Boolean](a))
            println(eval[Any](b).toString)

      case _ =>
        super.interpretNode(lhs, rhs)
    }

    if (SpatialConfig.debug) {
      println()
      println(lhs, rhs)
      debug
    }

    
  }

}
