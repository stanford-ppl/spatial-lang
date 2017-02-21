package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.{ControllerExp, CounterExp, UnrolledExp}
import spatial.SpatialConfig
import spatial.analysis.SpatialMetadataExp
import spatial.SpatialExp

trait ChiselGenStream extends ChiselCodegen {
  val IR: SpatialExp
  import IR._


  var streamIns: List[Sym[Reg[_]]] = List()
  var streamOuts: List[Sym[Reg[_]]] = List()

  override def quote(s: Exp[_]): String = {
    s match {
      case b: Bound[_] => super.quote(s)
      case _ => super.quote(s)

    }
  } 

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case StreamInNew(bus) =>
      streamIns = streamIns :+ lhs.asInstanceOf[Sym[Reg[_]]]
    case StreamOutNew(bus) => 
      streamOuts = streamOuts :+ lhs.asInstanceOf[Sym[Reg[_]]]
    case StreamDeq(stream, en) => 
      // val streamId = streamIns.indexOf(stream.asInstanceOf[Sym[StreamIn[_]]])
      emit(src"""val $lhs = ${stream}_data""")
      emit(src"""${stream}_pop := $en""")
    case StreamEnq(stream, data, en) => 
      // val streamId = streamIns.indexOf(stream.asInstanceOf[Sym[StreamIn[_]]])
      emit(src"""${stream}_push := $en""")
      emit(src"""${stream}_data := $data""")
    case _ => super.emitNode(lhs, rhs)
  }

  override protected def emitFileFooter() {

    withStream(getStream("IOModule")) {
      open(s"""class StreamInsBundle() extends Bundle{""")
      emit(s"""val data = Vec(${streamIns.length}, Input(UInt(32.W)))""")
      emit(s"""val ready = Vec(${streamIns.length}, Input(Bool()))""")
      emit(s"""val pop = Vec(${streamIns.length}, Output(Bool()))""")
      streamIns.zipWithIndex.map { case(p,i) => 
        withStream(getStream("GlobalWires")) {
          emit(s"""val ${quote(p)}_data = io.StreamIns.data($i) // ( ${nameOf(p).getOrElse("")} )""")  
          emit(s"""val ${quote(p)}_ready = io.StreamIns.ready($i) // ( ${nameOf(p).getOrElse("")} )""")  
          emit(s"""val ${quote(p)}_pop = io.StreamIns.pop($i) // ( ${nameOf(p).getOrElse("")} )""")  
        }
        emit(s"""//  ${quote(p)} = streamIns($i) ( ${nameOf(p).getOrElse("")} )""")
      // streamInsByName = streamInsByName :+ s"${quote(p)}"
      }
      close("}")

      open(s"""class StreamOutsBundle() extends Bundle{""")
      emit(s"""val data = Vec(${streamOuts.length}, Output(UInt(32.W)))""")
      emit(s"""val ready = Vec(${streamOuts.length}, Output(Bool()))""")
      emit(s"""val push = Vec(${streamOuts.length}, Output(Bool()))""")
      streamOuts.zipWithIndex.map { case(p,i) => 
        withStream(getStream("GlobalWires")) {
          emit(s"""val ${quote(p)}_data = io.StreamOuts.data($i) // ( ${nameOf(p).getOrElse("")} )""")  
          emit(s"""val ${quote(p)}_ready = io.StreamOuts.ready($i) // ( ${nameOf(p).getOrElse("")} )""")  
          emit(s"""val ${quote(p)}_push = io.StreamOuts.pop($i) // ( ${nameOf(p).getOrElse("")} )""")  
        }
        emit(s"""//  ${quote(p)} = streamOuts($i) ( ${nameOf(p).getOrElse("")} )""")
      // streamOutsByName = streamOutsByName :+ s"${quote(p)}"
      }
      close("}")
    }

    super.emitFileFooter()
  }

}
