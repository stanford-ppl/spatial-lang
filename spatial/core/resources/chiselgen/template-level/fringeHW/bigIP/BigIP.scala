package fringe.bigIP
import chisel3._
import chisel3.util._

/**
 * Target-specific IP
 */
abstract class BigIP {
  def getConst[T<:Data](sig: T): Option[BigInt] = sig match {
    case u: UInt => if (u.litArg.isDefined) Some(u.litArg.get.num) else None
    case s: UInt => if (s.litArg.isDefined) Some(s.litArg.get.num) else None
    case _ => None
  }

  def divide(dividend: UInt, divisor: UInt, latency: Int): UInt
  def divide(dividend: SInt, divisor: SInt, latency: Int): SInt
  def mod(dividend: UInt, divisor: UInt, latency: Int): UInt
  def mod(dividend: SInt, divisor: SInt, latency: Int): SInt

}


