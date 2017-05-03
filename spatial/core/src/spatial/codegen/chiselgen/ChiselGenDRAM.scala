package spatial.codegen.chiselgen

import spatial.SpatialConfig
import spatial.SpatialExp
import scala.collection.mutable.HashMap

trait ChiselGenDRAM extends ChiselGenSRAM with ChiselGenStructs {
  val IR: SpatialExp
  import IR._

  var loadsList = List[Exp[_]]()
  var storesList = List[Exp[_]]()
  var loadParMapping = List[String]()
  var storeParMapping = List[String]()
  // var loadParMapping = HashMap[Int, (Int,Int)]() 
  // var storeParMapping = HashMap[Int, (Int,Int)]() 

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          lhs match {
              case Def(e: DRAMNew[_,_]) => s"x${lhs.id}_dram"
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
    case tp: DRAMType[_] => src"Array[${tp.child}]"
    case _ => super.remap(tp)
  }

  protected def getLastChild(lhs: Exp[_]): Exp[_] = {
    var nextLevel = childrenOf(lhs)
    if (nextLevel.length == 0) {
      lhs
    } else {
      getLastChild(nextLevel.last)
    }
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@DRAMNew(dims) => 
      if (argMapping(lhs) == (-1,-1,-1)) {
        throw new UnusedDRAMException(lhs, nameOf(lhs).getOrElse("noname"))
      }

    case GetDRAMAddress(dram) =>
      val id = argMapping(dram)._2
      emit(src"""val $lhs = io.argIns($id)""")

    case FringeDenseLoad(dram,cmdStream,dataStream) =>
      // Get parallelization of datastream
      val par = readersOf(dataStream).head.node match {
        case Def(e@ParStreamRead(strm, ens)) => ens.length
        case _ => 1
      }

      val id = loadsList.length
      loadParMapping = loadParMapping :+ s"StreamParInfo(${bitWidth(dram.tp.typeArguments.head)}, ${par})" 
      loadsList = loadsList :+ dram
      val turnstiling_stage = getLastChild(parentOf(lhs).get)
      emitGlobalWire(src"""val ${turnstiling_stage}_enq = io.memStreams.loads(${id}).rdata.valid""")
      val allData = dram.tp.typeArguments.head match {
        case FixPtType(s,d,f) => if (spatialNeedsFPType(dram.tp.typeArguments.head)) {
            (0 until par).map{ i => src"""Utils.FixedPoint($s,$d,$f,io.memStreams.loads($id).rdata.bits($i))""" }.mkString(",")
          } else {
            (0 until par).map{ i => src"io.memStreams.loads($id).rdata.bits($i)" }.mkString(",")
          }
        case _ => (0 until par).map{ i => src"io.memStreams.loads($id).rdata.bits($i)" }.mkString(",")
      }
      emit(src"""val ${dataStream}_data = Vec(List($allData))""")
      emit(src"""${dataStream}_valid := io.memStreams.loads($id).rdata.valid""")
      emit(src"${cmdStream}_ready := io.memStreams.loads($id).cmd.ready")
      emitGlobalWire(src"""val ${cmdStream}_data = Wire(UInt(97.W)) // TODO: What is width of burstcmdbus?""")
      emit(src"io.memStreams.loads($id).rdata.ready := ${dataStream}_ready // Contains stage enable, rdatavalid, and fifo status")
      emit(src"io.memStreams.loads($id).cmd.bits.addr := ${cmdStream}_data(63,0) // Field 0")
      emit(src"io.memStreams.loads($id).cmd.bits.size := ${cmdStream}_data(95,64) // Field 1")
      emit(src"io.memStreams.loads($id).cmd.valid :=  ${cmdStream}_valid// LSB is enable, instead of pulser?? Reg(UInt(1.W), pulser.io.out)")
      emit(src"io.memStreams.loads($id).cmd.bits.isWr := ~${cmdStream}_data(96) // Field 2")
      emit(src"io.memStreams.loads($id).cmd.bits.isSparse := 0.U")

    case FringeSparseLoad(dram,addrStream,dataStream) =>
      // Get parallelization of datastream
      val par = readersOf(dataStream).head.node match {
        case Def(e@ParStreamRead(strm, ens)) => ens.length
        case _ => 1
      }
      assert(par == 1, s"Unsupported par '$par' for sparse loads! Must be 1 currently")

      val id = loadsList.length
      loadParMapping = loadParMapping :+ s"StreamParInfo(${bitWidth(dram.tp.typeArguments.head)}, ${par})" 
      loadsList = loadsList :+ dram
      val turnstiling_stage = getLastChild(parentOf(lhs).get)
      emitGlobalWire(src"""val ${turnstiling_stage}_enq = io.memStreams.loads(${id}).rdata.valid""")
      val allData = dram.tp.typeArguments.head match {
        case FixPtType(s,d,f) => if (spatialNeedsFPType(dram.tp.typeArguments.head)) {
            (0 until par).map{ i => src"""Utils.FixedPoint($s,$d,$f,io.memStreams.loads($id).rdata.bits($i))""" }.mkString(",")
          } else {
            (0 until par).map{ i => src"io.memStreams.loads($id).rdata.bits($i)" }.mkString(",")
          }
        case _ => (0 until par).map{ i => src"io.memStreams.loads($id).rdata.bits($i)" }.mkString(",")
      }
      emit(src"""val ${dataStream}_data = Vec(List($allData))""")
//      emitGlobalWire(src"""val ${addrStream}_data = Wire(UInt(97.W)) // Not sure if width is right""")
      emit(src"""${dataStream}_valid := io.memStreams.loads($id).rdata.valid""")
      emit(src"${addrStream}_ready := io.memStreams.loads($id).cmd.ready")
      emitGlobalWire(src"""val ${addrStream}_data = Wire(Vec(1, UInt(64.W))) // TODO: Vec(1) for address seems ridiculous""")
      emit(src"io.memStreams.loads($id).rdata.ready := ${dataStream}_ready // Contains stage enable, rdatavalid, and fifo status")
      emit(src"io.memStreams.loads($id).cmd.bits.addr := ${addrStream}_data(0)(63,0) // Field 0. TODO: Hardcoded indexing to 0")
      emit(src"io.memStreams.loads($id).cmd.bits.size := 1.U")
      emit(src"io.memStreams.loads($id).cmd.valid :=  ${addrStream}_valid// LSB is enable, instead of pulser?? Reg(UInt(1.W), pulser.io.out)")
      emit(src"io.memStreams.loads($id).cmd.bits.isWr := false.B // Field 2")
      emit(src"io.memStreams.loads($id).cmd.bits.isSparse := 1.U")

    case FringeDenseStore(dram,cmdStream,dataStream,ackStream) =>
      // Get parallelization of datastream
      val par = writersOf(dataStream).head.node match {
        case Def(e@ParStreamWrite(_, _, ens)) => ens.length
        case _ => 1
      }

      val id = storesList.length
      storeParMapping = storeParMapping :+ s"StreamParInfo(${bitWidth(dram.tp.typeArguments.head)}, ${par})" 
      storesList = storesList :+ dram
      // emitGlobalWire(src"""val ${childrenOf(childrenOf(parentOf(lhs).get).apply(1)).apply(1)}_enq = io.memStreams(${id}).rdata.valid""")
      emit(src"""// Connect streams to ports on mem controller""")
      val allData = (0 until par).map{ i => src"io.memStreams.stores($id).rdata.bits($i)" }.mkString(",")
      emitGlobalWire(src"val ${dataStream}_data = Wire(Vec($par, UInt(33.W)))")
      emit(src"""${dataStream}_ready := io.memStreams.stores($id).wdata.ready""")
      emit(src"""io.memStreams.stores($id).wdata.bits.zip(${dataStream}_data).foreach{case (wport, wdata) => wport := wdata(31,0) /*LSB is status bit*/}""")
      emit(src"""io.memStreams.stores($id).wdata.valid := ${dataStream}_valid""")
      emitGlobalWire(src"""val ${cmdStream}_data = Wire(UInt(97.W)) // TODO: What is width of burstcmdbus?""")
      emit(src"io.memStreams.stores($id).cmd.bits.addr := ${cmdStream}_data(63,0) // Field 0")
      emit(src"io.memStreams.stores($id).cmd.bits.size := ${cmdStream}_data(95,64) // Field 1")
      emit(src"io.memStreams.stores($id).cmd.valid :=  ${cmdStream}_valid // Field 2")
      emit(src"io.memStreams.stores($id).cmd.bits.isWr := ~${cmdStream}_data(96)")
      emit(src"io.memStreams.stores($id).cmd.bits.isSparse := 0.U")
      emit(src"${cmdStream}_ready := io.memStreams.stores($id).wdata.ready")
      emit(src"""${ackStream}_valid := io.memStreams.stores($id).wresp.valid""")
      emit(src"""io.memStreams.stores($id).wresp.ready := ${ackStream}_ready""")

    case FringeSparseStore(dram,cmdStream,ackStream) =>
      // Get parallelization of datastream
      val par = writersOf(cmdStream).head.node match {
        case Def(e@ParStreamWrite(_, _, ens)) => ens.length
        case _ => 1
      }
      Predef.assert(par == 1, s"Unsupported par '$par', only par=1 currently supported")

      val id = storesList.length
      storeParMapping = storeParMapping :+ s"StreamParInfo(${bitWidth(dram.tp.typeArguments.head)}, ${par})"
      storesList = storesList :+ dram

      val (addrLSB, addrWidth) = tupCoordinates(cmdStream.tp.typeArguments.head, "_1")
      val (dataLSB, dataWidth) = tupCoordinates(cmdStream.tp.typeArguments.head, "_2")
//      emitGlobalWire(src"""val ${cmdStream}_addrData = Wire(UInt(${addrWidth+dataWidth}.W))""")
//      emitGlobalWire(src"""val ${cmdStream}_addr = ${cmdStream}_addrData(${addrLSB+addrWidth-1}, ${addrLSB})""")
//      emitGlobalWire(src"""val ${cmdStream}_data = ${cmdStream}_addrData(${dataLSB+dataWidth-1}, ${dataLSB})""")

      emitGlobalWire(src"val ${cmdStream}_data = Wire(Vec($par, UInt(${addrWidth+dataWidth}.W)))")
      emit(src"""io.memStreams.stores($id).wdata.bits.zip(${cmdStream}_data).foreach{case (wport, wdata) => wport := wdata(${dataLSB+dataWidth-1}, ${dataLSB})}""")

      emit(src"""io.memStreams.stores($id).wdata.valid := ${cmdStream}_valid""")
      emit(src"io.memStreams.stores($id).cmd.bits.addr := ${cmdStream}_data(0)(${addrLSB+addrWidth-1}, ${addrLSB})  // TODO: Hardcoded 0 index")
      emit(src"io.memStreams.stores($id).cmd.bits.size := 1.U")
      emit(src"io.memStreams.stores($id).cmd.valid :=  ${cmdStream}_valid")
      emit(src"io.memStreams.stores($id).cmd.bits.isWr := 1.U")
      emit(src"io.memStreams.stores($id).cmd.bits.isSparse := 1.U")
      emit(src"${cmdStream}_ready := io.memStreams.stores($id).wdata.ready")
      emit(src"""${ackStream}_valid := io.memStreams.stores($id).wresp.valid""")
      emit(src"""io.memStreams.stores($id).wresp.ready := ${ackStream}_ready""")

    case _ => super.emitNode(lhs, rhs)
  }


  override protected def emitFileFooter() {

    withStream(getStream("Instantiator")) {
      emit("")
      emit(s"// Memory streams")
      emit(s"""val loadStreamInfo = List(${loadParMapping.mkString(",")}) """)
      emit(s"""val storeStreamInfo = List(${storeParMapping.mkString(",")}) """)
      emit(s"""val numArgIns_mem = ${loadsList.distinct.length} /*from loads*/ + ${storesList.distinct.length} /*from stores*/""")
    }

    withStream(getStream("IOModule")) {
      emit("// Memory Streams")
      emit(s"""val io_loadStreamInfo = List(${loadParMapping.mkString(",")}) """)
      emit(s"""val io_storeStreamInfo = List(${storeParMapping.mkString(",")}) """)
      emit(s"val io_numArgIns_mem = ${loadsList.distinct.length} /*from loads*/ + ${storesList.distinct.length} /*from stores*/")

    }

    super.emitFileFooter()
  }

}
