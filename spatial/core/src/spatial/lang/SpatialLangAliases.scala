package spatial.lang

import argon.core.ArgonCoreAliases
import argon.lang.ArgonLangAliases
import forge._

/** Internal, language type aliases (no cyclic aliases allowed, e.g. cannot have "type X = argon.lang.X") **/
trait SpatialLangAliases extends ArgonLangAliases with ArgonCoreAliases {
  type Bit = MBoolean
  type Bus = spatial.targets.Bus

  type BitVector = VectorN[Bit]
  @generate type VectorJJ$JJ$1to128[T] = Vec[_JJ,T]

  type MFile = File

  type Tile[T] = DRAMDenseTile[T]
  type SparseTile[T] = DRAMSparseTile[T]
}
