package spatial.lang

import forge._
import org.virtualized._
import spatial.SpatialApi

trait StagedUtils { this: SpatialApi =>
  @virtualize
  @api def printArray[T:Type](array: Array[T], header: String = ""): MUnit = {
    println(header)
    (0 until array.length) foreach { i => print(array(i).toString + " ") }
    println("")
  }

  @virtualize
  @api def printMatrix[T: Type](matrix: Matrix[T], header: String = ""): MUnit = {
    println(header)
    (0 until matrix.rows) foreach { i =>
      (0 until matrix.cols) foreach { j =>
        print(matrix(i, j).toString + "\t")
      }
      println("")
    }
  }

  @virtualize
  @api def printTensor3[T: Type](tensor: Tensor3[T], header: String = ""): MUnit = {
    println(header)
    (0 until tensor.dim0) foreach { i =>
      (0 until tensor.dim1) foreach { j =>
        (0 until tensor.dim2) foreach { k => 
          print(tensor(i, j, k).toString + "\t")
        }
        println("")
      }
      (0 until tensor.dim2) foreach {_ => print("--\t")}
      println("")
    }
  }

  @virtualize
  @api def printTensor4[T: Type](tensor: Tensor4[T], header: String = ""): MUnit = {
    println(header)
    (0 until tensor.dim0) foreach { i =>
      (0 until tensor.dim1) foreach { j =>
        (0 until tensor.dim2) foreach { k => 
          (0 until tensor.dim3) foreach { l => 
            print(tensor(i, j, k, l).toString + "\t")
          }
          println("")
        }
        (0 until tensor.dim3) foreach {_ => print("--\t")}
        println("")
      }
      (0 until tensor.dim3) foreach {_ => print("--\t")}
      println("")
    }
  }

  @virtualize
  @api def printTensor5[T: Type](tensor: Tensor5[T], header: String = ""): MUnit = {
    println(header)
    (0 until tensor.dim0) foreach { i =>
      (0 until tensor.dim1) foreach { j =>
        (0 until tensor.dim2) foreach { k => 
          (0 until tensor.dim3) foreach { l => 
            (0 until tensor.dim4) foreach { m => 
              print(tensor(i, j, k, l, m).toString + "\t")
            }
            println("")
          }
          (0 until tensor.dim4) foreach {_ => print("--\t")}
          println("")
        }
        (0 until tensor.dim4) foreach {_ => print("--\t")}
        println("")
      }
      (0 until tensor.dim4) foreach {_ => print("--\t")}
      println("")
    }
  }
}
