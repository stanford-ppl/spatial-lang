package fringe.bigIP
import chisel3._
import chisel3.util._

class BigIPSim extends BigIP {
  def divide(dividend: UInt, divisor: UInt, latency: Int) = {
    ShiftRegister(dividend/divisor, latency)
  }
}


