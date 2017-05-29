package spatial.codegen.cppgen

import argon.codegen.FileDependencies
import argon.codegen.cppgen.CppCodegen
import spatial.lang.CounterExp
import spatial.{SpatialConfig, SpatialExp}

trait CppGenCounter extends CppCodegen with FileDependencies {
  val IR: SpatialExp
  import IR._

  // dependencies ::= AlwaysDep("cppgen", "Counter.cpp")

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          lhs match {
            case Def(CounterNew(s,e,st,p)) =>
              s"x${lhs.id}_ctr"
            case Def(CounterChainNew(ctrs)) =>
              s"x${lhs.id}_ctrchain"
            case _ =>
              super.quote(s)
          }
        case _ =>
          super.quote(s)
      }
    } else {
      super.quote(s)
    }
  }

  override protected def remap(tp: Type[_]): String = tp match {
    case CounterType      => src"Counter"
    case CounterChainType => src"Array[Counter]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case CounterNew(start,end,step,par) =>
    case CounterChainNew(ctrs) =>
    case Forever() => emit(s"// ${quote(lhs)} = Forever")
    case _ => super.emitNode(lhs, rhs)
  }

}
