package templates

import chisel3.iotesters.{PeekPokeTester, Driver, ChiselFlatSpec}
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.IndexedSeq
class ParallelShiftRegFileTests(c: ParallelShiftRegFile) extends PeekPokeTester(c) {

  poke(c.io.reset, 1)
  step(1)
  poke(c.io.reset, 0)

  var gold = ArrayBuffer.fill(c.height,c.width)(0)
  val numCycles = 100
  var i = 0
  for (cycle <- 0 until numCycles) {
    // Shift random crap
    val shift_ens = (0 until c.height).map{i => rnd.nextInt(2)}
    val new_datas = (0 until c.height*c.wPar).map{i => cycle}
    // val waddr_col = rnd.nextInt(c.width)
    // val waddr_row = rnd.nextInt(c.height)
    (0 until c.height*c.wPar).foreach {i => poke(c.io.data_in(i), new_datas(i))}
    (0 until c.height).foreach { i => poke(c.io.shift_en(i), shift_ens(i))}
    step(1)
    c.io.shift_en.foreach { port => poke(port,0)}
    (0 until c.height).foreach { row =>
      if (shift_ens(row) == 1) {
        for (sh <- c.wPar until c.width) { gold(row)(c.width - sh) = gold(row)(c.width - sh-1)}
        for (sh <- 0 until c.wPar) { gold(row)(sh) = new_datas(row*c.wPar + sh)}
      }
      (0 until c.width).foreach { col => 
        val data = peek(c.io.data_out(row*c.width+col))
        val g = gold(row)(col)
        expect(c.io.data_out(row*c.width+col), g)
        print(data + " ")
      }
      println("")
      (0 until c.width).foreach { col =>
        print(gold(row)(col) + " ")
      }
      println("")
    }
    println("\n")
    step(1)
    // expect(c.io.output.data, initval)
  }
}
