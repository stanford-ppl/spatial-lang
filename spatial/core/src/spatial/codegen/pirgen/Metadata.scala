package spatial.codegen.pirgen

import scala.collection.mutable
import scala.util.{Try, Success, Failure}

trait MetadataMaps extends MMap { 
  metadatas += this
  def info(n:K):String = { s"${name}($n)=${get(n)}" }
  def reset = map.clear
}

  // Mapping Mem[Struct(Seq(fieldName, T))] -> Seq((fieldName, Mem[T]))
object decomposed extends MOneToOneMap with MetadataMaps {
  type K = Expr
  type V = Seq[(String, Expr)]
}

  // Mapping Mem[T] -> Mem[Struct(Seq(fieldName, T))]
object composed extends MOneToOneMap with MetadataMaps {
  type K = Expr
  type V = Expr 
}

object mappingOf extends MBiOneToManyMap with MetadataMaps {
  type K = Expr
  type V = CU

  def apply(v:V) = imap(v)
}

object innerDimOf extends MOneToOneMap with MetadataMaps {
  type K = Expr
  type V = Int
}

object bankOf extends MOneToOneMap with MetadataMaps {
  type K = CUMemory
  type V = Int
}

object instOf extends MOneToOneMap with MetadataMaps {
  type K = CUMemory
  type V = Int
}

object writeControllerOf extends MOneToManyMap with MetadataMaps {
  type K = CUMemory
  type V = (CU, Option[CU])
  override def apply(k:K):VV = map.getOrElse(k, mutable.Set[V]())
}

object readControllerOf extends MOneToManyMap with MetadataMaps {
  type K = CUMemory
  type V = (CU, Option[CU])
  override def apply(k:K):VV = map.getOrElse(k, mutable.Set[V]())
}
