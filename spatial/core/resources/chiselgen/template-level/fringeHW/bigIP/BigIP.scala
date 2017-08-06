package fringe.bigIP
import chisel3._
import chisel3.util._

/**
 * Target-specific IP
 */
abstract class BigIP {
  def divide(dividend: UInt, divisor: UInt, latency: Int): UInt
}


