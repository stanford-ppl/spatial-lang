package spatial.codegen.pirgen

import argon.core._
import argon.nodes._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import virtualized.SourceContext
import scala.collection.mutable.WrappedArray
import scala.reflect.runtime.universe.{Block => _, Type => _, _}

import scala.collection.mutable

class PIRStructAnalyzer(implicit val codegen:PIRCodegen) extends PIRTraversal {
  override val name = "PIR Struct Analyzer"
  var IR = codegen.IR

  override def preprocess[S:Type](b: Block[S]): Block[S] = {
    super.preprocess(b)
  }

  override def postprocess[S:Type](b: Block[S]): Block[S] = {
    super.postprocess(b)
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]) = {
    decomposeWithFields(lhs)
    super.visit(lhs, rhs)
  }

  def decomposeWithFields[T](exp:Exp[_])(implicit ev:TypeTag[T]):Either[Exp[_], Seq[(String, Exp[_])]] = decomposed.getOrElseUpdate(exp) {
    val struct = exp match {
      case Def(StreamInNew(bus)) => createStruct(exp, getFields(bus)) 
      case Def(StreamOutNew(bus)) => createStruct(exp, getFields(bus))
      case Def(SimpleStruct(elems)) => createStruct(exp, elems)
      case Def(VectorApply(vec, idx)) => createStruct(exp, getFields(vec))
      case Def(ListVector(elems)) => createStruct(exp, elems.flatMap(ele => getFields(ele)))
      case Def(GetDRAMAddress(dram)) => Left(exp) //TODO: consider the case where dram is composed
      case Def(RegNew(init)) => 
        decomposeWithFields(init)
        createStruct(exp, getFields(init))
      case Const(a:WrappedArray[_]) => createStruct(exp, a.toSeq) 
      case mem if isMem(mem) => 
        val fields =  mem.tp.typeArguments(0) match {
          case s:StructType[_] => s.fields.map(_._1)
          case _ => Seq()
        }
        createStruct(mem, fields)
      case ParLocalReader((mem,_,_)::_) => 
        createStruct(exp, getFields(mem))
      case ParLocalWriter((mem,_,_,_)::_) =>
        createStruct(exp, getFields(mem))
      case _ => Left(exp)
    }

    dbgs(s"Decomposing ${qdef(exp)} = $struct")
    struct match {
      case Left(exp) => 
      case Right(seq) => seq.foreach { case (field, dexp) =>
        // Special case where if dexp is constant, it can map to 
        // multiple exp but doesn't matter is the mapping is incorrect
        if (!isConstant(dexp) || !composed.contains(dexp)) {
          composed(dexp) = exp
        }
      }
    }

    struct
  }

  def createStruct[T](exp: Exp[_], fields: Seq[T]): Either[Exp[_], Seq[(String, Exp[_])]] = {
    if (fields.size < 1) {
      Left(exp)
    } else {
      val seq = fields.map {
        case field:String if fields.size== 1 => (field, exp) 
        case field:String => (field, fresh[Int32]) 
        case (field:String, dexp:Exp[_]) => (field, dexp)
      }
      Right(seq)
    }
  }


  def getFields(bus:Bus):Seq[String] = bus match {
    //case BurstCmdBus => Seq("offset", "size", "isLoad")
    case BurstCmdBus => Seq("offset", "size") // throw away isLoad bit
    case BurstAckBus => Seq("ack") 
    case bus:BurstDataBus[_] => Seq("data") 
    //case bus:BurstFullDataBus[_] => Seq("data", "valid") // throw away valid bit
    case bus:BurstFullDataBus[_] => Seq("data")
    case GatherAddrBus => Seq("addr")
    case bus:GatherDataBus[_] => Seq("data")
    //case bus:ScatterCmdBus[_] => Seq("data", "valid") // throw away valid bit
    case bus:ScatterCmdBus[_] => Seq("data")
    case ScatterAckBus => Seq("ack") 
    case _ => throw new Exception(s"Don't know how to decompose bus ${bus}")
  }

}

trait PIRStruct {
  // Struct handling
  def compose(dexp:Exp[_]) = composed.get(dexp).getOrElse(dexp)

  def decompose(exp: Exp[_]): Seq[Exp[_]] = decomposed.get(exp).getOrElse(Left(exp)) match {
    case Left(exp) => Seq(exp)
    case Right(seq) => seq.map(_._2)
  }

  def getFields(exp: Exp[_]): Seq[String] = {
    decomposed(exp) match {
      case Left(e) => Seq()
      case Right(seq) => seq.map(_._1)
    }
  }

  def getField(dexp: Exp[_]): Option[String] = {
    decomposed(compose(dexp)) match {
      case Left(e) => None 
      case Right(seq) => Some(seq.filter(_._2==dexp).headOption.map(_._1).getOrElse(
        throw new Exception(s"composed $dexp=${compose(dexp)}doesn't contain $dexp. seq=$seq")
        ))
    }
  }

  def lookupField(exp:Exp[_], fieldName:String):Option[Exp[_]] = {
    decomposed(exp) match {
      case Left(exp) => None
      case Right(fs) => 
        val matches = fs.filter(_._1==fieldName)
        assert(matches.size<=1, s"$exp has struct type with duplicated field name: [${fs.mkString(",")}]")
        if (matches.nonEmpty) Some(matches.head._2) else None
    }
  }

}
