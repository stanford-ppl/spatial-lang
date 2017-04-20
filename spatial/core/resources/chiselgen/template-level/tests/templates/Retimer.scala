// See LICENSE.txt for license details.
package templates

import chisel3.iotesters.{PeekPokeTester, Driver, ChiselFlatSpec}
import org.scalatest.Assertions._

class RetimerTests(c: Retimer) extends PeekPokeTester(c) {
  step(1)
  reset(1)
  var memory = Array.tabulate(c.length+1) { i => 0 }
  poke(c.io.input.en, 1)
  poke(c.io.input.init, 0)
  var head = 0
  var tail = if (c.length > 0) 1 else 0
  for (i <- 0 until 100) {
    val data = rnd.nextInt(10)
    memory(head) = data
    poke(c.io.input.data, data)
    // val a = peek(c.io.output.data)
    // println(s"writing $data to slot $head")
    // println(s"expect ${memory(tail)} in slot $tail and seeing $a")
    // println(s"")
    expect(c.io.output.data, memory(tail))
    head = if (head == c.length) 0 else head + 1
    tail = if (tail == c.length) 0 else tail + 1
    step(1)
  }


}

// class RetimerTester extends ChiselFlatSpec {
//   behavior of "Retimer"
//   backends foreach {backend =>
//     it should s"correctly add randomly generated numbers $backend" in {
//       Driver(() => new Retimer(10))(c => new RetimerTests(c)) should be (true)
//     }
//   }
// }


