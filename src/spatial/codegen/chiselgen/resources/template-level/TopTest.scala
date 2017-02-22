// See LICENSE for license details.

package example

import chisel3.core.Module
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import scala.collection.mutable.ListBuffer

/**
 * Top test harness
 */
class TopUnitTester(c: Top)(implicit args: Array[String]) extends ArgsTester(c) {

  // ---- Fringe software API ----
  def writeReg(reg: Int, data: Int) {
    poke(c.io.waddr, reg)
    poke(c.io.wdata, data)
    poke(c.io.wen, 1)
    step(1)
    poke(c.io.wen, 0)
  }

  def readReg(reg: Int): Int = {
    poke(c.io.raddr, reg)
    peek(c.io.rdata).toInt
  }

  def run() = {
    var numCycles = 0
    var status = 0
    writeReg(c.fringe.commandReg, 1)
    while ((status == 0) && (numCycles <= 100)) {
      step(1)
      status = readReg(c.fringe.statusReg)
      numCycles += 1
    }
    numCycles
  }

  // ---- Host code ----
  // Write to all argIns: Regs 2..numArgIns+2
  for (i <- 2 until c.numArgIns+2) {
    if (i == 2) writeReg(i, 96)
    else writeReg(i, (i-1)*2)  // Write pattern 4,6..
  }
  run()
  // Read all argOuts: numargIns+2..numArgIns+2+numArgOuts
  val argOuts = List.tabulate(c.numArgOuts) { i =>
    readReg(c.numArgIns+2+i)
  }

  println(s"argOuts: $argOuts")
}

object TopTest extends CommonMain {
  type DUTType = Top
  def dut = () => {
    val w = 32
    val numArgIns = 1
    val numArgOuts = 1
    val numMemoryStreams = 2
    new Top(w, numArgIns, numArgOuts, numMemoryStreams)
  }
  def tester = { c: DUTType => new TopUnitTester(c) }
}

