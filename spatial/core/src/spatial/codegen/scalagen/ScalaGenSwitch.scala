package spatial.codegen.scalagen

import argon.core._
import spatial.aliases._
import spatial.nodes._

trait ScalaGenSwitch extends ScalaGenBits with ScalaGenMemories with ScalaGenSRAM with ScalaGenController {

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@Switch(_,selects,cases) =>
      val isBits = Bits.unapply(op.mT).isDefined
      emit(src"/** BEGIN SWITCH $lhs **/")
      open(src"val $lhs = {")
        selects.indices.foreach { i =>
          open(src"""${if (i == 0) "if" else "else if"} (${selects(i)}) {""")
            val Def(SwitchCase(body)) = cases(i)
            open(src"val ${cases(i)} = {")
              emitBlock(body)
            close("}")
            emit(src"${cases(i)}")
          close("}")
        }
        if (isBits) emit(src"else { ${invalid(op.mT)} }") else emit(src"()")
        emitControlDone(lhs)
      close("}")
      emit(src"/** END SWITCH $lhs **/")

    case SwitchCase(body) => // Controlled by Switch

    case _ => super.emitNode(lhs, rhs)
  }
}
