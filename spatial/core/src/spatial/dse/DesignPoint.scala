package spatial.dse

import spatial.metadata.Domain
import forge.stateful

abstract class DesignPoint {
  @stateful def set(indexedSpace: Seq[(Domain[_], Int)], prods: Seq[BigInt], dims: Seq[BigInt]): Unit
}


case class PointIndex(pt: BigInt) extends DesignPoint {
  @stateful def set(indexedSpace: Seq[(Domain[_], Int)], prods: Seq[BigInt], dims: Seq[BigInt]): Unit = {
    indexedSpace.foreach{case (domain,d) => domain.set( ((pt / prods(d)) % dims(d)).toInt ) }
  }
}

case class Point(params: Seq[AnyVal]) extends DesignPoint {
  @stateful def set(indexedSpace: Seq[(Domain[_], Int)], prods: Seq[BigInt], dims: Seq[BigInt]): Unit = {
    indexedSpace.foreach{case (domain,d) => domain.setValueUnsafe(params(d)) }
  }
}