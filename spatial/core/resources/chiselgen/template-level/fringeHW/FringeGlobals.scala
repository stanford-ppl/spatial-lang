package fringe
import java.io.{File, PrintWriter}
import fringe.bigIP.BigIP

// Some global constants
object FringeGlobals {
  // BigIP handle
  private var _bigIP: BigIP = _
  def bigIP = _bigIP
  def bigIP_= (value: BigIP): Unit = _bigIP = value

  private var _target: String = ""
  def target = _target
  def target_= (value: String): Unit = {
    bigIP = value match {
      case "zynq" => new fringeZynq.bigIP.BigIPZynq()
      case "aws" => new fringeAWS.bigIP.BigIPAWS()
      case _ => new fringe.bigIP.BigIPSim()
    }
    _target = value
  }

  // tclScript
  private var _tclScript: PrintWriter = {
    val pw = new PrintWriter(new File("bigIP.tcl"))
    pw.println("source clockFreq.tcl")
    pw.flush
    pw
  }
  def tclScript = _tclScript
  def tclScript_= (value: PrintWriter): Unit = _tclScript = value

}
