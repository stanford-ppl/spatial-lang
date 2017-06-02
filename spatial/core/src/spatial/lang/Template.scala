package spatial.lang

import forge._

abstract class Template[T:Type] extends MetaAny[T] {
  @api def ===(that: T): MBoolean = this.s == that.s
  @api def =!=(that: T): MBoolean = this.s != that.s
  @api def toText: MString = u"${s.tp.stagedClass}"
}
