package spatial.codegen.pirgen

import argon.codegen.{Codegen, FileDependencies}
import argon.core._
import spatial.metadata._

import scala.collection.mutable

trait Lhs {
  val sym:Exp[_]
  val dsym:Exp[_]
}
trait PIRFormattedCodegen extends PIRFileGen with PIRTraversal with PIRLogger {

  val rhsMap = mutable.Map[Lhs, Rhs]()
  def lookup(lhs:Lhs):DefRhs = rhsMap(lhs) match {
    case AliasRhs(lhs, alias) => lookup(alias)
    case rhs:DefRhs => rhs
  }

  override protected def preprocess[S:Type](block: Block[S]): Block[S] = {
    rhsMap.clear
    super.preprocess(block)
  }

  override protected def emitFileHeader() {
    super.emitFileHeader()
    emit(s"def withCtx[T<:IR](x:T, ctx:String):T = { srcCtxOf(x) = ctx; x}")
    emitNameFunc
  }

  def emitNameFunc = {
    emit(s"def withName[T<:IR](x:T, name:String):T = { if (!nameOf.contains(x)) nameOf(x) = name; x}")
  }

  case class LhsSym(dsym:Exp[_], postFix:Option[String]=None) extends Lhs {
    val sym = compose(dsym)
  }
  case class LhsMem(dsym:Exp[_], instId:Int, bankId:Option[Int]=None) extends Lhs {
    val sym = compose(dsym)
  }
  object LhsMem {
    def apply(dsym:Exp[_], instId:Int, bankId:Int):LhsMem = LhsMem(dsym, instId, Some(bankId))
  }
  implicit def sym_to_lhs(sym:Exp[_]) = {
    if (isMem(sym) || isMem(compose(sym))) LhsMem(sym, 0) else LhsSym(sym)
  }

  trait Rhs {
    val lhs:Lhs
    assert(!rhsMap.contains(lhs), s"Already contains $lhs -> ${rhsMap(lhs)} but remap to $this")
    rhsMap += lhs -> this
    var _comment:Option[String] = None
    def comment(cm:String):this.type = { _comment = Some(cm); this}
    def comment:String = {
      val cm = lhs.sym match {
        case Def(op) => op.toString
        case _ => ""
      }
      cm + _comment.map { cm => s" $cm"}.getOrElse("")
    }
  }
  case class DefRhs(lhs:Lhs, tp:String, args:Any*) extends Rhs
  case class AliasRhs(lhs:Lhs, alias:Lhs) extends Rhs

  override protected def quoteOrRemap(arg: Any): String = arg match {
    case x:Iterable[_] => x.map(quoteOrRemap).toList.toString
    case Some(x) => s"Some(${quoteOrRemap(x)})"
    case None => s"None"
    case e: Exp[_] => quoteOrRemap(sym_to_lhs(e))
    case m: Type[_] => remap(m)
    case x@LhsSym(dsym,Some(postFix)) => s"${quote(dsym)}_$postFix" 
    case x@LhsSym(dsym,None) => quote(dsym)
    case x@LhsMem(dmem, instId, Some(bankId)) => s"${quote(dmem)}_d${instId}_b$bankId"
    case x@LhsMem(dmem, instId, None) if (duplicatesOf(x.sym).size <= 1) => quote(dmem)
    case x@LhsMem(dmem, instId, None) => s"${quote(dmem)}_d${instId}"
    case DefRhs(lhs, tp, args@ _*) =>
      //TODO: why is args of type Any
      val argsString = args.map {
        case (k,v) => s"$k=${quoteRef(v)}"
        case v => quoteRef(v)
      }.mkString(",")
      var q = s"${tp}($argsString)"
      lhs.sym.ctx match {
        case virtualized.EmptyContext => 
        case ctx => 
          q = src"""withCtx($q, "${ctx}${lhs.sym.name.map {n => s":$n"}.getOrElse("")}")"""
      }
      q = src"""withName($q, "${lhs}")"""
      q
    case x@AliasRhs(lhs, alias) => 
      var q = quoteRef(alias)
      q = src"""withName($q, "$lhs")"""
      q
    case x => x.toString
    //case x:SrcCtx => x.toString
    //case x => super.quoteOrRemap(x)
  }

  protected def quoteRef(x:Any):String = quoteOrRemap(x)

  def emit(rhs:Rhs):Unit = {
    emit(src"val ${rhs.lhs} = $rhs // ${rhs.comment}")
  }

  def emitblk[T](header:String)(block: => T):T = {
    open(s"$header {")
    val res = block
    close("} ")
    res
  }
}
