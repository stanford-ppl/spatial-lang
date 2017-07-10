package spatial.lang.cake

import argon.lang.cake._
import forge._
import spatial.lang.static._

trait SpatialExternal extends ArgonLangExternal with SpatialApi with SpatialExternalAliases {
  // HACK: Insert MUnit where required to make programs not have to include () at the end of ... => MUnit functions
  @api implicit def insert_unit[T:Type](x: T): MUnit = MUnit()
}

trait SpatialInternal extends ArgonLangInternal with SpatialExp with SpatialInternalAliases
