package spatial.codegen.pirgen

import argon.core._
import argon.nodes._
import spatial.analysis.SpatialTraversal
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

import scala.collection.mutable
import scala.collection.mutable.WrappedArray
import scala.reflect.runtime.universe.{Block => _, Type => _, _}

trait PIRStruct extends SpatialTraversal { 

  implicit def codegen:PIRCodegen

  // Mapping Mem[Struct(Seq(fieldName, T))] -> Seq((fieldName, Mem[T]))
  def decomposed: mutable.Map[Expr, Seq[(String, Expr)]]
  // Mapping Mem[T] -> Mem[Struct(Seq(fieldName, T))]
  def composed: mutable.Map[Expr, Expr]

  def compose(dexp:Expr) = composed.getOrElse(dexp, dexp)

  def decomposeWithFields[T](exp: Expr, fields: Seq[T]): Either[Expr, Seq[(String, Expr)]] = {
    if (fields.size < 1) {
      Left(exp)
    }
    else if (fields.size == 1) {
      Right(fields.map {
        case field:String => (field, exp)
        case (field:String, dexp:Expr) => (field, exp)
      })
    }
    else {
      Right(decomposed.getOrElseUpdate(exp, {
        fields.map { f => 
          val (field, dexp) = f match {
            case field:String => (field, fresh[Int32]) 
            case (field:String, dexp:Expr) => (field, dexp)
          }
          composed += dexp -> exp
          (field, dexp)
        }
      }))
    }
  }

  def decomposeWithFields[T](exp:Expr)(implicit ev:TypeTag[T]):Either[Expr, Seq[(String, Expr)]] = exp match {
    case Def(StreamInNew(bus)) => decomposeBus(bus, exp) 
    case Def(StreamOutNew(bus)) => decomposeBus(bus, exp)
    case Def(SimpleStruct(elems)) => decomposeWithFields(exp, elems)
    case Def(VectorApply(vec, idx)) => decomposeWithFields(exp, getFields(vec))
    case Def(ListVector(elems)) => decomposeWithFields(exp, elems.flatMap(ele => getFields(ele)))
    case Def(GetDRAMAddress(dram)) => Left(exp) //TODO: consider the case where dram is composed
    case Def(RegNew(init)) => 
      val fields = decomposeWithFields(init) match {
        case Left(init) => Seq() 
        case Right(seq) => seq.map{ case (f, e) => f }
      }
      decomposeWithFields(exp, fields)
    case Const(a:WrappedArray[_]) => decomposeWithFields(exp, a.toSeq) 
    case mem if isMem(mem) => 
      val fields =  mem.tp.typeArguments(0) match {
        case s:StructType[_] => s.fields.map(_._1)
        case _ => Seq()
      }
      decomposeWithFields(mem, fields)
    case ParLocalReader(reads) => 
      val (mem, _, _) = reads.head
      decomposeWithFields(exp, getFields(mem))
    case ParLocalWriter(writes) =>
      val (mem, _, _, _) = writes.head
      decomposeWithFields(exp, getFields(mem))
    case _ => 
      decomposed.get(exp).map(fs => Right(fs)).getOrElse(Left(exp))
  }

  def decomposeBus(bus:Bus, mem:Expr) = bus match {
    //case BurstCmdBus => decomposeWithFields(mem, Seq("offset", "size", "isLoad"))
    case BurstCmdBus => decomposeWithFields(mem, Seq("offset", "size")) // throw away isLoad bit
    case BurstAckBus => decomposeWithFields(mem, Seq("ack")) 
    case bus:BurstDataBus[_] => decomposeWithFields(mem, Seq("data")) 
    //case bus:BurstFullDataBus[_] => decomposeWithFields(mem, Seq("data", "valid")) // throw away valid bit
    case bus:BurstFullDataBus[_] => decomposeWithFields(mem, Seq("data"))
    case GatherAddrBus => decomposeWithFields(mem, Seq("addr"))
    case bus:GatherDataBus[_] => decomposeWithFields(mem, Seq("data"))
    //case bus:ScatterCmdBus[_] => decomposeWithFields(mem, Seq("data", "valid")) // throw away valid bit
    case bus:ScatterCmdBus[_] => decomposeWithFields(mem, Seq("data"))
    case ScatterAckBus => decomposeWithFields(mem, Seq("ack")) 
    case _ => throw new Exception(s"Don't know how to decompose bus ${bus}")
  }

  def decompose[T](exp: Expr, fields: Seq[T])(implicit ev: TypeTag[T]): Seq[Expr] = {
    decomposeWithFields(exp, fields) match {
      case Left(e) => Seq(e)
      case Right(seq) => seq.map(_._2)
    }
  }

  def decompose(exp: Expr): Seq[Expr] = {
    decomposeWithFields(exp) match {
      case Left(e) => Seq(e)
      case Right(seq) => seq.map(_._2)
    }
  }

  def getFields(exp: Expr): Seq[String] = {
    decomposeWithFields(exp) match {
      case Left(e) => Seq()
      case Right(seq) => seq.map(_._1)
    }
  }

  def getField(dexp: Expr): Option[String] = {
    decomposeWithFields(compose(dexp)) match {
      case Left(e) => None 
      case Right(seq) => Some(seq.filter(_._2==dexp).headOption.map(_._1).getOrElse(
        throw new Exception(s"composed $dexp=${compose(dexp)}doesn't contain $dexp. seq=$seq")
        ))
    }
  }

  def getMatchedDecomposed(dele:Expr, ele:Expr):Expr = {
    val i = decompose(compose(dele)).indexOf(dele)
    decompose(ele)(i)
  }

  def qdef(lhs:Any):String = {
    val rhs = lhs match {
      case lhs:Expr if (composed.contains(lhs)) => s"-> ${qdef(composed(lhs))}"
      case Def(e:UnrolledForeach) => 
        s"UnrolledForeach(iters=(${e.iters.mkString(",")}), valids=(${e.valids.mkString(",")}))"
      case Def(e:UnrolledReduce[_,_]) => 
        s"UnrolledReduce(iters=(${e.iters.mkString(",")}), valids=(${e.valids.mkString(",")}))"
      case lhs@Def(d) if isControlNode(lhs) => s"${d.getClass.getSimpleName}(binds=${d.binds})"
      case Op(rhs) => s"$rhs"
      case Def(rhs) => s"$rhs"
      case lhs => s"$lhs"
    }
    val name = lhs match {
      case lhs:Expr => compose(lhs).name.fold("") { n => s" ($n)" }
      case _ => ""
    }
    s"$lhs = $rhs$name"
  }
  
}
