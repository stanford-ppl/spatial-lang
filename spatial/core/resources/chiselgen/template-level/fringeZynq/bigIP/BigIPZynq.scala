package fringe.fringeZynq.bigIP
import fringe.FringeGlobals
import fringe.bigIP.BigIP
import chisel3._
import chisel3.util._
import scala.collection.mutable.Set

class BigIPZynq extends BigIP with ZynqBlackBoxes {
  def divide(dividend: UInt, divisor: UInt, latency: Int = 69): UInt = {
    val m = Module(new Divider(dividend.getWidth, divisor.getWidth, latency))
    m.io.dividend := dividend
    m.io.divisor := divisor
    m.io.out
  }
}


