package spatial.codegen.scalagen

import argon.core._
import spatial.aliases._

trait ScalaGenMemories extends ScalaGenBits {
  var globalMems: Boolean = false

  def emitMem(lhs: Exp[_], x: String) = if (globalMems) emit(s"if ($lhs == null) $x") else emit("val " + x)

  def flattenAddress(dims: Seq[Exp[Index]], indices: Seq[Exp[Index]], ofs: Option[Exp[Index]]): String = {
    val strides = List.tabulate(dims.length){i => (dims.drop(i+1).map(quote) :+ "1").mkString("*") }
    indices.zip(strides).map{case (i,s) => src"$i*$s" }.mkString(" + ") + ofs.map{o => src" + $o"}.getOrElse("")
  }

  def flattenAddress(dims: Seq[Exp[Index]], indices: Seq[Exp[Index]]): String = {
    val strides = List.tabulate(dims.length){i => (dims.drop(i+1).map(quote) :+ "1").mkString("*") }
    indices.zip(strides).map{case (i,s) => src"$i*$s"}.mkString(" + ")
  }

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
