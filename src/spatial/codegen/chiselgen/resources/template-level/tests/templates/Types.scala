// See LICENSE.txt for license details.
package types

import chisel3.iotesters.{PeekPokeTester, Driver, ChiselFlatSpec}


class FixedPointTesterTests(c: FixedPointTester) extends PeekPokeTester(c) {
  val num1s = List(1, 2, 3, 1.75, 31.15)
  val num2s = List(9, 32.175, 99.13861)
  num1s.foreach { a => 
  	num2s.foreach { b => 
  	  poke(c.io.num1.raw, (a*scala.math.pow(2,c.f)).toInt)
  	  poke(c.io.num2.raw, (b*scala.math.pow(2,c.f)).toInt)
  	  step(1)
  	  val sum = peek(c.io.add_result.raw)
  	  println(s"$a + $b = ${sum.toDouble/scala.math.pow(2,c.f)}")
  	  expect(c.io.add_result.raw, (a*scala.math.pow(2,c.f).toInt + b*scala.math.pow(2,c.f).toInt).toInt)
  	}
  }
}
