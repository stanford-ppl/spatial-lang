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
  type V = Seq[(String, Expr)]
}

  // Mapping Mem[T] -> Mem[Struct(Seq(fieldName, T))]
object composed extends MOneToOneMap with MetadataMaps {
  type K = Expr
  type V = Expr 
}

object mappingOf extends MBiOneToManyMap with MetadataMaps {
  type K = Expr
  type V = PIR

  def apply(v:V) = imap(v)
  def to[T](k:K):mutable.Set[T] = map(k).map{ _.asInstanceOf[T] }
  def getOrElseUpdate[T<:V](k:K)(v: => VV):mutable.Set[T] = {
    super.getOrElseUpdate(k)(v).map{_.asInstanceOf[T]}
  }
  def getT[T](k:K)(implicit ev:ClassTag[T]) = get(k).map { _.collect {case x:T => x} }
  def get(v:V) = imap.get(v)
}

object readerCUsOf extends MOneToOneMap with MetadataMaps {
  type K = Expr
  type V = List[CU]
}

object innerDimOf extends MOneToOneMap with MetadataMaps {
  type K = Expr // SRAM
  type V = Int
}

object outerDimsOf extends MOneToOneMap with MetadataMaps {
  type K = Expr // SRAM
  type V = Seq[Int]
}

// K: sram Exp
// V: List of number of outer banks per duplicate
object numOuterBanksOf extends MOneToOneMap with MetadataMaps {
  type K = Expr
  type V = Seq[Int]
}

object bankOf extends MOneToOneMap with MetadataMaps {
  type K = CUMemory
  type V = Int
}

object instOf extends MOneToOneMap with MetadataMaps {
  type K = CUMemory
  type V = Int
}

// Static analysis of which bank an access belongs to
object staticBanksOf extends MOneToOneMap with MetadataMaps {
  type K = Expr 
  type V = Seq[Int]
}

/*
 * producerOf
 * 1. sram: (writeAddrFIFO, producer)
 * 2. localMem: (writeBus, producer)
 * */
object producerOf extends MOneToManyMap with MetadataMaps {
  type K = CUMemory
  type V = (Any, CU) // (writer, producer)
  override def apply(k:K):VV = map.getOrElse(k, mutable.Set[V]())
}

/*
 * consumerOf
 * 1. sram: (readAddrFIFO, consumer)
 * 2. localMem: (currentCU, consumer)
 * */
object consumerOf extends MOneToManyMap with MetadataMaps {
  type K = CUMemory
  type V = (Any, CU) // (reader, consumer)
  override def apply(k:K):VV = map.getOrElse(k, mutable.Set[V]())
}
