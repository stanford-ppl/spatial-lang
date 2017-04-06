package fringe

import chisel3._
import chisel3.util._

/**
 * WidthConverterFIFO: Convert a stream of width w1 bits to
 * a stream of width w2 bits
 * @param win: Input word width (bits)
 * @param vin: Input vector length
 * @param wout: Output word width
 * @param vout: Out vector length
 * @param d: FIFO depth
 */
class WidthConverterFIFO(val win: Int, val vin: Int, val wout: Int, val vout: Int, val d: Int) extends Module {
  val io = IO(new Bundle {
    val enq = Input(Vec(vin, Bits(win.W)))
    val enqVld = Input(Bool())
    val deq = Output(Vec(vout, Bits(wout.W)))
    val deqVld = Input(Bool())
    val full = Output(Bool())
    val empty = Output(Bool())
    val almostEmpty = Output(Bool())
    val almostFull = Output(Bool())
  })

  def convertVec(inVec: Vec[UInt], outw: Int, outv: Int) = {
    // 1. Cat everything
    val unifiedUInt = inVec.reverse.reduce { Cat(_,_) }

    // 2. Split apart
    val out = Vec(List.tabulate(outv) { i =>
      unifiedUInt(i*outw+outw-1, i*outw)
    })

    out
  }

  /**
   * Queue is full if no new element of 'inWidth' can be enqueued
   * Queue is empty if no element of 'outWidth' can be dequeued
   */
  val inWidth = win * vin
  val outWidth = wout * vout

  if (inWidth < outWidth) {
    Predef.assert(outWidth % inWidth == 0, s"ERROR: Width conversion attempted between widths that are not multiples (in: $inWidth, out: $outWidth)")
    val v = outWidth / inWidth
    val fifo = Module(new FIFOCore(inWidth, d, v))
    val fifoConfig = Wire(new FIFOOpcode(d, v))
    fifoConfig.chainWrite := 1.U
    fifoConfig.chainRead := 0.U
    fifo.io.config := fifoConfig
    fifo.io.enq(0) := io.enq.reverse.reduce { Cat(_,_) }
    fifo.io.enqVld := io.enqVld
    io.full := fifo.io.full
    io.empty := fifo.io.empty
    io.almostEmpty := fifo.io.almostEmpty
    io.almostFull := fifo.io.almostFull
    io.deq := convertVec(fifo.io.deq, wout, vout)
    fifo.io.deqVld := io.deqVld
  } else if (inWidth > outWidth) {
    Predef.assert(inWidth % outWidth == 0, s"ERROR: Width conversion attempted between widths that are not multiples (in: $inWidth, out: $outWidth)")
    val v = inWidth / outWidth
    val fifo = Module(new FIFOCore(outWidth, d, v))
    val fifoConfig = Wire(new FIFOOpcode(d, v))
    fifoConfig.chainWrite := 0.U
    fifoConfig.chainRead := 1.U
    fifo.io.config := fifoConfig
    io.full := fifo.io.full
    io.empty := fifo.io.empty
    io.almostEmpty := fifo.io.almostEmpty
    io.almostFull := fifo.io.almostFull

    fifo.io.enq := convertVec(io.enq, outWidth, v)
    fifo.io.enqVld := io.enqVld


    io.deq := convertVec(Vec(fifo.io.deq(0)), wout, vout)
    fifo.io.deqVld := io.deqVld
  } else {
    val fifo = Module(new FIFOCore(win, d, vin))
    val fifoConfig = Wire(new FIFOOpcode(d, vin))
    fifoConfig.chainWrite := 0.U
    fifoConfig.chainRead := 0.U
    fifo.io.config := fifoConfig
    io.full := fifo.io.full
    io.empty := fifo.io.empty
    io.almostEmpty := fifo.io.almostEmpty
    io.almostFull := fifo.io.almostFull

    fifo.io.enq := io.enq
    fifo.io.enqVld := io.enqVld

    io.deq := fifo.io.deq
    fifo.io.deqVld := io.deqVld

  }
}
