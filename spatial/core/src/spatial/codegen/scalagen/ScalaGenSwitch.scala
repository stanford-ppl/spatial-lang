package spatial.codegen.scalagen

import spatial.SpatialExp

trait ScalaGenSwitch extends ScalaGenBits {
  val IR: SpatialExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@Switch(body,selects,cases) =>
      visitBlock(body)
      if (Bits.unapply(op.mT).isDefined) {
        open(src"val $lhs = {")
          selects.indices.foreach { i =>
            emit(src"""${if (i == 0) "if" else "else if"} (${selects(i)}) { ${cases(i)} }""")
          }
          emit(src"else { ${invalid(op.mT)} }")
        close("}")
      }
      else {
        emit(src"val $lhs = ()")
      }

    case SwitchCase(body) =>
      open(src"val $lhs = {")
        emitBlock(body)
      close("}")

    case _ => super.emitNode(lhs, rhs)
  }
}
