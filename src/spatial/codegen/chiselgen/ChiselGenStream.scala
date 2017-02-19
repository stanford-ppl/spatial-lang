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
      val streamId = streamIns.indexOf(stream.asInstanceOf[Sym[StreamIn[_]]])
      emit(src"""val $lhs = io.streamIns.data(${streamId})""")
      emit(src"""io.streamIns.pop := $en""")
    case StreamEnq(stream, data, en) => 
      val streamId = streamIns.indexOf(stream.asInstanceOf[Sym[StreamIn[_]]])
      emit(src"""io.streamOuts.push(${streamId}) := $en""")
      emit(src"""io.streamOuts.data(${streamId}) := $data""")
    case _ => super.emitNode(lhs, rhs)
  }

  override protected def emitFileFooter() {

    withStream(getStream("IOModule")) {
      open(s"""class StreamInsBundle() extends Bundle{""")
      emit(s"""val data = Vec(${streamIns.length}, Input(UInt(32.W)))""")
      emit(s"""val ready = Vec(${streamIns.length}, Input(Bool()))""")
      emit(s"""val pop = Vec(${streamIns.length}, Output(Bool()))""")
      streamIns.zipWithIndex.map { case(p,i) => 
        emit(s"""//  ${quote(p)} = streamIns($i) ( ${nameOf(p).getOrElse("")} )""")
      // streamInsByName = streamInsByName :+ s"${quote(p)}"
      }
      close("}")

      open(s"""class StreamOutsBundle() extends Bundle{""")
      emit(s"""val data = Vec(${streamOuts.length}, Output(UInt(32.W)))""")
      emit(s"""val ready = Vec(${streamOuts.length}, Output(Bool()))""")
      emit(s"""val push = Vec(${streamOuts.length}, Output(Bool()))""")
      streamOuts.zipWithIndex.map { case(p,i) => 
        emit(s"""//  ${quote(p)} = streamOuts($i) ( ${nameOf(p).getOrElse("")} )""")
      // streamOutsByName = streamOutsByName :+ s"${quote(p)}"
      }
      close("}")
    }
    super.emitFileFooter()
  }

}
