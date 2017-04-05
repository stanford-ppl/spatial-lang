package fringe

import util.HVec
import chisel3._
import chisel3.util._
import templates.Utils.log2Up

class FIFOArbiterWidthConvert(
  val win: List[Int],
  val vin: List[Int],
  val wout: Int,
  val vout: Int,
  val d: Int
) extends Module {

  val tagWidth = log2Up(numStreams)
  val numStreams = win.size

  val io = IO(new Bundle {
    val enq = Input(HVec.tabulate(numStreams) { i =>  Vec(vin(i), Bits(win(i).W))})
    val enqVld = Input(Vec(numStreams, Bool()))
    val full = Output(Vec(numStreams, Bool()))
    val deq = Output(Vec(vout, Bits(wout.W)))
    val deqVld = Input(Bool())
    val empty = Output(Bool())
    val forceTag = Flipped(Decoupled(UInt(tagWidth.W)))
    val tag = Output(UInt(tagWidth.W))
  })

  val tagFF = Module(new FF(tagWidth))
  tagFF.io.init := 0.U
  val tag = Mux(io.forceTag.valid, io.forceTag.bits, tagFF.io.out)

  // FIFOs
  if (numStreams > 0) {
    val fifos = List.tabulate(numStreams) { i =>
      val m = Module(new WidthConverterFIFO(win(i), vin(i), wout, vout, d))
      m.io.enq := io.enq(i)
      m.io.enqVld := io.enqVld(i)
      m.io.deqVld := io.deqVld & (tag === i.U)
      io.full(i) := m.io.full
      m
    }

    val enqSomething = io.enqVld.reduce{_|_}
    val allFifoEmpty = fifos.map { _.io.empty }.reduce{_&_}
    tagFF.io.enable := io.deqVld | (allFifoEmpty & enqSomething)

    val fifoValids = Mux(allFifoEmpty,
      io.enqVld,
      Vec(List.tabulate(numStreams) { i =>
        ~((~io.enqVld(i) & fifos(i).io.empty) | ((tag === i.U) & io.deqVld & ~io.enqVld(i) & fifos(i).io.almostEmpty))
      })
    )

    // Priority encoder and output interfaces
    val activeFifo = PriorityEncoder(fifoValids)
    tagFF.io.in := activeFifo

    val outMux = Module(new MuxVec(numStreams, vout, wout))
    outMux.io.ins := Vec(fifos.map {e => e.io.deq})
    outMux.io.sel := tag

    io.tag := tag
    io.deq := outMux.io.out
    io.empty := fifos.map {e => e.io.empty}.reduce{_&_}  // emptyMux.io.out
  } else { // Arbiter does nothing if there are no memstreams
    io.tag := 0.U(tagWidth.W)
    io.deq := Vec(List.tabulate(vout) { i => 0.U(wout.W) })
    io.empty := true.B
  }

}

