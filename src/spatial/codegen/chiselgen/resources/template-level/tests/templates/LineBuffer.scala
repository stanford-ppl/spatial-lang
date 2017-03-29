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
    poke(c.io.sEn(0), 1)
    poke(c.io.w_en, 1)
    for (j <- 0 until c.col_wPar) {
      poke(c.io.data_in(j), 100 + i + j)      
    }
    i = i + c.col_wPar
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
  while(i < c.num_lines*c.line_size)
  poke(c.io.w_en, 0)
  
  // The line buffer should now be full, and we can start steady-state testing
  
  // First, read the entire line buffer
  println("Line buffer contents:")
  var rows_concat = List.fill(c.num_lines)(new StringBuilder)
  for (col <- 0 until c.line_size by c.col_rPar) {
    for (j <- 0 until c.col_rPar) {
      poke(c.io.col_addr(j), col + j)
    }
    for (row <- 0 until c.num_lines) {
      for (j <- 0 until c.col_rPar) {
        rows_concat(row) ++= peek(c.io.data_out(row*c.col_rPar + j)).toString
        rows_concat(row) ++= " "        
      }
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
    poke(c.io.sEn(0), 1)
    poke(c.io.w_en, 1)
    for (j <- 0 until c.col_wPar) {
      poke(c.io.data_in(j), 200 + i + j)      
    }
    i = i + c.col_wPar
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
  while(i < c.extra_rows_to_buffer*c.line_size)
  poke(c.io.w_en, 0)
  
  // Read the entire line buffer again
  println("Line buffer contents:")
  rows_concat = List.fill(c.num_lines)(new StringBuilder)
  for (col <- 0 until c.line_size by c.col_rPar) {
    for (j <- 0 until c.col_rPar) {
      poke(c.io.col_addr(j), col + j)
    }
    for (row <- 0 until c.num_lines) {
      for (j <- 0 until c.col_rPar) {
        rows_concat(row) ++= peek(c.io.data_out(row*c.col_rPar + j)).toString
        rows_concat(row) ++= " "        
      }
    }
    step(1)
  }
  for (row <- 0 until c.num_lines) {
    println(rows_concat(row) + " ")
  }
  
  step(1)

  // Write some new data in
  println("Filling buffered rows...")
  i = 0
  do {
    poke(c.io.sEn(0), 1)
    poke(c.io.w_en, 1)
    for (j <- 0 until c.col_wPar) {
      poke(c.io.data_in(j), 300 + i + j)      
    }
    i = i + c.col_wPar
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
  while(i < c.extra_rows_to_buffer*c.line_size)
  poke(c.io.w_en, 0)
  
  // Switch the rows
  println("Switching rows:")
  poke(c.io.sDone(1), 1)
  step(1)
  poke(c.io.sDone(1), 0)
  poke(c.io.sEn(1), 0)
  
  // Read the entire line buffer again
  println("Line buffer contents:")
  rows_concat = List.fill(c.num_lines)(new StringBuilder)
  for (col <- 0 until c.line_size by c.col_rPar) {
    for (j <- 0 until c.col_rPar) {
      poke(c.io.col_addr(j), col + j)
    }
    for (row <- 0 until c.num_lines) {
      for (j <- 0 until c.col_rPar) {
        rows_concat(row) ++= peek(c.io.data_out(row*c.col_rPar + j)).toString
        rows_concat(row) ++= " "        
      }
    }
    step(1)
  }
  for (row <- 0 until c.num_lines) {
    println(rows_concat(row) + " ")
  }
  
}
