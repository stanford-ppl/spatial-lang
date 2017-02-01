package spatial.api

import spatial.SpatialApi

trait StagedUtilOps
trait StagedUtilApi extends StagedUtilOps { this: SpatialApi =>

  def printArr[T:Staged](a: Array[T], str: String = "")(implicit ctx: SrcCtx): Void = {
    println(str)
    (0 until a.length) foreach { i => print(a(i) + " ") }
    println("")
  }

}
trait StagedUtilExp extends StagedUtilOps
