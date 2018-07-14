package spatial.codegen.pirgen

import argon.codegen.{Codegen, FileDependencies}
import argon.core._
import spatial.metadata._

import scala.collection.mutable

trait PIRFormattedCodegen extends Codegen with PIRTraversal with PIRLogger with PIRStruct {

  val controlStack = mutable.Stack[Exp[_]]()
  def currCtrl = controlStack.top
  def inHwBlock = controlStack.nonEmpty

  trait Lhs {
    val lhs:Exp[_]
  }
  case class LhsSym(dlhs:Exp[_], postFix:Option[String]=None) extends Lhs {
    val lhs = compose(dlhs)
    override def toString = postFix match {
      case Some(postFix) => s"${quote(dlhs)}_$postFix"
      case None => s"${quote(dlhs)}"
    }
  }
  implicit def sym_to_lhs(sym:Exp[_]) = LhsSym(sym)
  case class LhsMem(dmem:Exp[_], instId:Int, bankId:Option[Int]=None) extends Lhs {
    val lhs = compose(dmem)

    override def toString = bankId match {
      case Some(bankId) => s"${dmem}_d${instId}_b$bankId"
      case None => if (duplicatesOf(lhs).size==1) s"$dmem" else s"${dmem}_d${instId}"
    }
  }
  object LhsMem {
    def apply(dmem:Exp[_], instId:Int, bankId:Int):LhsMem = LhsMem(dmem, instId, Some(bankId))
  }

  def quoteCtrl = {
    if (controlStack.isEmpty) ".ctrl(top)"
    else s".ctrl($currCtrl)"
  }

  def quoteCtx(lhs:Lhs) = {
    lhs.lhs.ctx match {
      case virtualized.EmptyContext => ""
      case ctx => 
        s""".srcCtx("${ctx}${lhs.lhs.name.map {n => s":$n"}.getOrElse("")}")"""
    }
  }

  def emitMeta(lhs:LhsMem) = {
    val mem = compose(lhs.dmem)
    val insts = duplicatesOf(mem)
    val inst = insts(lhs.instId)
    emit(s"isAccum($lhs) = ${inst.isAccum}")
    emit(s"bufferDepthOf($lhs) = ${inst.depth}")
  }

  def emit(lhs:Lhs, rhsExp:Any, comment:Any):Unit = {
    val ctrl = controlStack.headOption.map { _.toString }.getOrElse(s"design.top.topController")
    emit(s"""val $lhs = withCtrl($ctrl) { $rhsExp.name("$lhs")${quoteCtx(lhs)} } // $comment""")

    lhs match {
      case lhs:LhsMem =>
        emitMeta(lhs)
      case lhs =>
    }
  }

  def alias(lhs:Lhs, rhsExp:Any, comment:Any):Unit = {
    val ctrl = controlStack.headOption.map { _.toString }.getOrElse(s"design.top.topController")
    emit(s"""val $lhs = withCtrl($ctrl) { $rhsExp } // $comment""")
  }

  def emitblk[T](header:String)(block: => T):T = {
    open(s"$header {")
    val res = block
    close("} ")
    res
  }
}
