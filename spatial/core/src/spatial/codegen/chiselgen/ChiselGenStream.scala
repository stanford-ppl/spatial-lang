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
      emitGlobal(src"val ${lhs}_ready = Wire(Bool())", forceful = true)
      emitGlobal(src"val ${lhs}_valid = Wire(Bool())", forceful = true)
      bus match {
        case BurstDataBus() =>
        case BurstAckBus =>
        case _ =>
          streamIns = streamIns :+ lhs.asInstanceOf[Sym[Reg[_]]]
      }
    case StreamOutNew(bus) =>
      emitGlobal(src"val ${lhs}_ready = Wire(Bool())", forceful = true)
      emitGlobal(src"val ${lhs}_valid = Wire(Bool())", forceful = true)
      bus match {
        case BurstFullDataBus() =>
        case BurstCmdBus =>
        case _ =>
          streamOuts = streamOuts :+ lhs.asInstanceOf[Sym[Reg[_]]]
      }
    case StreamRead(stream, en) =>
      val isAck = stream match {
        case Def(StreamInNew(bus)) => bus match {
          case BurstAckBus => true
          case _ => false
        }
        case _ => false
      }
      emit(src"""${stream}_ready := ${en} & ${parentOf(lhs).get}_datapath_en // TODO: Definitely wrong thing for parstreamread""")
      if (!isAck) {
        val id = argMapping(stream)._1
        Predef.assert(id != -1, s"Stream ${quote(stream)} not present in streamIns")
        emit(src"""val ${quote(lhs)} = io.genericStreams.ins($id).bits.data """)  // Ignores enable for now
      } else {
        emit(src"""// read is of burstAck on $stream""")
      }

    case StreamWrite(stream, data, en) =>
      val externalStream = stream match {
        case Def(StreamOutNew(bus)) => s"$bus".replace("(","").replace(")","") match {
          case "BustFullDataBus" => false
          case "BurstCmdBus" => false
          case _ => true
        }
        case _ => false
			}

      emit(src"""${stream}_valid := ${parentOf(lhs).get}_done & $en""")
      if (externalStream) {
        val id = argMapping(stream)._1

        Predef.assert(id != -1, s"Stream ${quote(stream)} not present in streamOuts")
        emit(src"""io.genericStreams.outs($id).bits.data := ${quote(data)}.number """)  // Ignores enable for now
        emit(src"""io.genericStreams.outs($id).valid := ${stream}_valid""")
      } else {
        emit(src"""${stream}_data := $data""")
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
