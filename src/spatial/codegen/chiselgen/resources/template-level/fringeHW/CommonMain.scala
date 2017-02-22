// See LICENSE for license details.

package example

import chisel3.core.Module
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}


abstract class ArgsTester[+T <: Module](c: T)(implicit args: Array[String]) extends PeekPokeTester(c) {
  def printFail(msg: String) = println(Console.BLACK + Console.RED_B + s"FAIL: $msg" + Console.RESET)
  def printPass(msg: String) = println(Console.BLACK + Console.GREEN_B + s"PASS: $msg" + Console.RESET)
}

trait CommonMain {
  /**
   * 'args' variable that holds commandline arguments
   * TODO: Is using a var the best way to handle this?
   */
  implicit var args: Array[String] = _
  case class SplitArgs(chiselArgs: Array[String], testArgs: Array[String])

  type DUTType <: Module
  def dut: () => DUTType
  def tester: DUTType => ArgsTester[DUTType]

  def separateChiselArgs(args: Array[String]) = {
    val argSeparator = "--testArgs"
    val (chiselArgs, otherArgs) = if (args.contains("--testArgs")) {
      args.splitAt(args.indexOf("--testArgs"))
    } else {
      (args, Array[String]())
    }
    val actualChiselArgs = if (chiselArgs.size == 0) Array("--help") else chiselArgs
    val testArgs = otherArgs.drop(1)
    SplitArgs(actualChiselArgs, testArgs)
  }

  def main(args: Array[String]) {
    val splitArgs = separateChiselArgs(args)
    this.args = splitArgs.testArgs
    if (splitArgs.chiselArgs.contains("--test-command")) {
      val cmd = splitArgs.chiselArgs(splitArgs.chiselArgs.indexOf("--test-command")+1)
      Driver.run(dut, cmd)(tester)
    } else {
      Driver.execute(splitArgs.chiselArgs, dut)(tester)
    }
  }
}
