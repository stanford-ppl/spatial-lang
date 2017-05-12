package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.{ControllerExp, CounterExp, UnrolledExp}
import spatial.SpatialConfig
import spatial.analysis.SpatialMetadataExp 
import spatial.SpatialExp
import scala.collection.mutable.HashMap
import spatial.targets.DE1._

trait ChiselGenStream extends ChiselGenSRAM {
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
      emitGlobalWire(src"val ${lhs}_ready = Wire(Bool())", forceful = true)
      emitGlobalWire(src"val ${lhs}_valid = Wire(Bool())", forceful = true)
      emitGlobalWire(src"val ${lhs} = Wire(${newWire(readersOf(lhs).head.node.tp)})")
      bus match {
        case BurstDataBus() =>
        case BurstAckBus =>
        case ScatterAckBus =>
        case GatherDataBus() =>
        case VideoCamera => 
          emit(src"// video in camera, node = $lhs", forceful=true)
          emit(src"// reset and output logic for video camera", forceful=true)
          emit(src"when (reset) {", forceful=true)
          emit(src"  io.stream_out_data           := 0.U ", forceful=true)
          emit(src"  io.stream_out_startofpacket  := 0.U ", forceful=true)
          emit(src"  io.stream_out_endofpacket    := 0.U ", forceful=true)
          emit(src"  io.stream_out_empty          := 0.U ", forceful=true)
          emit(src"} .elsewhen (io.stream_out_ready | ~io.stream_out_valid) { ", forceful=true)
          emit(src"  io.stream_out_data           := converted_data ", forceful=true)
          emit(src"  io.stream_out_startofpacket  := io.stream_in_startofpacket ", forceful=true)
          emit(src"  io.stream_out_endofpacket    := io.stream_in_endofpacket ", forceful=true)
          emit(src"  io.stream_out_empty          := io.stream_in_empty  ", forceful=true)
          emit(src"} ", forceful=true) 

          emit(src"io.stream_in_ready := ${lhs}_ready", forceful=true)
          emit(src"${lhs}_valid := io.stream_in_valid", forceful=true)

        case SliderSwitch =>
          emit(src"// switch, node = $lhs", forceful=true)
//          emit(src"${lhs}_ready := 1.U", forceful=true)
          emit(src"${lhs}_valid := 1.U", forceful=true)

        case _ =>
          streamIns = streamIns :+ lhs.asInstanceOf[Sym[Reg[_]]]
      }
    case StreamOutNew(bus) =>
      val wireType = writersOf(lhs).head.node match {
        case Def(e@ParStreamWrite(_, data, ens)) => src"Vec(${ens.length}, ${newWire(data.head.tp)})"
        case Def(e@StreamWrite(_, data, _)) => newWire(data.tp)
      }

      emitGlobalWire(src"val ${lhs}_ready = Wire(Bool())", forceful = true)
      emitGlobalWire(src"val ${lhs}_valid = Wire(Bool())", forceful = true)
      emitGlobalWire(src"val ${lhs} = Wire(${wireType})")
      bus match {
        case BurstFullDataBus() =>
        case BurstCmdBus =>
        case GatherAddrBus =>
        case ScatterCmdBus() => 
        case VGA =>
          emit(src"// EMITTING FOR VGA; in OUTPUT REGISTERS, Output Register section $lhs", forceful=true)
          emit(src"io.stream_out_valid := ${lhs}_valid", forceful=true)
          emit(src"${lhs}_ready := io.stream_out_ready", forceful=true)
        case LEDR =>
          emit(src"// LEDR, node = $lhs", forceful=true)
          emit(src"${lhs}_ready := 1.U", forceful=true)
          emit(src"${lhs}_valid := 1.U", forceful=true)
        //  emit(src"io.led_stream_out_data := converted_data", forceful=true)
        case _ =>
          streamOuts = streamOuts :+ lhs.asInstanceOf[Sym[Reg[_]]]
      }

    case StreamRead(stream, en) =>
      val isAck = stream match {
        case Def(StreamInNew(bus)) => bus match {
          case BurstAckBus => true
          case ScatterAckBus => true
          case _ => false
        }
        case _ => false
      }
      val parent = parentOf(lhs).get
      emit(src"""${stream}_ready := ${en} & chisel3.util.ShiftRegister(${parent}_datapath_en, ${symDelay(en)}) """)
      if (!isAck) {
        stream match {
          case Def(StreamInNew(bus)) => bus match {
            case VideoCamera => 
              emit(src"""val $lhs = io.stream_in_data""")  // Ignores enable for now
            case SliderSwitch => 
              emit(src"""val $lhs = io.switch_stream_in_data""")
            case BurstDataBus() => 
              emit(src"""val $lhs = (0 until 1).map{ i => ${stream}(i) }""")

            case _ =>
              val id = argMapping(stream)._1
              Predef.assert(id != -1, s"Stream ${quote(stream)} not present in streamIns")
              emit(src"""val ${quote(lhs)} = io.genericStreams.ins($id).bits.data """)  // Ignores enable for now
          }
        }
      } else {
        emit(src"""// read is of burstAck on $stream""")
      }

    case StreamWrite(stream, data, en) =>
      val parent = parentOf(lhs).get
      stream match {
        case Def(StreamOutNew(bus)) => bus match {
            case VGA => 
              emitGlobalWire(src"""// EMITTING VGA GLOBAL""")
              emitGlobalWire(src"""val ${stream} = Wire(UInt(16.W))""")
              emitGlobalWire(src"""val converted_data = Wire(UInt(16.W))""")
              emit(src"""// emiiting data for stream ${stream}""")
              emit(src"""${stream} := $data""")
              emit(src"""converted_data := ${stream}""")
              emit(src"""${stream}_valid := ${en} & ShiftRegister(${parent}_datapath_en & ~${parent}_inhibitor,${symDelay(en)})""")
            case LEDR =>
              emitGlobalWire(src"""val ${stream} = Wire(UInt(32.W))""")
        //      emitGlobalWire(src"""val converted_data = Wire(UInt(32.W))""")
              emit(src"""${stream} := $data""")
              emit(src"""io.led_stream_out_data := ${stream}""")
            case BurstFullDataBus() =>
              emit(src"""${stream}_valid := ShiftRegister(${parent}_datapath_en & ~${parent}_inhibitor,${symDelay(en)}) & $en""")
              emit(src"""${stream} := $data""")

            case BurstCmdBus =>  
              emit(src"""${stream}_valid := chisel3.util.ShiftRegister(${parent}_datapath_en & ~${parent}_inhibitor,${symDelay(en)}) & $en""")
              emit(src"""${stream} := $data""")

            case _ => 
              emit(src"""${stream}_valid := ShiftRegister(${parent}_datapath_en & ~${parent}_inhibitor,${symDelay(en)}) & $en""")
              val id = argMapping(stream)._1
              Predef.assert(id != -1, s"Stream ${quote(stream)} not present in streamOuts")
              emit(src"""io.genericStreams.outs($id).bits.data := ${quote(data)}.number """)  // Ignores enable for now
              emit(src"""io.genericStreams.outs($id).valid := ${stream}_valid""")
        }
      }
    case _ => super.emitNode(lhs, rhs)
  }

  override protected def emitFileFooter() {

    val insList = (0 until streamIns.length).map{ i => s"StreamParInfo(32, 1)" }.mkString(",")
    val outsList = (0 until streamOuts.length).map{ i => s"StreamParInfo(32, 1)" }.mkString(",")

    withStream(getStream("IOModule")) {
      emit(src"// Non-memory Streams")
      emit(s"""val io_streamInsInfo = List(${insList})""")
      emit(s"""val io_streamOutsInfo = List(${outsList})""")
    }

    withStream(getStream("Instantiator")) {
      emit(src"// Non-memory Streams")
      emit(s"""val streamInsInfo = List(${insList})""")
      emit(s"""val streamOutsInfo = List(${outsList})""")
    }

    super.emitFileFooter()
  }
}
