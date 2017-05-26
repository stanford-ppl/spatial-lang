package templates

import chisel3.iotesters.{PeekPokeTester, Driver, ChiselFlatSpec}
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.IndexedSeq
class ShiftRegFileTests(c: ShiftRegFile) extends PeekPokeTester(c) {

  poke(c.io.reset, 1)
  step(1)
  poke(c.io.reset, 0)

  var gold = ArrayBuffer.fill(c.dims.last,c.dims.head)(0)
  val numCycles = 100
  var i = 0
  for (cycle <- 0 until numCycles) {
    // Shift random crap
    val shift_ens = (0 until c.dims.last).map{i => rnd.nextInt(2)}
    val new_datas = (0 until c.dims.last*c.wPar).map{i => cycle}
    // val waddr_col = rnd.nextInt(c.dims.head)
    // val waddr_row = rnd.nextInt(c.dims.last)
    (0 until c.dims.last*c.wPar).foreach {i => poke(c.io.data_in(i), new_datas(i))}
    (0 until c.dims.last).foreach { i => poke(c.io.shift_en(i), shift_ens(i))}
    step(1)
    c.io.shift_en.foreach { port => poke(port,0)}
    (0 until c.dims.last).foreach { row =>
      if (shift_ens(row) == 1) {
        for (sh <- c.wPar until c.dims.head) { gold(row)(c.dims.head - sh) = gold(row)(c.dims.head - sh-1)}
        for (sh <- 0 until c.wPar) { gold(row)(sh) = new_datas(row*c.wPar + sh)}
      }
      (0 until c.dims.head).foreach { col => 
        val data = peek(c.io.data_out(row*c.dims.head+col))
        val g = gold(row)(col)
        expect(c.io.data_out(row*c.dims.head+col), g)
        print(data + " ")
      }
      println("")
      (0 until c.dims.head).foreach { col =>
        print(gold(row)(col) + " ")
      }
      println("")
    }
    println("\n")
    step(1)
    // expect(c.io.output.data, initval)
  }
}


class NBufShiftRegFileTests(c: NBufShiftRegFile) extends PeekPokeTester(c) {

  poke(c.io.reset, 1)
  step(1)
  poke(c.io.reset, 0)

  var gold = ArrayBuffer.fill(c.dims.last*c.numBufs,c.dims.head)(0)
  val numCycles = 100
  var i = 0
  for (cycle <- 0 until numCycles) {
    // Shit random crap
    val new_datas = (0 until c.dims.last*c.wPar).map{i => cycle}
    (0 until c.dims.last*c.wPar).foreach {i => poke(c.io.data_in(i), new_datas(i))}
    (0 until c.dims.last).foreach { i => poke(c.io.shift_en(i), true)}
    step(1)
    c.io.shift_en.foreach { port => poke(port,0)}
    (0 until c.numBufs).foreach { buf => 
      println("Buffer " + buf + ":")
      (0 until c.dims.last).foreach { row =>
        if (buf == 0) { // update gold entry buffer 
          for (sh <- c.wPar until c.dims.head) { gold(row)(c.dims.head - sh) = gold(row)(c.dims.head - sh-1)}
          for (sh <- 0 until c.wPar) { gold(row)(sh) = new_datas(row*c.wPar + sh)}
        }
        (0 until c.dims.head).foreach { col => 
          val data = peek(c.io.data_out(buf*c.dims.head*c.dims.last+row*c.dims.head+col))
          val g = gold(buf*c.dims.last+row)(col)
          expect(c.io.data_out(buf*c.dims.last*c.dims.head+row*c.dims.head+col), g)
          print(data + " ")
        }
        println("")
        (0 until c.dims.head).foreach { col =>
          print(gold(buf*c.dims.last+row)(col) + " ")
        }
        println("")
      }
    }
    println("\n")
    if (cycle % (c.dims.head/2) == 0) {
      // Force buffer swap
      poke(c.io.sEn(0), 1)
      step(5)
      poke(c.io.sDone(0), 1)
      step(1)
      poke(c.io.sEn(0), 0)
      poke(c.io.sDone(0), 0)
      step(1)
      // Swap golds
      for (buf <- (c.numBufs-1) until 0 by -1){
        for (row <- 0 until c.dims.last) {
          for (col <- 0 until c.dims.head) {
            gold(buf*c.dims.last + row)(col) = gold((buf-1)*c.dims.last + row)(col)
          }
        }
      }
    }
    step(1)
    // expect(c.io.output.data, initval)
  }
}
