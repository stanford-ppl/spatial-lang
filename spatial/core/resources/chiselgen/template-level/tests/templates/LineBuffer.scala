package templates

import chisel3.iotesters.{PeekPokeTester, Driver, ChiselFlatSpec}
import scala.math._
import scala.collection.mutable.ArrayBuffer
import java.io.PrintWriter
import scala.io.Source

class LineBufferTests(c: LineBuffer) extends PeekPokeTester(c) {
    
  poke(c.io.reset, 1)
  step(1)
  poke(c.io.reset, 0)

  // println("Flush buffer")
  for (i <- 0 until c.num_lines*c.line_size + 1 by c.col_wPar) {
    poke(c.io.sEn(0), 1)
    poke(c.io.w_en, 1)
    for (j <- 0 until c.col_wPar) {
      poke(c.io.data_in(j), 0)      
    }
    step(1)
    if (i % c.line_size == 0) {
      poke(c.io.sDone(0), 1)
      poke(c.io.w_en, 0)
      step(1)
      poke(c.io.sEn(0), 0)
      poke(c.io.sDone(0), 0)
      step(1)
    }

  }
  poke(c.io.w_en, 0)
  step(1)

  val iters = (c.num_lines + c.extra_rows_to_buffer) * 3
  for (iter <- 0 until iters) {
    // println("Filling lines")
    for (k <- 0 until c.extra_rows_to_buffer) {
      for (i <- 0 until c.line_size by c.col_wPar) {
        poke(c.io.sEn(0), 1)
        poke(c.io.w_en, 1)
        for (j <- 0 until c.col_wPar) {
          poke(c.io.data_in(j), 100*(iter*c.extra_rows_to_buffer+k) + i + j)      
        }
        step(1)
      }
      poke(c.io.sDone(0), 1)
      poke(c.io.w_en, 0)
      step(1)
      poke(c.io.sEn(0), 0)
      poke(c.io.sDone(0), 0)
      step(1)
    }

    // println("Checking line")
    var rows_concat = List.fill(c.num_lines)(new StringBuilder)
    var gold_concat = List.fill(c.num_lines)(new StringBuilder)
    for (col <- 0 until c.line_size by c.col_rPar) {
      for (j <- 0 until c.col_rPar) {
        poke(c.io.col_addr(j), col + j)
      }
      for (row <- 0 until c.num_lines) {
        val init = if (iter*c.extra_rows_to_buffer - (c.num_lines - c.extra_rows_to_buffer - row) < 0) 0 else 1
        val scalar = iter*c.extra_rows_to_buffer - (c.num_lines - c.extra_rows_to_buffer - row)
        for (j <- 0 until c.col_rPar) {
          val r = peek(c.io.data_out(row*c.col_rPar + j))
          val g = init*(scalar*100 + col + j)
          expect(c.io.data_out(row*c.col_rPar+j), g)
          rows_concat(row) ++= r.toString
          rows_concat(row) ++= " "        
          gold_concat(row) ++= g.toString
          gold_concat(row) ++= " "        
        }
      }
      step(1)
    }
    println("Saw:")
    for (row <- 0 until c.num_lines) {
      println(rows_concat(row) + " ")
    }
    println("Expected:")
    for (row <- 0 until c.num_lines) {
      println(gold_concat(row) + " ")
    }

  }

  
}
