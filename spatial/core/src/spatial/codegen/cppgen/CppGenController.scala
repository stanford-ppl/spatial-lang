package spatial.codegen.cppgen

import argon.codegen.cppgen.CppCodegen
import argon.core._
import spatial.aliases._
import spatial.nodes._
import scala.collection.mutable
import spatial.SpatialConfig

trait CppGenController extends CppCodegen {

  var instrumentCounters: List[(Exp[_], Int)] = List()

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case Hwblock(func,isForever) =>
      controllerStack.push(lhs)
      // Skip everything inside
      instrumentCounters = instrumentCounters :+ (lhs, controllerStack.length)
      toggleEn()
      emitBlock(func)
      toggleEn()
      emit(s"// Register ArgIns and ArgIOs in case some are unused")
      emit(s"c1->setNumArgIns(${argIns.length} + ${drams.length} + ${argIOs.length});")
      emit(s"c1->setNumArgIOs(${argIOs.length});")
      emit(s"time_t tstart = time(0);")
      val memlist = if (setMems.nonEmpty) {s""", ${setMems.mkString(",")}"""} else ""
      emit(s"c1->run();")
      emit(s"time_t tend = time(0);")
      emit(s"double elapsed = difftime(tend, tstart);")
      emit(s"""std::cout << "Kernel done, test run time = " << elapsed << " ms" << std::endl;""")
      controllerStack.pop()

      if (SpatialConfig.enableInstrumentation) {
        emit(src"""std::ofstream instrumentation ("./instrumentation.txt");""")

        emit(s"// Need to instrument ${instrumentCounters}")
        val instrumentStart = argIOs.length + argOuts.length // These "invisible" instrumentation argOuts start after the full range of IR interface args
        emit(s"// Detected ${argOuts.length} argOuts and ${argIOs.length} argIOs, start instrument indexing at ${instrumentStart}")
        // In order to get niter / parent execution, we need to know the immediate parent of each controller and divide out that guy's niter
        val immediate_parent_niter_hashmap = mutable.HashMap[Int, Exp[_]]()
        instrumentCounters.zipWithIndex.foreach{case (c, i) => 
          immediate_parent_niter_hashmap.getOrElseUpdate(c._2, c._1)
          val indent = "  "*c._2
          emit(s"""long ${c._1}_cycles = c1->getArg(${instrumentStart}+2*${i}, false);""")
          emit(s"""long ${c._1}_iters = c1->getArg(${instrumentStart}+2*${i}+1, false);""")
          val immediate_parent = if (immediate_parent_niter_hashmap.get(c._2-1).isDefined) immediate_parent_niter_hashmap.get(c._2-1).get else c._1
          emit(s"""long ${c._1}_iters_per_parent = ${c._1}_iters / std::max((long)1,${immediate_parent}_iters);""")
          emit(s"""long ${c._1}_avg = ${c._1}_cycles / std::max((long)1,${c._1}_iters);""")
          emit(s"""std::cout << "${indent}${c._1} - " << ${c._1}_avg << " (" << ${c._1}_cycles << " / " << ${c._1}_iters << ") [" << ${c._1}_iters_per_parent << " iters/parent execution]" << std::endl;""")
          open(s"if (instrumentation.is_open()) {")
            emit(s"""instrumentation << "${indent}${c._1} - " << ${c._1}_avg << " (" << ${c._1}_cycles << " / " << ${c._1}_iters << ") [" << ${c._1}_iters_per_parent << " iters/parent execution]" << std::endl;""")
          close("}")
        }
        emit(src"""instrumentation.close();""")
      }

    case UnitPipe(_,func) => 
      controllerStack.push(lhs)
      instrumentCounters = instrumentCounters :+ (lhs, controllerStack.length)
      emitBlock(func)
      controllerStack.pop()

    case ParallelPipe(ens,func) =>
      controllerStack.push(lhs)
      instrumentCounters = instrumentCounters :+ (lhs, controllerStack.length)
      emitBlock(func)
      controllerStack.pop()      

    case op@Switch(body,selects,cases) =>
      controllerStack.push(lhs)
      instrumentCounters = instrumentCounters :+ (lhs, controllerStack.length)
      cases.collect{case s: Sym[_] => stmOf(s)}.foreach{ stm => 
        visitStm(stm)
      }
      controllerStack.pop()      

    case op@SwitchCase(body) =>
      controllerStack.push(lhs)
      instrumentCounters = instrumentCounters :+ (lhs, controllerStack.length)
      emitBlock(body)
      controllerStack.pop()      

    case StateMachine(ens,start,notDone,action,nextState,state) =>
      controllerStack.push(lhs)
      instrumentCounters = instrumentCounters :+ (lhs, controllerStack.length)    
      emitBlock(notDone)
      visitBlock(action)
      visitBlock(nextState)
      controllerStack.pop()

    case _ => super.emitNode(lhs, rhs)
  }
}

   
