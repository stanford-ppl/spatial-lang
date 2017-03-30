// See LICENSE for license details.

package top

import fringe._
import accel._
import chisel3.core.Module
import chisel3._
import chisel3.util._
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import scala.collection.mutable.ListBuffer
/**
 * Top test harness
 */
class TopUnitTester(c: Top)(implicit args: Array[String]) extends ArgsTester(c) {
}

object Instantiator extends CommonMain {
  type DUTType = Top
  
  def supportedTarget(t: String) = t match {
    case "aws" => true
    case "zynq" => true
    case "verilator" => true
    case "de1soc" => true
    case _ => false
  }
  
  def dut = () => {
    
    // Memory streams
    val numMemoryStreams = 0 + 0
    val numArgIns_mem = 0 + 0
    
    // Scalars
    val numArgIns_reg = 1
    val numArgOuts_reg = 1
    //x150_argin = argIns(0) ( x )
    //x151_argout = argOuts(0) ( y )
    val w = 32
    val numArgIns = numArgIns_mem  + numArgIns_reg
    val numArgOuts = numArgOuts_reg
    val target = if (args.size > 0) args(0) else "verilator" 
    Predef.assert(supportedTarget(target), s"ERROR: Unsupported Fringe target '$target'")
    new Top(w, numArgIns, numArgOuts, numMemoryStreams, target)
  }
  def tester = { c: DUTType => new TopUnitTester(c) }
}
