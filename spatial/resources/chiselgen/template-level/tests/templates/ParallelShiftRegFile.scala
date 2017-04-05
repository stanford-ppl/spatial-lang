package templates

import chisel3.iotesters.{PeekPokeTester, Driver, ChiselFlatSpec}

class ParallelShiftRegFileTests(c: ParallelShiftRegFile) extends PeekPokeTester(c) {

  poke(c.io.reset, 1)
  step(1)
  poke(c.io.reset, 0)
  val numCycles = 40
  var i = 0
  for (cycle <- 0 until numCycles) {
    val shift_en_this_cycle = rnd.nextInt(2)
    val w_en_this_cycle = rnd.nextInt(2)
    val w_addr = rnd.nextInt(c.width)
    if (shift_en_this_cycle == 1) {
      (0 until c.stride).foreach { s => 
        poke(c.io.data_in(s), 100 + i*c.stride + (c.stride-1-s))
      }
    } else if (w_en_this_cycle == 1) {
      poke(c.io.data_in(0), 200 + i)
      poke(c.io.w_rowAddr(0), 0)
      poke(c.io.w_colAddr(0), w_addr)
    }
    poke(c.io.shift_en(0), shift_en_this_cycle)
    poke(c.io.w_en(0), w_en_this_cycle)
    (0 until c.width).foreach { s => 
      print(peek(c.io.data_out(s)))
      print(" ")
    }
    println("")
    if (shift_en_this_cycle == 1) {
      print("SHIFT_EN HIGH: ")
      i = i + 1
    } else if (w_en_this_cycle == 1) {
      print("w_addr = " + w_addr + ", ")
      print("W_EN HIGH:     ")
      i = i + 1
    } else {
      print("BOTH LOW:      ")
    }
    step(1)
    // expect(c.io.output.data, initval)
  }
}
