package spatial.interpreter

import argon.core._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}

trait Debuggings extends AInterpreter {

  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {
    case PrintlnIf(EBoolean(cond), EString(str)) =>
      if (cond)
        println(str)
    case BreakpointIf(EBoolean(cond)) =>
      if (cond) {
        println(s"[${Console.BLUE}breakpoint info${Console.RESET}]")
        displayInfo        
        println(s"${Console.RED_B}Reached a breakpoint: press a key to continue or q to quit${Console.RESET}")
        
        if (io.StdIn.readLine() == "q") {          
          config.exit()
          System.exit(0)
        }        
      }

    case ExitIf(EBoolean(cond)) =>
      if (cond) {
        println(s"[${Console.RED}exit info${Console.RESET}]")
        displayInfo        
        println(s"${Console.RED_B}Reached an exit point${Console.RESET}")
        config.exit()
        System.exit(0)
      }
      

  }

}


