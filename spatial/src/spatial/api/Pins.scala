package spatial.api

import spatial.SpatialExp
import spatial.targets._

trait PinApi extends PinExp {
  this: SpatialExp =>
}

trait PinExp {
  this: SpatialExp =>

  def target: FPGATarget // Needs to be filled in by application, defaults to Default

  type Pin = spatial.targets.Pin
  type Bus = spatial.targets.Bus
}

