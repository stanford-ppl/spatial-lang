package spatial.lang.static

import argon.core._
import forge._

trait LowPriorityVectorImplicits { this: SpatialApi =>
  @api implicit def vectorNFakeType[T:Type:Bits]: Type[VectorN[T]] = {
    error(ctx, u"VectorN value cannot be used directly as a staged type")
    error("Add a type conversion here using .asVector#, where # is the length of the vector")
    error(ctx)
    VectorN.typeFromLen[T](-1)
  }
}

trait VectorApi extends LowPriorityVectorImplicits { this: SpatialApi => }

