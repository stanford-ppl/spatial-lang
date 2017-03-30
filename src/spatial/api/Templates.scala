package spatial.api

import argon.core.Staging
import argon.ops.{BoolExp, TextExp}
import forge._

trait TemplatesExp extends Staging with BoolExp with TextExp {

  abstract class Template[T:Meta] extends MetaAny[T] {
    @api def ===(that: T): Bool = this.s == that.s
    @api def =!=(that: T): Bool = this.s != that.s
    @api def toText: Text = u"${s.tp.stagedClass}"
  }

}
