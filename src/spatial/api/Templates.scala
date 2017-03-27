package spatial.api

import argon.core.Staging
import argon.ops.{BoolExp, TextExp}

trait TemplatesExp extends Staging with BoolExp with TextExp {

  abstract class Template[T:Meta] extends MetaAny[T] {
    def ===(that: T)(implicit ctx: SrcCtx): Bool = this.s == that.s
    def =!=(that: T)(implicit ctx: SrcCtx): Bool = this.s != that.s
    def toText(implicit ctx: SrcCtx): Text = u"${s.tp.stagedClass}"
  }

}
