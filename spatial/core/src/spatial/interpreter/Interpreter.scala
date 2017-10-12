package spatial.interpreter

//import sys.process._
import argon.interpreter.{Interpreter => AInterpreter}
import argon.nodes._
import spatial.nodes._
import argon.core._

trait Interpreter
    extends AInterpreter
    with Controllers
    with FileIOs
    with Debuggings
    with HostTransfers
    with Regs
    with Strings
    with FixPts
    with FltPts
    with Arrays
    with Streams
    with Structs
    with SRAMs
    with DRAMs
    with Booleans
    with Counters
    with Vectors
    with FIFOs
    with FSMs
    with RegFiles
    with Maths
    with LUTs
{

  
  var instructionNumber = 0
  override protected def interpretNode(lhs: Sym[_], rhs: Op[_]): Unit = {

    if (config.verbosity >= 2) {
      displayInfo
      println()         
      if (Streams.streamsIn.size > 0 || Streams.streamsOut.size > 0) {
        println(s"[${Console.BLUE}input streams size${Console.RESET}]")
        Streams.streamsIn.foreach { case (k, s) => println(k + ": " + s.size) }
        Streams.streamsOut.foreach { case (k, s) => println(k + ": " + s.size) } 
      }
    }
    if (config.verbosity >= 1) {
      println(s"[${Console.CYAN}context${Console.RESET}]")         
      println(lhs.ctx)
      val line = lhs.ctx.lineContent.getOrElse("")
      /*
      val colored_ar = ((s"echo $s" #| "pygmentize -l scala ").!!).split("\n")
      val colored =
        if (colored_ar.isEmpty)
          ""
        else
          colored_ar(0)
      println(colored)
      */
      val methodName = lhs.ctx.methodName
      val index = line.indexOfSlice(methodName)
      val highlighted =
        if (index == -1)
          line
        else
          line.take(index) + Console.CYAN_B + methodName + Console.RESET + line.drop(index + methodName.length)
      println(highlighted)
      println(s"[${Console.CYAN}node${Console.RESET}]: ${Console.CYAN}$rhs${Console.RESET} -> $lhs")
      if (config.verbosity == 0)
    println(s"[${Console.CYAN}instruction #${Console.RESET}]: ${Console.BLUE}$instructionNumber${Console.RESET}\r")        
      else 
        println(s"[${Console.CYAN}instruction #${Console.RESET}]: ${Console.BLUE}$instructionNumber${Console.RESET}")
      instructionNumber += 1      
    }


    
    super.interpretNode(lhs, rhs)

  }

  override def displayInfo() = {
    val variablesF = variables.toList.sortBy(_._1.toString).filter(!_._2.isInstanceOf[Unit])
  println(s"[${Console.BLUE}sram content${Console.RESET}]")    
    variablesF.filter(_._2.isInstanceOf[ISRAM]).foreach(displayPair)    
    println(s"[${Console.BLUE}fifo content${Console.RESET}]")    
    variablesF.filter(_._2.isInstanceOf[IFIFO]).foreach(displayPair)
    println(s"[${Console.BLUE}reg content${Console.RESET}]")    
    variablesF.filter(_._2.isInstanceOf[IReg]).foreach(displayPair)
    println(s"[${Console.BLUE}regfiles content${Console.RESET}]")    
    variablesF.filter(_._2.isInstanceOf[IRegFile]).foreach(displayPair)
    println(s"[${Console.BLUE}LUT content${Console.RESET}]")    
    variablesF.filter(_._2.isInstanceOf[ILUT]).foreach(displayPair)    
//    println(s"[${Console.BLUE}streams content${Console.RESET}]")    
//    variablesF.filter(_._2.isInstanceOf[Queue]).foreach(displayPair)
    println(s"[${Console.BLUE}others${Console.RESET}]")
    variablesF.filter(x => {
      !x._2.isInstanceOf[IRegFile] &&
      !x._2.isInstanceOf[ISRAM] &&
      !x._2.isInstanceOf[IFIFO] &&
      !x._2.isInstanceOf[ILUT] &&      
      !x._2.isInstanceOf[IReg]      
    }).foreach(displayPair)
    println(s"[${Console.BLUE}bounds content${Console.RESET}]")    
    bounds.toList.sortBy(_._1.toString).foreach(displayPair)



  }

}
