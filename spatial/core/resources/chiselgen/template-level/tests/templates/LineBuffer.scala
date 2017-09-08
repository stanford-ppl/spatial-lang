package templates

import chisel3.iotesters.{PeekPokeTester, Driver, ChiselFlatSpec}
import scala.math._
import scala.collection.mutable.ArrayBuffer
import java.io.PrintWriter
import scala.io.Source

class LineBufferTests(c: LineBuffer) extends PeekPokeTester(c) {
    
  var gold = ArrayBuffer.fill[BigInt](c.extra_rows_to_buffer + c.num_lines, c.line_size)(0)

  def swap(): Unit = {
    // Poke lb
    poke(c.io.sDone(0), 1)
    step(1)
    poke(c.io.sEn(0), 0)
    poke(c.io.sDone(0), 0)
    step(1)

    // Handle gold
<<<<<<< Updated upstream
    for (i <- c.num_lines+c.extra_rows_to_buffer-1 until c.rstride-1 by -1) {
=======
    for (i <- c.num_lines+c.extra_rows_to_buffer-1 until c.rstride-1) {
>>>>>>> Stashed changes
      for (j <- 0 until c.line_size) {
        gold(i)(j) = gold(i-c.rstride)(j)
      }
    }
    for (i <- 0 until c.rstride){
      for (j <- 0 until c.line_size) {
        gold(i)(j) = 0
      }      
    }
  }

  def printGold(): Unit = {
    println("Current Gold:")
<<<<<<< Updated upstream
    for (i <- c.num_lines+c.extra_rows_to_buffer-1 to 0 by -1) {
=======
    for (i <- 0 until c.num_lines+c.extra_rows_to_buffer) {
>>>>>>> Stashed changes
      println("")
      for (j <- 0 until c.line_size) {
        print("\t" + gold(i)(j))
      }
<<<<<<< Updated upstream
      if (i < c.rstride) print("   <- load row\t")
      else if (i < c.extra_rows_to_buffer) print("   <- invisible row\t")
      else print("      \t")
=======
      if (i < c.rstride) print("\t <- load row")
      else if (i < c.extra_rows_to_buffer) print("\t <- invisible row")
>>>>>>> Stashed changes
    }
  }

  poke(c.io.reset, 1)
  step(1)
  poke(c.io.reset, 0)


  // Flush lb
  for (i <- 0 until c.num_lines*c.line_size + 1 by c.col_wPar) {
    poke(c.io.sEn(0), 1)
    for (j <- 0 until c.rstride) {
      poke(c.io.w_en(j), 1)
      for (k <- 0 until c.col_wPar) {
        poke(c.io.data_in(j*c.col_wPar + k), 0)      
      }
    }
    step(1)
    if (i % c.line_size == 0) {
      swap()
    }

  }

  for(j <- 0 until c.rstride){
    poke(c.io.w_en(j), 0)
  }
  step(1)

  val iters = (c.num_lines + c.extra_rows_to_buffer) * 3
  for (iter <- 0 until iters) {
    // Write to lb
    for (k <- 0 until c.rstride) {
      poke(c.io.w_en(k), 1)
      for (i <- 0 until c.line_size by c.col_wPar) {
        poke(c.io.sEn(0), 1)
        for (j <- 0 until c.col_wPar) {
          poke(c.io.data_in(k*c.col_wPar + j), 100*(iter*c.rstride+k) + i + j)      
        }
        step(1)
      }
      poke(c.io.w_en(k), 0)
      // Write to gold
      for(j <- 0 until c.line_size){
        gold(c.rstride - k - 1)(j) = 100*(iter*c.rstride+k) + j
      }
    }
    step(1)
    swap()

    // println("Checking line")
    var rows_concat = List.fill(c.num_lines)(new StringBuilder)
    for (col <- 0 until c.line_size by c.col_rPar) {
      for (j <- 0 until c.col_rPar) {
        poke(c.io.col_addr(j), col + j)
      }
      for (row <- 0 until c.num_lines) {
        val init = if (iter*c.extra_rows_to_buffer - (c.num_lines - c.extra_rows_to_buffer - row) < 0) 0 else 1
        val scalar = iter*c.extra_rows_to_buffer - (c.num_lines - c.extra_rows_to_buffer - row)
        for (j <- 0 until c.col_rPar) {
          val r = peek(c.io.data_out(row*c.col_rPar + j))
          val g = gold(c.num_lines - row + c.extra_rows_to_buffer - 1)(col+j)
          expect(c.io.data_out(row*c.col_rPar+j), g)
          rows_concat(row) ++= r.toString
          rows_concat(row) ++= "\t"        
        }
      }
      step(1)
    }
    println("Saw:")
    for (row <- 0 until c.num_lines) {
      println(rows_concat(row) + " ")
    }
    printGold()
    println("")
    println("")

  }

  
}
