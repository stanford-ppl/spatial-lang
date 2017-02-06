package spatial.api

import argon.ops.VoidExp
import spatial.SpatialApi
import org.virtualized.virtualize

trait StagedUtilOps
trait StagedUtilApi extends StagedUtilOps { this: SpatialApi =>

  @virtualize
  def printArray[T:Staged](array: Array[T], header: String = "")(implicit ctx: SrcCtx): Void = {
    println(header)
    (0 until array.length) foreach { i => print( textify(array(i)) + " ") } // Have to use textify here...
    println("")
  }

  implicit def insert_void[T](x: T)(implicit ctx: SrcCtx): Void = ()

}
trait StagedUtilExp extends StagedUtilOps with VoidExp
