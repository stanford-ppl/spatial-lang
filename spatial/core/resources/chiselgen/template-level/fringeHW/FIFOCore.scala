package fringe

import chisel3._
import chisel3.util.{log2Ceil, isPow2}
import templates.Utils.log2Up


/**
 * FIFO config register format
 */
case class FIFOOpcode(val d: Int, val v: Int) extends Bundle {
  def roundUpDivide(num: Int, divisor: Int) = (num + divisor - 1) / divisor

  var chainWrite = Bool()
  var chainRead = Bool()

  override def cloneType(): this.type = {
    new FIFOOpcode(d, v).asInstanceOf[this.type]
  }
}

abstract class FIFOBase(val w: Int, val d: Int, val v: Int) extends Module {
  val io = IO(new Bundle {
    val enq = Input(Vec(v, Bits(w.W)))
    val enqVld = Input(Bool())
    val deq = Output(Vec(v, Bits(w.W)))
    val deqVld = Input(Bool())
    val full = Output(Bool())
    val empty = Output(Bool())
    val almostFull = Output(Bool())
    val almostEmpty = Output(Bool())
    val config = Input(new FIFOOpcode(d, v))
  })

  val addrWidth = log2Up(d/v)
  val bankSize = d/v

  // Check for sizes and v
  Predef.assert(d%v == 0, s"Unsupported FIFO size ($d)/banking($v) combination; $d must be a multiple of $v")
  Predef.assert(isPow2(v), s"Unsupported banking number $v; must be a power-of-2")
  Predef.assert(isPow2(d), s"Unsupported FIFO size $d; must be a power-of-2")

  // Create size register
  val sizeUDC = Module(new UpDownCtr(log2Up(d+1)))
  val size = sizeUDC.io.out
  val remainingSlots = d.U - size
  val nextRemainingSlots = d.U - sizeUDC.io.nextInc

  val strideInc = Mux(io.config.chainWrite, 1.U, v.U)
  val strideDec = Mux(io.config.chainRead, 1.U, v.U)

  // FIFO is full if #rem elements (d - size) < strideInc
  // FIFO is emty if elements (size) < strideDec
  // FIFO is almostFull if the next enqVld will make it full
  val empty = size < strideDec
  val almostEmpty = sizeUDC.io.nextDec < strideDec
  val full = remainingSlots < strideInc
  val almostFull = nextRemainingSlots < strideInc

  sizeUDC.io.initval := 0.U
  sizeUDC.io.max := d.U
  sizeUDC.io.init := 0.U
  sizeUDC.io.strideInc := strideInc
  sizeUDC.io.strideDec := strideDec
  sizeUDC.io.init := 0.U

  val writeEn = io.enqVld & ~full
  val readEn = io.deqVld & ~empty
  sizeUDC.io.inc := writeEn
  sizeUDC.io.dec := readEn

  io.empty := empty
  io.almostEmpty := almostEmpty
  io.full := full
  io.almostFull := almostFull
}

class FIFOCounter(override val d: Int, override val v: Int) extends FIFOBase(1, d, v) {
  io.deq.foreach { d => d := ~empty }
}

class FIFOCore(override val w: Int, override val d: Int, override val v: Int) extends FIFOBase(w, d, v) {
  // Create wptr (tail) counter chain
  val wptrConfig = Wire(new CounterChainOpcode(log2Up(bankSize+1), 2, 0, 0))
  wptrConfig.chain(0) := io.config.chainWrite
  (0 until 2) foreach { i => i match {
      case 1 => // Localaddr: max = bankSize, stride = 1
        val cfg = wptrConfig.counterOpcode(i)
        cfg.max := bankSize.U
        cfg.stride := 1.U
        cfg.maxConst := true.B
        cfg.strideConst := true.B
      case 0 => // Bankaddr: max = v, stride = 1
        val cfg = wptrConfig.counterOpcode(i)
        cfg.max := v.U
        cfg.stride := 1.U
        cfg.maxConst := true.B
        cfg.strideConst := true.B
  }}
  val wptr = Module(new CounterChainCore(log2Up(bankSize+1), 2, 0, 0))
  wptr.io.enable(0) := writeEn & io.config.chainWrite
  wptr.io.enable(1) := writeEn
  wptr.io.config := wptrConfig
  val tailLocalAddr = wptr.io.out(1)
  val tailBankAddr = wptr.io.out(0)


  // Create rptr (head) counter chain
  val rptrConfig = Wire(new CounterChainOpcode(log2Up(bankSize+1), 2, 0, 0))
  rptrConfig.chain(0) := io.config.chainRead
  (0 until 2) foreach { i => i match {
      case 1 => // Localaddr: max = bankSize, stride = 1
        val cfg = rptrConfig.counterOpcode(i)
        cfg.max := bankSize.U
        cfg.stride := 1.U
        cfg.maxConst := true.B
        cfg.strideConst := true.B
      case 0 => // Bankaddr: max = v, stride = 1
        val cfg = rptrConfig.counterOpcode(i)
        cfg.max := v.U
        cfg.stride := 1.U
        cfg.maxConst := true.B
        cfg.strideConst := true.B
    }}
  val rptr = Module(new CounterChainCore(log2Up(bankSize+1), 2, 0, 0))
  rptr.io.enable(0) := readEn & io.config.chainRead
  rptr.io.enable(1) := readEn
  rptr.io.config := rptrConfig
  val headLocalAddr = rptr.io.out(1)
  val nextHeadLocalAddr = Mux(io.config.chainRead, Mux(rptr.io.done(0), rptr.io.next(1), rptr.io.out(1)), rptr.io.next(1))
  val headBankAddr = rptr.io.out(0)
  val nextHeadBankAddr = rptr.io.next(0)

  // Backing SRAM
  val mems = List.fill(v) { Module(new SRAM(w, bankSize)) }
  mems.zipWithIndex.foreach { case (m, i) =>
    // Read address
    m.io.raddr := Mux(readEn, nextHeadLocalAddr, headLocalAddr)

    // Write address
    m.io.waddr := tailLocalAddr

    // Write data
    val wdata = i match {
      case 0 => io.enq(i)
      case _ => Mux(io.config.chainWrite, io.enq(0), io.enq(i))
    }
    m.io.wdata := wdata

    // Write enable
    val wen = Mux(io.config.chainWrite,
                    io.enqVld & tailBankAddr === i.U,
                    io.enqVld)
    m.io.wen := wen

    // Read data output
    val rdata = i match {
      case 0 =>
        val rdata0Mux = Module(new MuxN(v, w))
        val addrFF = Module(new FF(log2Ceil(v)))
        addrFF.io.in := Mux(readEn, nextHeadBankAddr, headBankAddr)
        addrFF.io.enable := true.B

        rdata0Mux.io.ins := Vec(mems.map {_.io.rdata })
        rdata0Mux.io.sel := Mux(io.config.chainRead, addrFF.io.out, 0.U)
        rdata0Mux.io.out
      case _ =>
        m.io.rdata
    }
    io.deq(i) := rdata
  }
}

//class FIFO(val w: Int, val d: Int, val v: Int, val inst: FIFOConfig) extends ConfigurableModule[FIFOOpcode] {
//  val addrWidth = log2Ceil(d/v)
//  val bankSize = d/v
//
//  // Check for sizes and v
//  Predef.assert(d%v == 0, s"Unsupported FIFO size ($d)/banking($v) combination; $d must be a multiple of $v")
//  Predef.assert(isPow2(v), s"Unsupported banking number $v; must be a power-of-2")
//  Predef.assert(isPow2(d), s"Unsupported FIFO size $d; must be a power-of-2")
//
//  val io = new ConfigInterface {
//    val config_enable = Input(Bool())
//    val enq = Vec.fill(v) { Bits(INPUT, width = w) }
//    val enqVld = Input(Bool())
//    val deq = Vec.fill(v) { Bits(OUTPUT, width = w) }
//    val deqVld = Input(Bool())
//    val full = Output(Bool())
//    val empty = Output(Bool())
//  }
//
//  val configType = FIFOOpcode(d, v)
//  val configIn = FIFOOpcode(d, v)
//  val configInit = FIFOOpcode(d, v, Some(inst))
//  val config = Reg(configType, configIn, configInit)
//  when (io.config_enable) {
//    configIn := configType.cloneType().fromBits(Fill(configType.getWidth, io.config_data))
//  } .otherwise {
//    configIn := config
//  }
//
//  val fifo = Module(new FIFOCore(w, d, v))
//  fifo.io.chainWrite := config.chainWrite
//  fifo.io.chainRead := config.chainRead
//  fifo.io.enq := io.enq
//  fifo.io.enqVld := io.enqVld
//  fifo.io.deqVld := io.deqVld
//  io.deq := fifo.io.deq
//  io.full := fifo.io.full
//  io.empty := fifo.io.empty
//}


