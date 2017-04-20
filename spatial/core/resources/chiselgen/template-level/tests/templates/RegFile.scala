package templates

import chisel3.iotesters.{PeekPokeTester, Driver, ChiselFlatSpec}

class RegFileTests(c: RegFile) extends PeekPokeTester(c) {

  poke(c.io.reset, 1)
  step(1)
  poke(c.io.reset, 0)
  val numCycles = 20
  var i = 0
  for (cycle <- 0 until numCycles) {
    val en_this_cycle = rnd.nextInt(2)
    val w_addr = rnd.nextInt(c.size)
    poke(c.io.data_in, 100 + i)
    poke(c.io.w_en, en_this_cycle)
    poke(c.io.w_addr, w_addr)
    (0 until c.size).foreach { s => 
      print(peek(c.io.data_out(s)))
      print(" ")
    }
    println("")
    print("w_addr = " + w_addr + ", ")
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
