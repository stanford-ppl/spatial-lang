package spatial.codegen.dotgen

import argon.codegen.dotgen.DotCodegen
import spatial.api.{ControllerExp, CounterExp, UnrolledExp}
import spatial.SpatialConfig
import spatial.analysis.SpatialMetadataExp
import spatial.SpatialExp

trait DotGenController extends DotCodegen {
  val IR: SpatialExp
  import IR._

    //val smStr = styleOf(sym) match {
      //case MetaPipe => s"Metapipe"
      //case StreamPipe => "Streampipe"
      //case InnerPipe => "Innerpipe"
      //case SeqPipe => s"Seqpipe"
      //case ForkJoin => s"Parallel"
    //}

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case Hwblock(func) =>

    case UnitPipe(en,func) =>

    case ParallelPipe(en,func) =>

    case OpForeach(cchain, func, iters) =>

    case OpReduce(cchain, accum, map, load, reduce, store, fold, zero, rV, iters) =>

    case OpMemReduce(cchainMap,cchainRed,accum,map,loadRes,loadAcc,reduce,storeAcc,fold,zero,rV,itersMap,itersRed) =>

    case _ => super.emitNode(lhs, rhs)
  }
}
