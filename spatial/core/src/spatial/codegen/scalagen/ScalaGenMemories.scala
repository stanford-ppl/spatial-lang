package spatial.codegen.scalagen

import org.virtualized.SourceContext

trait ScalaGenMemories extends ScalaGenBits {
  import IR._

  var enableMemGen: Boolean = false

  private def oob(tp: Type[_], mem: Exp[_], lhs: Exp[_], inds: Seq[Exp[_]], pre: String, post: String, isRead: Boolean)(lines: => Unit) = {
    val name = u"$mem"
    val addr = if (inds.isEmpty && pre == "" && post == "") "err.getMessage"
    else "\"" + pre + "\" + " + "s\"\"\"${" + inds.map(quote).map(_ + ".toString").mkString(" + \", \" + ") + "}\"\"\" + \"" + post + "\""

    val op = if (isRead) "read" else "write"

    open(src"try {")
    lines
    close("}")
    open(src"catch {case err: java.lang.ArrayIndexOutOfBoundsException => ")
    emit(s"""System.out.println("[warn] ${lhs.ctx} Memory $name: Out of bounds $op at address " + $addr)""")
    if (isRead) emit(src"${invalid(tp)}")
    close("}")
  }

  def oobApply(tp: Type[_], mem: Exp[_], lhs: Exp[_], inds: Seq[Exp[_]], pre: String = "", post: String = "")(lines: => Unit) = {
    oob(tp, mem, lhs, inds, pre, post, isRead = true)(lines)
  }

  def oobUpdate(tp: Type[_], mem: Exp[_], lhs: Exp[_], inds: Seq[Exp[_]], pre: String = "", post: String = "")(lines: => Unit) = {
    oob(tp, mem, lhs, inds, pre, post, isRead = false)(lines)
  }

}
