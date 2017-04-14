package spatial.api

import argon.core.Staging
import forge._
import spatial.SpatialExp

trait TemplatesExp  {
  this: SpatialExp =>

  abstract class Template[T:Meta] extends MetaAny[T] {
    @api def ===(that: T): Bool = this.s == that.s
    @api def =!=(that: T): Bool = this.s != that.s
    @api def toText: Text = u"${s.tp.stagedClass}"
  }

}
