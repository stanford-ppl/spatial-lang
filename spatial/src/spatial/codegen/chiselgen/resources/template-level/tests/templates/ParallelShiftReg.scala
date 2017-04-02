package templates

import chisel3.iotesters.{PeekPokeTester, Driver, ChiselFlatSpec}

class ParallelShiftRegTests(c: ParallelShiftReg) extends PeekPokeTester(c) {

  poke(c.io.reset, 1)
  step(1)
  poke(c.io.reset, 0)
  val numCycles = 20
  var i = 0
  for (cycle <- 0 until numCycles) {
    val en_this_cycle = rnd.nextInt(2)
    (0 until c.stride).foreach { s => 
      poke(c.io.data_in(s), 100 + i*c.stride + (c.stride-1-s))
    }
    poke(c.io.w_en, en_this_cycle)
    (0 until c.size).foreach { s => 
      print(peek(c.io.data_out(s)))
      print(" ")
    }
    println("")
    if (en_this_cycle == 1) {
      print("W_EN HIGH: ")
      i = i + 1
    } else {
      print("W_EN LOW:  ")
    }
    step(1)
    // expect(c.io.output.data, initval)
  }
}
