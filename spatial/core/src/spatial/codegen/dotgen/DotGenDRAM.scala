package spatial.codegen.dotgen

import argon.codegen.dotgen._
import argon.core._
import spatial.aliases._
import spatial.nodes._
import spatial.utils._

trait DotGenDRAM extends DotGenSRAM {

  override def attr(n: Exp[_]) = n match {
    case n if isDRAM(n) => super.attr(n).shape(box).style(filled).color(pink)
    case n if isFringe(n) => super.attr(n).shape(box).style(filled).color(lightgrey)
    case n => super.attr(n)
  }

  def emitFringe(lhs:Sym[_], rhs:Op[_]):Unit = {
    emitSubGraph(lhs, DotAttr().label(quote(lhs)).style(filled).color(lightgrey)){
      //emitVert(lhs)
      rhs.allInputs.filter(isDRAM(_)).foreach(emitVert(_, false))
      rhs.allInputs.filter(isStream(_)).foreach(emitVert(_, false))
    }
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@DRAMNew(dims,zero) => // emitVert(lhs)
    case GetDRAMAddress(dram) =>
    case FringeDenseLoad(dram,cmdStream,dataStream) => emitFringe(lhs, rhs)
    case FringeDenseStore(dram,cmdStream,dataStream,ackStream) => emitFringe(lhs, rhs)
    case FringeSparseLoad(dram,addrStream,dataStream) => emitFringe(lhs, rhs)
    case FringeSparseStore(dram,cmdStream,ackStream) => emitFringe(lhs, rhs)
    case _ => super.emitNode(lhs, rhs)
  }


  override protected def emitFileFooter() {
    super.emitFileFooter()
  }

}
