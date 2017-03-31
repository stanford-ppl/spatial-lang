package spatial.codegen.cppgen

import argon.codegen.cppgen.CppCodegen
import spatial.api.HostTransferExp
import spatial.SpatialConfig
import spatial.api.RegExp
import spatial.SpatialExp
import spatial.analysis.SpatialMetadataExp


trait CppGenHostTransfer extends CppCodegen  {
  val IR: SpatialExp
  import IR._

  override protected def spatialNeedsFPType(tp: Type[_]): Boolean = tp match { // FIXME: Why doesn't overriding needsFPType work here?!?!
      case FixPtType(s,d,f) => if (s) true else if (f == 0) false else true
      case IntType()  => false
      case LongType() => false
      case FloatType() => true
      case DoubleType() => true
      case _ => super.needsFPType(tp)
  }

  override def quote(s: Exp[_]): String = {
  	if (SpatialConfig.enableNaming) {
	    s match {
	      case lhs: Sym[_] =>
	        lhs match {
	          case Def(SetArg(reg:Sym[_],_)) => s"x${lhs.id}_set${reg.id}"
	          case Def(GetArg(reg:Sym[_])) => s"x${lhs.id}_get${reg.id}"
	          case Def(SetMem(_,_)) => s"x${lhs.id}_setMem"
	          case Def(GetMem(_,_)) => s"x${lhs.id}_getMem"
	          case _ => super.quote(s)
	        }
	      case _ =>
	        super.quote(s)
	    }
    } else {
    	super.quote(s)
    }
  } 

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case SetArg(reg, v) => 
      reg.tp.typeArguments.head match {
        case FixPtType(s,d,f) => if (f != 0) {
            emit(src"c1->setArg(${argMapping(reg)._1}, $v * (1 << $f)); // $lhs", forceful = true)
            emit(src"${reg.tp} $reg = $v;")
          } else {
            emit(src"c1->setArg(${argMapping(reg)._1}, $v); // $lhs", forceful = true)
            emit(src"${reg.tp} $reg = $v;")
          }
        case _ => 
            emit(src"c1->setArg(${argMapping(reg)._1}, $v); // $lhs", forceful = true)
            emit(src"${reg.tp} $reg = $v;")

      }
    case GetArg(reg)    => 
      reg.tp.typeArguments.head match {
        case FixPtType(s,d,f) => if (f != 0) {
            emit(src"int32_t ${lhs}_tmp = c1->getArg(${argMapping(reg)._1});", forceful = true)            
            emit(src"${lhs.tp} ${lhs} = (${lhs.tp}) ${lhs}_tmp / (1 << $f);", forceful = true)            
          } else {
            emit(src"${lhs.tp} $lhs = (${lhs.tp}) c1->getArg(${argMapping(reg)._1});", forceful = true)
          }
        case _ => 
            emit(src"${lhs.tp} $lhs = (${lhs.tp}) c1->getArg(${argMapping(reg)._1});", forceful = true)
        }
    case SetMem(dram, data) => 
      if (spatialNeedsFPType(dram.tp.typeArguments.head)) {
        dram.tp.typeArguments.head match { 
          case FixPtType(s,d,f) => 
            emit(src"vector<int32_t>* ${dram}_rawified = new vector<int32_t>((*${data}).size());")
            open(src"for (int ${dram}_rawified_i = 0; ${dram}_rawified_i < (*${data}).size(); ${dram}_rawified_i++) {")
              emit(src"(*${dram}_rawified)[${dram}_rawified_i] = (int32_t) ((*${data})[${dram}_rawified_i] * (1 << $f));")
            close("}")
            emit(src"c1->memcpy($dram, &(*${dram}_rawified)[0], (*${dram}_rawified).size() * sizeof(int32_t));", forceful = true)
          case _ => emit(src"c1->memcpy($dram, &(*${data})[0], (*${data}).size() * sizeof(int32_t));", forceful = true)
        }
      } else {
        emit(src"c1->memcpy($dram, &(*${data})[0], (*${data}).size() * sizeof(int32_t));", forceful = true)
      }
    case GetMem(dram, data) => 
      if (spatialNeedsFPType(dram.tp.typeArguments.head)) {
        dram.tp.typeArguments.head match { 
          case FixPtType(s,d,f) => 
            emit(src"vector<int32_t>* ${data}_rawified = new vector<int32_t>((*${data}).size());")
            emit(src"c1->memcpy(&(*${data}_rawified)[0], $dram, (*${data}_rawified).size() * sizeof(int32_t));", forceful = true)
            open(src"for (int ${data}_i = 0; ${data}_i < (*${data}).size(); ${data}_i++) {")
              emit(src"int32_t ${data}_tmp = (*${data}_rawified)[${data}_i];")
              emit(src"(*${data})[${data}_i] = (double) ${data}_tmp / (1 << $f);")
            close("}")
          case _ => emit(src"c1->memcpy(&(*$data)[0], $dram, (*${data}).size() * sizeof(int32_t));", forceful = true)
        }
      } else {
        emit(src"c1->memcpy(&(*$data)[0], $dram, (*${data}).size() * sizeof(int32_t));", forceful = true)
      }
    case _ => super.emitNode(lhs, rhs)
  }



}
