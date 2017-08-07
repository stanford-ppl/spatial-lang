package fringe.bigIP
import chisel3._
import chisel3.util._

class BigIPSim extends BigIP {
  def divide(dividend: UInt, divisor: UInt, latency: Int): UInt = {
    getConst(divisor) match { // Use combinational Verilog divider and ignore latency if divisor is constant
      case Some(bigNum) => dividend / bigNum.U
      case None => ShiftRegister(dividend/divisor, latency)
    }
  }

  def divide(dividend: SInt, divisor: SInt, latency: Int): SInt = {
    getConst(divisor) match { // Use combinational Verilog divider and ignore latency if divisor is constant
      case Some(bigNum) => dividend / bigNum.S
      case None => ShiftRegister(dividend/divisor, latency)
    }
  }

  def mod(dividend: UInt, divisor: UInt, latency: Int): UInt = {
    getConst(divisor) match { // Use combinational Verilog divider and ignore latency if divisor is constant
      case Some(bigNum) => dividend % bigNum.U
      case None => ShiftRegister(dividend % divisor, latency)
    }
  }

  def mod(dividend: SInt, divisor: SInt, latency: Int): SInt = {
    getConst(divisor) match { // Use combinational Verilog divider and ignore latency if divisor is constant
      case Some(bigNum) => dividend % bigNum.S
      case None => ShiftRegister(dividend % divisor, latency)
    }
  }

  def multiply(a: UInt, b: UInt, latency: Int): UInt = {
    val aconst = getConst(a)
    val bconst = getConst(b)
    if (aconst.isDefined | bconst.isDefined) { // Constant optimization
      if (aconst.isDefined && bconst.isDefined) { (aconst.get * bconst.get).U }
      else {
        val const = if (aconst.isDefined) aconst.get else bconst.get
        val other = if (aconst.isDefined) b else a
        ShiftRegister(const.U * other, 0)
      }
    } else {
      ShiftRegister(a * b, latency)
    }
  }
  def multiply(a: SInt, b: SInt, latency: Int): SInt = {
    val aconst = getConst(a)
    val bconst = getConst(b)
    if (aconst.isDefined | bconst.isDefined) { // Constant optimization
      if (aconst.isDefined && bconst.isDefined) { (aconst.get * bconst.get).S }
      else {
        val const = if (aconst.isDefined) aconst.get else bconst.get
        val other = if (aconst.isDefined) b else a
        ShiftRegister(const.S * other, 0)
      }
    } else {
      ShiftRegister(a * b, latency)
    }
  }
}


