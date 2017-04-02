package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.{ControllerExp, CounterExp, UnrolledExp}
import spatial.SpatialConfig
import spatial.analysis.SpatialMetadataExp
import spatial.SpatialExp
import scala.collection.mutable.HashMap

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
      s"$bus" match {
        case "BurstFullDataBus" => 
        case "BurstAckBus" => 
        case "VideoCamera" => 
        //  emitGlobal("val $lhs_en = Bool()")
          emit(src"// video in camera, node = $lhs", forceful=true)
          emit(src"// reset and output logic for video camera", forceful=true)
          emit(src"when (reset) {", forceful=true)
          emit(src"  io.stream_out_data           := 0.U ", forceful=true)
          emit(src"  io.stream_out_startofpacket  := 0.U ", forceful=true)
          emit(src"  io.stream_out_endofpacket    := 0.U ", forceful=true)
          emit(src"  io.stream_out_empty          := 0.U ", forceful=true)
          emit(src"  io.stream_out_valid          := 0.U ", forceful=true)
          emit(src"} .elsewhen (io.stream_out_ready | ~io.stream_out_valid) { ", forceful=true)
          emit(src"  io.stream_out_data           := converted_data ", forceful=true)
          emit(src"  io.stream_out_startofpacket  := io.stream_in_startofpacket ", forceful=true)
          emit(src"  io.stream_out_endofpacket    := io.stream_in_endofpacket ", forceful=true)
          emit(src"  io.stream_out_empty          := io.stream_in_empty  ", forceful=true)
          emit(src"  io.stream_out_valid          := io.stream_in_valid  ", forceful=true)
          emit(src"} ", forceful=true) 

        case _ =>
          emit(src"// New stream in $lhs")
          streamIns = streamIns :+ lhs.asInstanceOf[Sym[Reg[_]]]
      }
    case StreamOutNew(bus) =>
      s"$bus" match {
        case "BurstCmdBus" => 
        case "VGA" =>
          emit(src"// EMITTING FOR VGA; in OUTPUT REGISTERS, Output Register section $lhs", forceful=true)
        case _ =>
          emit(src"// New stream out $lhs")
          streamOuts = streamOuts :+ lhs.asInstanceOf[Sym[Reg[_]]]
      }
    case StreamRead(stream, en) =>
      emit(src"""val $lhs = io.stream_in_data""")  // Ignores enable for now
//      emit(src"""val $lhs = ${stream}_data""")  // Ignores enable for now
      

    case StreamWrite(stream, data, en) =>
      emitGlobal(src"""val ${stream}_valid = Wire(Bool())""")
      emit(src"""${stream}_valid := ${parentOf(lhs).get}_done & $en""")
      emitGlobal(src"""val ${stream}_data = Wire(UInt(16.W))""")
      emitGlobal(src"""val converted_data = Wire(UInt(16.W))""")
//      emitGlobal(src"""val ${stream}_data = Wire(UInt(97.W))""")
      emit(src"""${stream}_data := $data""")
      emit(src"""converted_data := ${stream}_data""")
    case _ => super.emitNode(lhs, rhs)
  }

  override protected def emitFileFooter() {

    // withStream(getStream("IOModule")) {
    //   open(s"""class StreamInsBundle() extends Bundle{""")
    //   emit(s"""val data = Vec(${streamIns.length}, Input(UInt(32.W)))""")
    //   emit(s"""val ready = Vec(${streamIns.length}, Input(Bool()))""")
    //   emit(s"""val pop = Vec(${streamIns.length}, Output(Bool()))""")
    //   streamIns.zipWithIndex.foreach{ case(p,i) =>
    //     withStream(getStream("GlobalWires")) {
    //       emit(s"""val ${quote(p)}_data = io.StreamIns.data($i) // ( ${nameOf(p).getOrElse("")} )""")  
    //       emit(s"""val ${quote(p)}_ready = io.StreamIns.ready($i) // ( ${nameOf(p).getOrElse("")} )""")  
    //       emit(s"""val ${quote(p)}_pop = io.StreamIns.pop($i) // ( ${nameOf(p).getOrElse("")} )""")  
    //     }
    //     emit(s"""//  ${quote(p)} = streamIns($i) ( ${nameOf(p).getOrElse("")} )""")
    //   // streamInsByName = streamInsByName :+ s"${quote(p)}"
    //   }
    //   close("}")

    //   open(s"""class StreamOutsBundle() extends Bundle{""")
    //   emit(s"""val data = Vec(${streamOuts.length}, Output(UInt(32.W)))""")
    //   emit(s"""val ready = Vec(${streamOuts.length}, Output(Bool()))""")
    //   emit(s"""val push = Vec(${streamOuts.length}, Output(Bool()))""")
    //   streamOuts.zipWithIndex.foreach{ case(p,i) =>
    //     withStream(getStream("GlobalWires")) {
    //       emit(s"""val ${quote(p)}_data = io.StreamOuts.data($i) // ( ${nameOf(p).getOrElse("")} )""")  
    //       emit(s"""val ${quote(p)}_ready = io.StreamOuts.ready($i) // ( ${nameOf(p).getOrElse("")} )""")  
    //       emit(s"""val ${quote(p)}_push = io.StreamOuts.pop($i) // ( ${nameOf(p).getOrElse("")} )""")  
    //     }
    //     emit(s"""//  ${quote(p)} = streamOuts($i) ( ${nameOf(p).getOrElse("")} )""")
    //   // streamOutsByName = streamOutsByName :+ s"${quote(p)}"
    //   }
    //   close("}")
    // }

    super.emitFileFooter()
  }

}
