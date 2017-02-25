package example

import chisel3._
import chisel3.util._
import chisel3.core.{Direction, Input}

class MemoryStreamer(
  val w: Int,
  val d: Int,
  val v: Int
) extends Module {

  def connect[T<:Data](sink: ReadyValidIO[T], src: ReadyValidIO[T]) {
    sink.bits := src.bits
    sink.valid := src.valid
    src.ready := sink.ready
  }

  def widthConvertFifo(inWidth: Int, outWidth: Int, depth: Int) = {
    if (inWidth < outWidth) {
      Predef.assert(outWidth % inWidth == 0, s"ERROR: Width conversion attempted between widths that are not multiples (in: $inWidth, out: $outWidth)")
     val w = inWidth


    } else {
      Predef.assert(inWidth % outWidth == 0, s"ERROR: Width conversion attempted between widths that are not multiples (in: $inWidth, out: $outWidth)")

    }
  }

  val wordSizeBytes = w/8

  val io = IO(new Bundle {
    val done = Output(Bool())
    val last = Input(Bool())
    val accel = new MemoryStream(w, v) // input
    val fringe = Flipped(new MemoryStream(w, v)) // output
  })

  // Command:  accel -> fringe
  connect(io.fringe.cmd, io.accel.cmd)

  // Wdata: accel -> fringe
  // Passing wdata right through. Eventually we will perform
  // word width conversion here
  // Rdata: accel -> convertWidth_from_w_to_512 -> fringe
  connect(io.fringe.wdata, io.accel.wdata)

  // Rdata: fringe -> accel
  // Passing rdata right through. Eventually we will perform
  // word width conversion here
  // Rdata: fringe -> convertWidth_from_512_to_w -> accel
  connect(io.accel.rdata, io.fringe.rdata)

  // Size FIFO, counters
  val sizeIssuedFifo = Module(new FIFOCore(1+w, d, v))
  val sizeIssuedFifoConfig = Wire(new FIFOOpcode(d, v))
  sizeIssuedFifoConfig.chainRead := UInt(1)
  sizeIssuedFifoConfig.chainWrite := UInt(1)
  sizeIssuedFifo.io.config := sizeIssuedFifoConfig

  sizeIssuedFifo.io.enq(0) := Cat(io.last, io.accel.cmd.bits.size)
  sizeIssuedFifo.io.enqVld := io.accel.cmd.valid

  // Burst received counter
  val receivedCounter = Module(new Counter(w))
  receivedCounter.io.max := sizeIssuedFifo.io.deq(0)(w-1, 0)
  receivedCounter.io.stride := UInt(1)
  receivedCounter.io.reset := UInt(0)
  receivedCounter.io.saturate := UInt(0)
  receivedCounter.io.enable := io.fringe.rdata.valid
  sizeIssuedFifo.io.deqVld := receivedCounter.io.done

  io.done := sizeIssuedFifo.io.deq(0)(w) & receivedCounter.io.done
}
