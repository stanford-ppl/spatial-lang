package spatial.lang.static

import argon.core._
import forge._
import org.virtualized.virtualize

trait PrintingApi { this: SpatialApi =>
  /** Prints the given Array to the console, preceded by an optional heading. **/
  @virtualize
  @api def printArray[T:Type](array: MArray[T], heading: MString = opt[MString]): MUnit = {
    val header = heading.getOrElseCreate("")
    println(header)
    (0 until array.length) foreach { i => print(array(i).toString + " ") }
    println("")
  }

  /** Prints the given Matrix to the console, preceded by an optional heading. **/
  @virtualize
  @api def printMatrix[T:Type](matrix: Matrix[T], heading: MString = opt[MString]): MUnit = {
    val header = heading.getOrElseCreate("")
    println(header)
    (0 until matrix.rows) foreach { i =>
      (0 until matrix.cols) foreach { j =>
        print(matrix(i, j).toString + "\t")
      }
      println("")
    }
  }

  /** Prints the given Tensor3 to the console, preceded by an optional heading. **/
  @virtualize
  @api def printTensor3[T:Type](tensor: Tensor3[T], heading: MString = opt[MString]): MUnit = {
    val header = heading.getOrElseCreate("")
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

  /** Prints the given Tensor4 to the console, preceded by the an optional heading. **/
  @virtualize
  @api def printTensor4[T:Type](tensor: Tensor4[T], heading: MString = opt[MString]): MUnit = {
    val header = heading.getOrElseCreate("")
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

  /** Prints the given Tensor5 to the console, preceded by the an optional heading. **/
  @virtualize
  @api def printTensor5[T:Type](tensor: Tensor5[T], heading: MString = opt[MString]): MUnit = {
    val header = heading.getOrElseCreate("")
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
