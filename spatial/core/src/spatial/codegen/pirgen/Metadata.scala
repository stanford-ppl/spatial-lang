package spatial.codegen.pirgen

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

object pcusOf extends MOneToManyMap with MetadataMaps {
  type K = Expr
  type V = PCU
}

object cusOf extends MOneToManyMap with MetadataMaps {
  type K = Expr
  type V = CU
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
