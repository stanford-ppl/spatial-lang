package spatial.codegen.dotgen

import argon.codegen.dotgen._
import argon.core._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

trait DotGenReg extends DotCodegen {

  override def attr(n:Exp[_]) = n match {
    case n if isArgIn(n) | isArgOut(n) => super.attr(n).shape(box).style(filled).color(indianred)
    case n if isReg(n) => super.attr(n).shape(box).style(filled).color(limegreen)
    case n => super.attr(n)
  }

  def emitMemRead(reader:Sym[_]) = {
    val LocalReader(reads) = reader
    reads.foreach { case (mem, ind, en) =>
      readersOf(mem).foreach { case read =>
        if (read.node==reader) {
          emitEdge(mem, read.ctrlNode, DotAttr().label(s"${quote(reader)}"))
        }
      }
    }
  }

  def emitRetime(data:Exp[_], sr: Sym[_]) = {
    emitEdge(data, sr)
  }
  def emitRetimeRead(reader:Sym[_], sr: Exp[_]) = {
    emitEdge(sr, reader)
  }
  def emitRetimeWrite(writer:Sym[_], sr: Exp[_]) = {
    emitEdge(writer, sr)
  }

  def emitMemWrite(writer:Sym[_]) = {
    val LocalWriter(writes) = writer
    writes.foreach { case (mem, value, _, _) =>
      writersOf(mem).foreach { case write =>
        if (write.node==writer) {
          emitEdge(write.ctrlNode, mem, DotAttr().label(s"${quote(writer)}"))
        }
      }
    }
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case ArgInNew(init)  => emitVert(lhs, forceful=true)
    case ArgOutNew(init) => emitVert(lhs, forceful=true)
    case RegNew(init)    => emitVert(lhs)
    case RegRead(reg)    => if (config.dotDetail == 0) {emitMemRead(lhs)} else {
                  emitVert(lhs)
                  emitEdge(reg, lhs)
                }
    case RegWrite(reg,v,en) => if (config.dotDetail == 0) {emitMemWrite(lhs)} else {
                  emitEdge(v, reg)
                  emitEn(en, reg)
                }
    case _ => super.emitNode(lhs, rhs)
  }

  override protected def emitFileFooter() {
    super.emitFileFooter()
  }
}
