package spatial.codegen.pirgen

import scala.collection.mutable
import scala.util.{Try, Success, Failure}
import scala.reflect.ClassTag

trait MetadataMaps extends MMap { 
  metadatas += this
  def info(n:K):String = { s"${name}($n)=${get(n)}" }
  def reset = map.clear
}

  // Mapping Mem[Struct(Seq(fieldName, T))] -> Seq((fieldName, Mem[T]))
object decomposed extends MOneToOneMap with MetadataMaps {
  type K = Expr
  type V = Either[Expr, Seq[(String, Expr)]]
}

  // Mapping Mem[T] -> Mem[Struct(Seq(fieldName, T))]
object composed extends MOneToOneMap with MetadataMaps {
  type K = Expr
  type V = Expr 
}

object innerDimOf extends MOneToOneMap with MetadataMaps {
  type K = (Expr, Int) // (SRAM, dispatch ID)
  type V = (Int, mutable.Set[Expr]) // (dim, ctrls)
}

object outerDimsOf extends MOneToOneMap with MetadataMaps {
  type K = (Expr, Int) // (SRAM, dispatch ID)
  type V = Seq[Int]
}

object numOuterBanksOf extends MOneToOneMap with MetadataMaps {
  type K = (Expr, Int) // (SRAM, dispatch ID)
  type V = Int
}

// Static analysis of which bank an access belongs to
object staticBanksOf extends MOneToOneMap with MetadataMaps {
  type K = (Expr, Int) // (access, instId)
  type V = Seq[Int] // List of banks 
}

object isInnerCounter extends MOneToOneMap with MetadataMaps {
  type K = Expr 
  type V = Boolean
}

