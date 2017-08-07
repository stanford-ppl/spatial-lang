package fringe.bigIP
import chisel3._
import chisel3.util._

class BigIPSim extends BigIP {
  def divide(dividend: UInt, divisor: UInt, latency: Int): UInt = {
    ShiftRegister(dividend/divisor, latency)
  }
  def divide(dividend: SInt, divisor: SInt, latency: Int): SInt = {
    ShiftRegister(dividend/divisor, latency)
  }

}


