package spatial.api

import argon.core.Staging
import argon.ops.ArrayExtApi
import spatial.SpatialExp
import forge._

// TODO: Is this still used by anything? If not, delete
trait FileIOApi extends FileIOExp with ArrayExtApi {
  this: SpatialExp =>

  @api def loadCSV[T:Meta:Bits](filename: java.lang.String, len: Index): Array[T] = {
    val array = Array.empty[T](wrap(len.s))
    // load_csv(filename, array.s)
    array
  }

}

trait FileIOExp extends Staging with DRAMExp with RegExp {
  this: SpatialExp =>

  // // /** IR Nodes **/
  // case class LoadCSV[T:Type:Bits](filename: String, array: Exp[MetaArray[T]]) extends Op[Void] {
  //   def mirror(f:Tx) = load_csv(filename,f(array))
  //   override def aliases = Nil
  // }

  // // implicit def loadCSV[T:Meta:Bits] = LoadCSV[T]

  // // /** Constructors **/
  // def load_csv[T:Type:Bits](filename: String,  array: Exp[MetaArray[T]])(implicit ctx: SrcCtx): Exp[Void] = {
  //   stageWrite(array)(LoadCSV(filename, array))(ctx)
  // }

  // implicit def LoadCSV[T:Meta:Bits]: Meta[LoadCSV[T]] = LoadCSV(typ[T])


}

