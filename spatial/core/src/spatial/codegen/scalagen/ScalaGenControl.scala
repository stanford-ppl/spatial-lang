package spatial.codegen.scalagen

import argon.codegen.scalagen.ScalaCodegen
import argon.core._

trait ScalaGenControl extends ScalaCodegen {
  def localMems: List[Exp[_]]

  protected def emitControlDone(ctrl: Exp[_]): Unit = { }

}
