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
      emit(src"c1->setArg(${argMapping(reg)}, $v); // $lhs", forceful = true)
      emit(src"${reg.tp} $reg = $v;")
    case GetArg(reg)    => emit(src"${lhs.tp} $lhs = (${lhs.tp}) c1->getArg(${argMapping(reg)});", forceful = true)
    case SetMem(dram, data) => 
      emit(src"c1->memcpy($dram, ${data}, ${data}_length * sizeof(${data.tp}));", forceful = true)
      emit(src"c1->setArg(${argMapping(dram)}, $dram);")
    case GetMem(dram, data) => 
      emit(src"$data = $dram;", forceful = true)
    case _ => super.emitNode(lhs, rhs)
  }



}
