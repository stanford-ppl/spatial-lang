package spatial.codegen.simgen

import spatial.analysis.NodeClasses
import spatial.api.ControllerExp

trait SimGenController extends SimCodegen {
  val IR: ControllerExp with NodeClasses
  import IR._

  def getAncestors(node: Exp[_]): Seq[Exp[_]] = parentOf(node) match {
    case Some(ctrl) => ctrl +: getAncestors(ctrl)
    case None => Nil
  }
  def emitControlBody(node: Exp[_], blocks: Block[_]*) = {
    getAncestors(node).foreach{ancestor => emit(src"import $ancestor._")}
    if (isOuterControl(node)) {
      emit(src"""children = List(${childrenOf(node).map(quote).mkString(", ")})""")
    }

    blocks.foreach(emitBlock)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case Hwblock(func,isForever) if !isForever =>
      val stream = newStream(quote(lhs))
      val parent = if (isOuterControl(lhs)) "SequentialPipe" else "InnerPipe"
      withStream(stream) {
        emit(src"/** BEGIN HARDWARE BLOCK $lhs **/")
        open(src"""object $lhs extends $parent("$lhs") {""")

        emitControlBody(lhs, func)

        close("}")
        emit(src"/** END HARDWARE BLOCK $lhs **/")
      }
      stream.close()

    case UnitPipe(ens, func) =>
      val stream = newStream(quote(lhs))
      val parent = if (isOuterControl(lhs)) "SequentialPipe" else "InnerPipe"
      withStream(stream) {
        emit(src"/** BEGIN UNIT PIPE $lhs **/")
        open(src"""object $lhs extends $parent("$lhs") {""")

        emitControlBody(lhs, func)

        close("}")
        emit(src"/** END UNIT PIPE $lhs **/")
      }
      stream.close()

    case ParallelPipe(ens, func) =>
      val stream = newStream(quote(lhs))
      withStream(stream) {
        emit(src"/** BEGIN PARALLEL PIPE $lhs **/")
        //val en = if (ens.isEmpty) "true" else ens.map(quote).mkString(" && ")
        open(src"""object $lhs extends ParallelPipe("$lhs") {""")

        emitControlBody(lhs, func)

        close("}")
        emit(src"/** END PARALLEL PIPE $lhs **/")
      }
      stream.close()

    case _ => super.emitNode(lhs, rhs)
  }
}
