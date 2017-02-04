package spatial.api

import spatial.SpatialApi
import org.virtualized.virtualize

trait StagedUtilOps
trait StagedUtilApi extends StagedUtilOps { this: SpatialApi =>

  @virtualize
  def printArr[T:Staged](a: Array[T], str: String = "")(implicit ctx: SrcCtx): Void = {
    println(str)
    (0 until a.length) foreach { i => print( textify(a(i)) + " ") } // Have to use textify here...
    println("")
  }

}
trait StagedUtilExp extends StagedUtilOps
