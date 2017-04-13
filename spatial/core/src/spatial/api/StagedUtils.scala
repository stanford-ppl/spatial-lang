package spatial.api

import argon.core.Staging
import argon.ops.ArrayApi
import spatial._
import org.virtualized._
import forge._

trait StagedUtilApi extends StagedUtilExp {
  this: SpatialApi =>

  @virtualize
  @api def printArray[T:Meta](array: Array[T], header: String = ""): Void = {
    println(header)
    (0 until array.length) foreach { i => print(array(i).toString + " ") }
    println("")
  }

  @virtualize
  @api def printMatrix[T: Meta](matrix: Matrix[T], header: String = ""): Void = {
    println(header)
    (0 until matrix.rows) foreach { i =>
      (0 until matrix.cols) foreach { j =>
        print(matrix(i, j).toString + "\t")
      }
      println("")
    }
  }

}

trait StagedUtilExp extends Staging {
  this: SpatialExp =>


}
