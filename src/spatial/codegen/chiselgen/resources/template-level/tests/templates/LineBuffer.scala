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

  // Write c.num_lines * c.line_size elements in
  println("Filling Line buffer...")
  var i = 0
  do {
    val en_this_cycle = rnd.nextInt(2)
    poke(c.io.data_in, 100 + i)
    poke(c.io.w_en, en_this_cycle)
    if (en_this_cycle == 1) {
      i = i + 1
    }
    step(1)
  }
  while(i < c.num_lines*c.line_size)
  poke(c.io.w_en, 0)
  
  // The line buffer should now be full, and we can start steady-state testing
  
  // First, read the entire line buffer
  println("Line buffer contents:")
  var rows_concat = List.fill(c.num_lines)(new StringBuilder)
  for (col <- 0 until c.line_size) {
    poke(c.io.col_addr, col)
    for (row <- 0 until c.num_lines) {
      rows_concat(row) ++= peek(c.io.data_out(row)).toString
      rows_concat(row) ++= " "
    }
    step(1)
  }
  for (row <- 0 until c.num_lines) {
    println(rows_concat(row) + " ")
  }
  
  // Now write some new data in
  println("Filling buffered rows...")
  i = 0
  do {
    val en_this_cycle = rnd.nextInt(2)
    poke(c.io.data_in, 200 + i)
    poke(c.io.w_en, en_this_cycle)
    if (en_this_cycle == 1) {
      i = i + 1
    }
    step(1)
  }
  while(i < c.extra_rows_to_buffer*c.line_size)
  poke(c.io.w_en, 0)
  
  // Read the entire line buffer again
  println("Line buffer contents:")
  rows_concat = List.fill(c.num_lines)(new StringBuilder)
  for (col <- 0 until c.line_size) {
    poke(c.io.col_addr, col)
    for (row <- 0 until c.num_lines) {
      rows_concat(row) ++= peek(c.io.data_out(row)).toString
      rows_concat(row) ++= " "
    }
    step(1)
  }
  for (row <- 0 until c.num_lines) {
    println(rows_concat(row) + " ")
  }
  
  // Switch the rows
  println("Switching rows:")
  poke(c.io.r_done, 1)
  step(1)
  poke(c.io.r_done, 0)
  
  // Read the entire line buffer again
  println("Line buffer contents:")
  rows_concat = List.fill(c.num_lines)(new StringBuilder)
  for (col <- 0 until c.line_size) {
    poke(c.io.col_addr, col)
    for (row <- 0 until c.num_lines) {
      rows_concat(row) ++= peek(c.io.data_out(row)).toString
      rows_concat(row) ++= " "
    }
    step(1)
  }
  for (row <- 0 until c.num_lines) {
    println(rows_concat(row) + " ")
  }
  
  // Write some new data in
  println("Filling buffered rows...")
  i = 0
  do {
    val en_this_cycle = rnd.nextInt(2)
    poke(c.io.data_in, 300 + i)
    poke(c.io.w_en, en_this_cycle)
    if (en_this_cycle == 1) {
      i = i + 1
    }
    step(1)
  }
  while(i < c.extra_rows_to_buffer*c.line_size)
  poke(c.io.w_en, 0)
  
  // Switch the rows
  println("Switching rows:")
  poke(c.io.r_done, 1)
  step(1)
  poke(c.io.r_done, 0)
  
  // Read the entire line buffer again
  println("Line buffer contents:")
  rows_concat = List.fill(c.num_lines)(new StringBuilder)
  for (col <- 0 until c.line_size) {
    poke(c.io.col_addr, col)
    for (row <- 0 until c.num_lines) {
      rows_concat(row) ++= peek(c.io.data_out(row)).toString
      rows_concat(row) ++= " "
    }
    step(1)
  }
  for (row <- 0 until c.num_lines) {
    println(rows_concat(row) + " ")
  }
  
}
