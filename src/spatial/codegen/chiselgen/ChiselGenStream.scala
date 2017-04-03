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
      s"$bus".replace("(","").replace(")","") match {
        case "BurstDataBus" =>
        case "BurstAckBus" =>
        case _ =>
          streamIns = streamIns :+ lhs.asInstanceOf[Sym[Reg[_]]]
      }
    case StreamOutNew(bus) =>
      s"$bus".replace("(","").replace(")","") match {
        case "BurstFullDataBus" =>
        case "BurstCmdBus" =>
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
      if (!isAck) {
        val streamID = streamIns.indexOf(stream.asInstanceOf[Sym[Reg[_]]])
        Predef.assert(streamID != -1, s"Stream ${quote(stream)} not present in streamIns")
        emit(src"""val ${quote(lhs)} = io.streamIns.bits.data // Will use ID=$streamID in next change. StreamRead(stream = $stream, en = $en)""")  // Ignores enable for now
      } else {
        emit(src"""// read is of burstAck on $stream""")
      }

    case StreamWrite(stream, data, en) =>
        val streamID = streamOuts.indexOf(stream.asInstanceOf[Sym[Reg[_]]])
        Predef.assert(streamID != -1, s"Stream ${quote(stream)} not present in streamOuts")
        emit(src"""io.streamOuts.bits.data := ${quote(data)}.asUInt() // Will use ID=$streamID in next change. StreamWrite(stream = $stream, data = $data, en = $en)""")  // Ignores enable for now
        emit(src"""io.streamOuts.valid := ${parentOf(lhs).get}_done & $en""")
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
