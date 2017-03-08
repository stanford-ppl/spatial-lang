package fringe

import chisel3._
import chisel3.util._

class FIFOArbiter(
  val w: Int,
  val d: Int,
  val v: Int,
  val numStreams: Int
) extends Module {

  val wordSizeBytes = w/8
  val tagWidth = log2Up(numStreams)

  val io = IO(new Bundle {
    val enq = Input(Vec(numStreams, Vec(v, Bits(w.W))))
    val enqVld = Input(Vec(numStreams, Bool()))
    val full = Output(Vec(numStreams, Bool()))
    val deq = Output(Vec(v, Bits(w.W)))
    val deqVld = Input(Bool())
    val empty = Output(Bool())
    val tag = Output(UInt(tagWidth.W))
    val config = Input(new FIFOOpcode(d, v))
  })

  val tagFF = Module(new FF(tagWidth))
  tagFF.io.init := 0.U
  val tag = tagFF.io.out

  // FIFOs
  if (numStreams > 0) {
    val fifos = List.tabulate(numStreams) { i =>
      val m = Module(new FIFOCore(w, d, v))
      val fifoConfig = Wire(new FIFOOpcode(d, v))
      fifoConfig.chainRead := io.config.chainRead
      fifoConfig.chainWrite := io.config.chainWrite
      m.io.config := fifoConfig
      m.io.enq := io.enq(i)
      m.io.enqVld := io.enqVld(i)
      m.io.deqVld := io.deqVld & (tag === i.U)
      io.full(i) := m.io.full
      m
    }
    val enq = io.enqVld.reduce{_|_}
    tagFF.io.enable := Reg(UInt(1.W), io.deqVld) | (fifos.map { _.io.empty }.reduce {_&_} & Reg(Bool(), enq))

    // Priority encoder and output interfaces
    val fifoValids = fifos.map { ~_.io.empty }
    val activeFifo = PriorityEncoder(fifoValids)
    tagFF.io.in := activeFifo

    val outMux = Module(new MuxVec(numStreams, v, w))
  //  outMux.io.ins := Vec(fifos.map {e => Reg(e.io.deq.cloneType, e.io.deq)})
    outMux.io.ins := Vec(fifos.map {e => e.io.deq})
    outMux.io.sel := tag

//    val emptyMux = Module(new MuxN(numStreams, w))
//  //  emptyMux.io.ins := Vec(fifos.map {e => Reg(UInt(1.W), e.io.empty)})
//    emptyMux.io.ins := Vec(fifos.map {e => e.io.empty})
//    emptyMux.io.sel := tag

    io.tag := tag
    io.deq := outMux.io.out
    io.empty := fifos.map {e => e.io.empty}.reduce{_&_}  // emptyMux.io.out
  } else { // Arbiter does nothing if there are no memstreams
    io.tag := 0.U(tagWidth.W)
    io.deq := Vec(v, 0.U(w.W))
    io.empty := true.B
  }

}

