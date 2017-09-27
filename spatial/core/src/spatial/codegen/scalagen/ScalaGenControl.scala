package spatial.codegen.scalagen

import argon.codegen.scalagen.ScalaCodegen
import argon.core._
import spatial.aliases._

trait ScalaGenControl extends ScalaCodegen {
  def localMems: List[Exp[_]]

  protected def emitControlDone(ctrl: Exp[_]): Unit = { }

  protected def emitControlIncrement(ctrl: Exp[_], iter: Seq[Bound[Index]]): Unit = { }
}
