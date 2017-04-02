package spatial.codegen.chiselgen

import spatial.SpatialConfig
import spatial.SpatialExp
import scala.collection.mutable.HashMap

trait ChiselGenDRAM extends ChiselGenSRAM {
  val IR: SpatialExp
  import IR._

  var numLoads = 0
  var numStores = 0
  var loadParMapping = HashMap[Int, Int]() 
  var storeParMapping = HashMap[Int, Int]() 

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          lhs match {
              case Def(e: DRAMNew[_]) => s"x${lhs.id}_dram"
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

  private def findDenseConsumer(denseXfer: Exp[_]): Exp[_] = {
    /* The rdata_ready must be tied to the same enable signal for the consuming stage
       Currently, this means finding the last leaf node in the children of a 
       FringeDenseLoad's parent
    */
    val parent = parentOf(denseXfer).get
    val lastChild = childrenOf(parent).last
    childrenOf(lastChild).last

  }

  override protected def remap(tp: Staged[_]): String = tp match {
    case tp: DRAMType[_] => src"Array[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@DRAMNew(dims) => 
      if (argMapping(lhs) == (-1,-1,-1)) {
        throw new UnusedDRAMException(lhs, nameOf(lhs).getOrElse("noname"))
      }

    case GetDRAMAddress(dram) =>
      val id = argMapping(dram)._1
      emit(src"""val $lhs = io.argIns($id)""")

    case FringeDenseLoad(dram,cmdStream,dataStream) =>
      // Get parallelization of datastream
      val par = readersOf(dataStream).head.node match {
        case Def(e@ParStreamRead(strm, ens)) => ens.length
        case _ => 1
      }

      val id = numLoads
      loadParMapping += (id -> par)
      numLoads = numLoads + 1
      emitGlobal(src"""val ${childrenOf(childrenOf(parentOf(lhs).get).apply(1)).apply(1)}_enq = io.memStreams.loads(${id}).rdata.valid""")
      emit(src"""// Connect streams to ports on mem controller""")
      val allData = dram.tp.typeArguments.head match {
        case FixPtType(s,d,f) => if (spatialNeedsFPType(dram.tp.typeArguments.head)) {
            (0 until par).map{ i => src"""Utils.FixedPoint($s,$d,$f,io.memStreams.loads($id).rdata.bits($i))""" }.mkString(",")
          } else {
            (0 until par).map{ i => src"io.memStreams.loads($id).rdata.bits($i)" }.mkString(",")
          }
        case _ => (0 until par).map{ i => src"io.memStreams.loads($id).rdata.bits($i)" }.mkString(",")
      }
      emit(src"""val ${dataStream}_data = Vec(List($allData))""")
      emitGlobal(src"""val ${dataStream}_ready = io.memStreams.loads($id).rdata.valid""")
      val consumeReady = findDenseConsumer(lhs)
      emit(src"io.memStreams.loads($id).rdata.ready := ${consumeReady}_en // Contains stage enable, rdatavalid, and fifo status")
      emit(src"io.memStreams.loads($id).cmd.bits.addr := ${cmdStream}_data(96, 33) // Bits 33 to 96 are addr")
      emit(src"io.memStreams.loads($id).cmd.bits.size := ${cmdStream}_data(32,1) // Bits 1 to 32 are size command")
      emit(src"io.memStreams.loads($id).cmd.valid :=  ${cmdStream}_valid// LSB is enable, instead of pulser?? Reg(UInt(1.W), pulser.io.out)")
      emit(src"io.memStreams.loads($id).cmd.bits.isWr := ~${cmdStream}_data(0)")
      emitGlobal(src"val ${cmdStream}_ready = true.B // Assume cmd fifo will never fill up")


    case FringeDenseStore(dram,cmdStream,dataStream,ackStream) =>
      // Get parallelization of datastream
      val par = writersOf(dataStream).head.node match {
        case Def(e@ParStreamWrite(_, _, ens)) => ens.length
        case _ => 1
      }

      val id = numStores
      storeParMapping += (id -> par)
      numStores = numStores + 1
      // emitGlobal(src"""val ${childrenOf(childrenOf(parentOf(lhs).get).apply(1)).apply(1)}_enq = io.memStreams(${id}).rdata.valid""")
      emit(src"""// Connect streams to ports on mem controller""")
      val allData = (0 until par).map{ i => src"io.memStreams.stores($id).rdata.bits($i)" }.mkString(",")
      emitGlobal(src"val ${dataStream}_data = Wire(Vec($par, UInt(33.W)))")
      emitGlobal(src"""val ${dataStream}_en = Wire(Bool())""")
      emit(src"""io.memStreams.stores($id).wdata.bits.zip(${dataStream}_data).foreach{case (wport, wdata) => wport := wdata(32,1) /*LSB is status bit*/}""")
      emit(src"""io.memStreams.stores($id).wdata.valid := ${dataStream}_en""")
      val ackReady = findDenseConsumer(lhs)
      emit(src"""// CHISEL3 currently crashes because we connect a Bool to a Bool here......... WTF! io.memStreams.stores($id).wdata.ready := ${ackReady}_en // Contains stage enable, wdatavalid, and fifo status""")
      emit(src"io.memStreams.stores($id).cmd.bits.addr := ${cmdStream}_data(96, 33) // Bits 33 to 96 (AND BEYOND???) are addr")
      emit(src"io.memStreams.stores($id).cmd.bits.size := ${cmdStream}_data(32,1) // Bits 1 to 32 are size command")
      emit(src"io.memStreams.stores($id).cmd.valid :=  ${cmdStream}_valid")
      emit(src"io.memStreams.stores($id).cmd.bits.isWr := ~${cmdStream}_data(0)")
      emitGlobal(src"val ${cmdStream}_ready = true.B // Assume cmd fifo will never fill up")
      emitGlobal(src"""val ${dataStream}_ready = true.B // Assume cmd fifo will never fill up""")
      emitGlobal(src"""val ${ackStream}_ready = Wire(Bool())""")
      emit(src"""${ackStream}_ready := io.memStreams.stores($id).wresp  // Not really tested well""")
      emitGlobal(src"val ${ackStream}_data = 0.U // Definitely wrong signal")

    case FringeSparseLoad(dram,addrStream,dataStream) =>
      open(src"val $lhs = $addrStream.foreach{addr => ")
        emit(src"$dataStream.enqueue( $dram(addr) )")
      close("}")
      emit(src"$addrStream.clear()")

    case FringeSparseStore(dram,cmdStream,ackStream) =>
      open(src"val $lhs = $cmdStream.foreach{cmd => ")
        emit(src"$dram(cmd._2) = cmd._1 ")
        emit(src"$ackStream.enqueue(true)")
      close("}")
      emit(src"$cmdStream.clear()")

    case _ => super.emitNode(lhs, rhs)
  }


  override protected def emitFileFooter() {
    val loadsList = (0 until numLoads).map{ i => s"StreamParInfo(32, ${loadParMapping(i)})" }.mkString(",")
    val storesList = (0 until numStores).map{ i => s"StreamParInfo(32, ${storeParMapping(i)})" }.mkString(",")

    withStream(getStream("Instantiator")) {
      emit("")
      emit(s"// Memory streams")
      emit(s"""val numLoadStreams = ${numLoads}""")
      emit(s"""val numStoreStreams = ${numStores}""")
      emit(s"val loadStreamInfo = List(${loadsList}) ")
      emit(s"val storeStreamInfo = List(${storesList}) ")
      emit(s"""val numArgIns_mem = ${numLoads} + ${numStores}""")
    }

    withStream(getStream("IOModule")) {
      emit("// Tile Load")
      emit(s"val io_numLoadStreams = ${numLoads}")
      emit(s"val io_numStoreStreams = ${numStores}")
      emit(s"val io_loadStreamInfo = List(${loadsList}) ")
      emit(s"val io_storeStreamInfo = List(${storesList}) ")
      emit(s"val io_numArgIns_mem = ${numLoads} + ${numStores}")

    }

    super.emitFileFooter()
  }

}
