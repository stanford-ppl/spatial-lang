package templates

import chisel3.iotesters.{PeekPokeTester, Driver, ChiselFlatSpec}
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.IndexedSeq
class ShiftRegFileTests(c: ShiftRegFile) extends PeekPokeTester(c) {

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


class NBufShiftRegFileTests(c: NBufShiftRegFile) extends PeekPokeTester(c) {

  poke(c.io.reset, 1)
  step(1)
  poke(c.io.reset, 0)

  var gold = ArrayBuffer.fill(c.height*c.numBufs,c.width)(0)
  val numCycles = 100
  var i = 0
  for (cycle <- 0 until numCycles) {
    // Shit random crap
    val new_datas = (0 until c.height*c.wPar).map{i => cycle}
    (0 until c.height*c.wPar).foreach {i => poke(c.io.data_in(i), new_datas(i))}
    (0 until c.height).foreach { i => poke(c.io.shift_en(i), true)}
    step(1)
    c.io.shift_en.foreach { port => poke(port,0)}
    (0 until c.numBufs).foreach { buf => 
      println("Buffer " + buf + ":")
      (0 until c.height).foreach { row =>
        if (buf == 0) { // update gold entry buffer 
          for (sh <- c.wPar until c.width) { gold(row)(c.width - sh) = gold(row)(c.width - sh-1)}
          for (sh <- 0 until c.wPar) { gold(row)(sh) = new_datas(row*c.wPar + sh)}
        }
        (0 until c.width).foreach { col => 
          val data = peek(c.io.data_out(buf*c.width*c.height+row*c.width+col))
          val g = gold(buf*c.height+row)(col)
          expect(c.io.data_out(buf*c.height*c.width+row*c.width+col), g)
          print(data + " ")
        }
        println("")
        (0 until c.width).foreach { col =>
          print(gold(buf*c.height+row)(col) + " ")
        }
        println("")
      }
    }
    println("\n")
    if (cycle % (c.width/2) == 0) {
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
        for (row <- 0 until c.height) {
          for (col <- 0 until c.width) {
            gold(buf*c.height + row)(col) = gold((buf-1)*c.height + row)(col)
          }
        }
      }
    }
    step(1)
    // expect(c.io.output.data, initval)
  }
}
