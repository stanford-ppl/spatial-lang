package spatial.codegen.simgen

import argon.ops.HashMapExp
import spatial.SpatialExp

trait SimGenHashMap extends SimCodegen {
  val IR: SpatialExp
  import IR._

  override protected def remap(tp: Type[_]): String = tp match {
    case HashIndexType(mK) => src"scala.collection.mutable.HashMap[$mK,${typ[Index]}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]) = rhs match {
    case HashIndexApply(index, key) => emit(src"val $lhs = $index.getOrElse($key, -1)")
    case _ => super.emitNode(lhs, rhs)
  }

  override protected def emitFat(lhs: Seq[Sym[_]], rhs: Def) = rhs match {
    case e @ BuildHashMap(in, apply, keyFunc, valFunc, reduce, rV, i) =>
      open(src"val (${lhs(0)},${lhs(1)},${lhs(2)}) = {")
        emit(src"val index  = new ${HashIndexType(e.mK)}()")
        emit(src"val keys   = new scala.collection.mutable.ArrayBuffer[${e.mK}]()")
        emit(src"val values = new scala.collection.mutable.ArrayBuffer[${e.mV}]()")

        open(src"$in.indices.foreach{$i => ")
          visitBlock(apply)
          visitBlock(keyFunc)
          visitBlock(valFunc)
          open(src"if (index.contains(${keyFunc.result})) {")
            emit(src"val idx = index.apply(${keyFunc.result})")
            emit(src"val ${rV._1} = ${valFunc.result}")
            emit(src"val ${rV._2} = values.apply(idx)")
            visitBlock(reduce)
            emit(src"values.update(idx, ${reduce.result})")
          close("}")
          open("else {")
            emit(src"index += ${keyFunc.result} -> values.size")
            emit(src"keys += ${keyFunc.result}")
            emit(src"values += ${valFunc.result}")
          close("}")
        close("}")
        emit(src"(keys.toArray, values.toArray, index)")
      close("}")

    case _ => super.emitFat(lhs, rhs)
  }

}
