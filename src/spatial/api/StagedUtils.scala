package spatial.api

import argon.core.Staging
import argon.ops.ArrayApi
import forge._
import spatial.SpatialApi

trait StagedUtilApi extends StagedUtilExp with ArrayApi {
  this: SpatialApi =>

  @api def printArray[T: Meta](array: Array[T], header: String = "")(implicit ctx: SrcCtx): Void = {
    println(header)
    (0 until array.length) foreach { i => print(array(i).toText + " ") }
    println("")
  }

  @api def printMatrix[T: Meta](matrix: Matrix[T], header: String = "")(implicit ctx: SrcCtx): Void = {
    println(header)
    (0 until matrix.rows) foreach { i =>
      (0 until matrix.cols) foreach { j =>
        print(matrix(i, j).toText + "\t")
      }
      println("")
    }
  }

  implicit def insert_void[T: Type](x: T)(implicit ctx: SrcCtx): Void = unit2void(())
}

trait StagedUtilExp extends Staging
