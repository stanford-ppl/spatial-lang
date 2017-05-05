package spatial.api

import spatial.SpatialExp
import forge._

trait Aliases { this: SpatialExp =>
  @generate
  type UIntJJ$JJ$2to128 = FixPt[FALSE,_JJ,_0]
  @generate
  type IntJJ$JJ$2to128 = FixPt[TRUE,_JJ,_0]

  type Bit = Bool
}
