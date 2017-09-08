package templates

import chisel3.iotesters.{PeekPokeTester, Driver, ChiselFlatSpec}
import scala.math._
import scala.collection.mutable.ArrayBuffer
import java.io.PrintWriter
import scala.io.Source

class SystolicArray2DTests(c: SystolicArray2D) extends PeekPokeTester(c) {
    
  poke(c.io.reset, 1)
  step(1)
  poke(c.io.reset, 0)

  // Create super square for input data
  val super_square = (0 until c.super_square_dims(0)).map{i => 
    (0 until c.super_square_dims(1)).map{j => 
      BigInt((i*c.super_square_dims(1) + j))
    }
  }

  // Create super square mask
  val super_square_mask = (0 until c.super_square_dims(0)).map{i => 
    (0 until c.super_square_dims(1)).map{j => 
      if (i < c.edge_thickness(0)._1 && j < c.edge_thickness(1)._1) { // Quadrant 0
        if (c.vertex_thickness(0)._1 * c.vertex_thickness(0)._2 == 0) 0 else 1
      } else if (i < c.edge_thickness(0)._1 && j >= c.edge_thickness(1)._1+c.dims(1)) { // Quadrant 1
        if (c.vertex_thickness(1)._1 * c.vertex_thickness(1)._2 == 0) 0 else 1
      } else if (j < c.edge_thickness(1)._1 && i >= c.edge_thickness(0)._1+c.dims(0)) { // Quadrant 2
        if (c.vertex_thickness(2)._1 * c.vertex_thickness(2)._2 == 0) 0 else 1
      } else if (i >= c.edge_thickness(0)._1+c.dims(0) && j >= c.edge_thickness(1)._1+c.dims(1)) { // Quadrant 3
        if (c.vertex_thickness(3)._1 * c.vertex_thickness(3)._2 == 0) 0 else 1
      } else if (i < c.edge_thickness(0)._1) { // Neg Edge 1
        if (c.edge_thickness(0)._1 > 0) 1 else 0
      } else if (j < c.edge_thickness(1)._1) { // Neg Edge 0
        if (c.edge_thickness(1)._1 > 0) 1 else 0
      } else if (i >= c.edge_thickness(0)._1+c.dims(0)) { // Pos Edge 1
        if (c.edge_thickness(0)._2 > 0) 1 else 0
      } else if (j >= c.edge_thickness(1)._1+c.dims(1)) { // Pos Edge 0
        if (c.edge_thickness(1)._2 > 0) 1 else 0
      } else { 0 }
    }
  }

  println("neighborhood (* = self)")
  for (i <- 0 until c.neighborhood_size(0)) {
    println("")
    for (j <- 0 until c.neighborhood_size(1)) {
      print("\t" + c.movement_scalars(i * c.neighborhood_size(1) + j))
      if (c.self_position(i * c.neighborhood_size(1) + j) == 1) print("*")
    }
    print("\t")
  }
  // Show input
  println("\nMasked input square")
  for (i <- 0 until c.super_square_dims(0)) {
    println("")
    for (j <- 0 until c.super_square_dims(1)) {
      if (super_square_mask(i)(j) == 0) print("\t-") else print("\t" + super_square(i)(j))
    }
    print("\t")
  }

  var gold = ArrayBuffer.fill[BigInt](c.super_square_dims(0), c.super_square_dims(1))(0)
  for (i <- 0 until c.super_square_dims(0)) {
    for (j <- 0 until c.super_square_dims(1)){
      if (super_square_mask(i)(j) == 1) gold(i)(j) = super_square(i)(j)
    }
  }
  var gold_tmp = ArrayBuffer.fill[BigInt](c.super_square_dims(0), c.super_square_dims(1))(0)
  for (i <- 0 until c.super_square_dims(0)) {
    for (j <- 0 until c.super_square_dims(1)){
      if (super_square_mask(i)(j) == 1) gold_tmp(i)(j) = super_square(i)(j)
    }
  }

  def getArray(): List[BigInt] = {
    val arr = (0 until c.dims.product).map{ i =>
      peek(c.io.out(i))
    }
    println("\nCurrent Array:")
    for(i <- 0 until c.dims(0)) {
      println(" ")
      for (j <- 0 until c.dims(1)) {
        if (j == 0) print("\t")
        print("\t" + arr(i*c.dims(1)+j))
      }
      print("\t")
    }
    arr.toList
  }

  def updateGold(): Unit = {
    for(i <- 0 until c.dims(0)){
      for (j <- 0 until c.dims(1)){
        val new_val = (0 until c.neighborhood_size(0)).map { ii =>
          (0 until c.neighborhood_size(1)).map { jj =>
            val weight = c.movement_scalars(ii*c.neighborhood_size(1) + jj)
            val src_row = i - c.self_coords(0) + ii
            val src_col = j - c.self_coords(1) + jj
            val super_square_row = c.edge_thickness(0)._1 + src_row
            val super_square_col = c.edge_thickness(1)._1 + src_col  
            if (weight != 0) {
              gold(super_square_row)(super_square_col) * BigInt(weight.toInt)
            } else {
              BigInt(0)
            }
          }
        }.flatten.reduce{(a,b) => 
          c.operation match {
            case Sum      => a + b  
            case Product  => a * b 
            case Min      => if (a < b) a else b 
            case Max      => if (a < b) b else a
          }
        }
        val start_0 = c.edge_thickness(0)._1
        val start_1 = c.edge_thickness(1)._1
        gold_tmp(i+start_0)(j+start_1) = new_val.toInt
      }
    }
    for(i <- 0 until c.super_square_dims(0)){
      for(j <- 0 until c.super_square_dims(1)){
        gold(i)(j) = gold_tmp(i)(j)
      }
    }
  }

  // Touch inputs
  var id = 0
  for (i <- 0 until c.super_square_dims(0)) {
    for (j <- 0 until c.super_square_dims(1)) {
      if (super_square_mask(i)(j) == 1) {
        poke(c.io.in(id), super_square(i)(j))
        id = id + 1
      }
    }
  }

  val num_shifts = 10
  for (i <- 0 until num_shifts) { 
    step(1)
    poke(c.io.shiftEn, 1)
    step(1)
    poke(c.io.shiftEn, 0)
    val result = getArray()
    updateGold()

    val start_0 = c.edge_thickness(0)._1
    val start_1 = c.edge_thickness(1)._1
    for(ii <- 0 until c.dims(0)) {
      for (jj <- 0 until c.dims(1)) {
        val p = peek(c.io.out(ii*c.dims(1)+jj))
        if (p != gold(start_0+ii)(start_1+jj)) println(" Failure, " + p + " =/= " + gold(start_0+ii)(start_1+jj))
        expect(c.io.out(ii*c.dims(1) + jj), gold(start_0+ii)(start_1+jj))
      }
    }


  }



  // // println("Flush buffer")
  // for (i <- 0 until c.num_lines*c.line_size + 1 by c.col_wPar) {
  //   poke(c.io.sEn(0), 1)
  //   poke(c.io.w_en, 1)
  //   for (j <- 0 until c.col_wPar) {
  //     poke(c.io.data_in(j), 0)      
  //   }
  //   step(1)
  //   if (i % c.line_size == 0) {
  //     poke(c.io.sDone(0), 1)
  //     poke(c.io.w_en, 0)
  //     step(1)
  //     poke(c.io.sEn(0), 0)
  //     poke(c.io.sDone(0), 0)
  //     step(1)
  //   }

  // }
  // poke(c.io.w_en, 0)
  // step(1)

  // val iters = (c.num_lines + c.extra_rows_to_buffer) * 3
  // for (iter <- 0 until iters) {
  //   // println("Filling lines")
  //   for (k <- 0 until c.extra_rows_to_buffer) {
  //     for (i <- 0 until c.line_size by c.col_wPar) {
  //       poke(c.io.sEn(0), 1)
  //       poke(c.io.w_en, 1)
  //       for (j <- 0 until c.col_wPar) {
  //         poke(c.io.data_in(j), 100*(iter*c.extra_rows_to_buffer+k) + i + j)      
  //       }
  //       step(1)
  //     }
  //     poke(c.io.sDone(0), 1)
  //     poke(c.io.w_en, 0)
  //     step(1)
  //     poke(c.io.sEn(0), 0)
  //     poke(c.io.sDone(0), 0)
  //     step(1)

  //   }

  //   // println("Checking line")
  //   var rows_concat = List.fill(c.num_lines)(new StringBuilder)
  //   var gold_concat = List.fill(c.num_lines)(new StringBuilder)
  //   for (col <- 0 until c.line_size by c.col_rPar) {
  //     for (j <- 0 until c.col_rPar) {
  //       poke(c.io.col_addr(j), col + j)
  //     }
  //     for (row <- 0 until c.num_lines) {
  //       val init = if (iter*c.extra_rows_to_buffer - (c.num_lines - c.extra_rows_to_buffer - row) < 0) 0 else 1
  //       val scalar = iter*c.extra_rows_to_buffer - (c.num_lines - c.extra_rows_to_buffer - row)
  //       for (j <- 0 until c.col_rPar) {
  //         val r = peek(c.io.data_out(row*c.col_rPar + j))
  //         val g = init*(scalar*100 + col + j)
  //         expect(c.io.data_out(row*c.col_rPar+j), g)
  //         rows_concat(row) ++= r.toString
  //         rows_concat(row) ++= " "        
  //         gold_concat(row) ++= g.toString
  //         gold_concat(row) ++= " "        
  //       }
  //     }
  //     step(1)
  //   }
  //   // println("Saw:")
  //   for (row <- 0 until c.num_lines) {
  //     // println(rows_concat(row) + " ")
  //   }
  //   // println("Expected:")
  //   for (row <- 0 until c.num_lines) {
  //     // println(gold_concat(row) + " ")
  //   }

  // }

  
}
