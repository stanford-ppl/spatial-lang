package templates

import chisel3._
import chisel3.util
import scala.math._



class FIFO(val pR: Int, val pW: Int, val depth: Int, val bitWidth: Int = 32) extends Module {
  def this(tuple: (Int, Int, Int)) = this(tuple._1, tuple._2, tuple._3)
  def this(p: Int, depth: Int) = this(p, p, depth)

  val io = IO( new Bundle {
    val in = Vec(pW, Input(UInt(bitWidth.W)))
    val out = Vec(pR, Output(UInt(bitWidth.W)))
    val enq = Input(Bool())
    val deq = Input(Bool())
    val empty = Output(Bool())
    val full = Output(Bool())
    val debug = new Bundle {
      val overwrite = Output(Bool())
      val overread = Output(Bool())
      val error = Output(Bool())
    }
  })

  // TODO: Assert pW and pR evenly divide p
  val p = scala.math.max(pW,pR)

  // Register for tracking number of elements in FIFO
  val elements = Module(new IncDincCtr(pW, pR, depth))
  elements.io.input.inc_en := io.enq
  elements.io.input.dinc_en := io.deq

  // Create physical mems
  val m = (0 until p).map{ i => Module(new Mem1D(depth/p, true, bitWidth))}

  // Create head and reader sub counters
  val subWriter = Module(new SingleCounter(1))
  val subReader = Module(new SingleCounter(1))
  subWriter.io.input.max := (p/pW).U
  subWriter.io.input.enable := io.enq
  subWriter.io.input.stride := 1.U
  subWriter.io.input.gap := 0.U
  subWriter.io.input.start := 0.U
  subWriter.io.input.reset := reset
  subWriter.io.input.saturate := false.B
  subReader.io.input.max := (p/pR).U
  subReader.io.input.enable := io.deq
  subReader.io.input.stride := 1.U
  subReader.io.input.gap := 0.U
  subReader.io.input.start := 0.U
  subReader.io.input.reset := reset
  subReader.io.input.saturate := false.B

  // Create head and reader counters
  val writer = Module(new SingleCounter(1))
  val reader = Module(new SingleCounter(1))
  writer.io.input.max := (depth/p).U
  writer.io.input.enable := io.enq & subWriter.io.output.done
  writer.io.input.stride := 1.U
  writer.io.input.gap := 0.U
  writer.io.input.start := 0.U
  writer.io.input.reset := reset
  writer.io.input.saturate := false.B
  reader.io.input.max := (depth/p).U
  reader.io.input.enable := io.deq & subReader.io.output.done
  reader.io.input.stride := 1.U
  reader.io.input.gap := 0.U
  reader.io.input.start := 0.U
  reader.io.input.reset := reset
  reader.io.input.saturate := false.B  

  // Connect enqer
  if (pW == pR) {
    m.zip(io.in).foreach { case (mem, data) => 
      mem.io.w.addr := writer.io.output.count(0)
      mem.io.w.data := data
      mem.io.w.en := io.enq
    }
  } else {
    (0 until pW).foreach { w_i => 
      (0 until (p / pW)).foreach { i => 
        m(w_i + i*pW).io.w.addr := writer.io.output.count(0)
        m(w_i + i*pW).io.w.data := io.in(w_i)
        m(w_i + i*pW).io.w.en := io.enq & (subWriter.io.output.count(0) === i.U)
      }
    }
  }

  // Connect deqper
  if (pW == pR) {
    m.zip(io.out).foreach { case (mem, data) => 
      mem.io.r.addr := reader.io.output.count(0)
      mem.io.r.en := io.deq
      data := mem.io.output.data
    }
  } else {
    (0 until pR).foreach { r_i => 
      val rSel = Wire(Vec( (p/pR), Bool()))
      val rData = Wire(Vec( (p/pR), UInt(32.W)))
      (0 until (p / pR)).foreach { i => 
        m(r_i + i*pR).io.r.addr := reader.io.output.count(0)
        m(r_i + i*pR).io.r.en := io.deq & (subReader.io.output.count(0) === i.U)
        rSel(i) := subReader.io.output.count(0) === i.U
        // if (i == 0) { // Strangeness from inc-then-read nuisance
        //   rSel((p/pR)-1) := subReader.io.output.count(0) === i.U
        // } else {
        //   rSel(i-1) := subReader.io.output.count(0) === i.U
        // }
        rData(i) := m(r_i + i*pR).io.output.data
      }
      io.out(r_i) := chisel3.util.PriorityMux(rSel, rData)
    }
  }

  // Check if there is data
  io.empty := elements.io.output.empty
  io.full := elements.io.output.full

  // Debug signals
  io.debug.overread := elements.io.output.overread
  io.debug.overwrite := elements.io.output.overwrite
  io.debug.error := elements.io.output.overwrite | elements.io.output.overread

  // // Old empty and error tracking
  // val ovW = Module(new SRFF())
  // val ovR = Module(new SRFF())
  // val www_c = writer.io.output.countWithoutWrap(0)*(p/pW).U + subWriter.io.output.count(0)
  // val w_c = writer.io.output.count(0)*(p/pW).U + subWriter.io.output.count(0)
  // val rww_c = reader.io.output.countWithoutWrap(0)*(p/pR).U + subReader.io.output.count(0)
  // val r_c = reader.io.output.count(0)*(p/pR).U + subReader.io.output.count(0)
  // val hasData = Module(new SRFF())
  // hasData.io.input.set := (w_c === r_c) & io.enq & !(ovR.io.output.data | ovW.io.output.data)
  // hasData.io.input.reset := (r_c + 1.U === www_c) & io.deq & !(ovR.io.output.data | ovW.io.output.data)
  // io.empty := !hasData.io.output.data

  // // Debugger
  // val overwrite = hasData.io.output.data & (w_c === r_c) & io.enq // TODO: Need to handle sub-counters
  // val fixed_overwrite = (www_c === r_c + 1.U) & io.deq
  // ovW.io.input.set := overwrite
  // ovW.io.input.reset := fixed_overwrite
  // io.debug.overwrite := ovW.io.output.data
  // val overread = !hasData.io.output.data & (w_c === r_c) & io.deq // TODO: Need to handle sub-counters
  // val fixed_overread = (w_c + 1.U === rww_c) & io.enq
  // ovR.io.input.set := overread
  // ovR.io.input.reset := fixed_overread
  // io.debug.overread := ovR.io.output.data

  // io.debug.error := ovR.io.output.data | ovW.io.output.data

}

class FILO(val pR: Int, val pW: Int, val depth: Int, val bitWidth: Int = 32) extends Module {
  def this(tuple: (Int, Int, Int)) = this(tuple._1, tuple._2, tuple._3)
  def this(p: Int, depth: Int) = this(p, p, depth)

  val io = IO( new Bundle {
    val in = Vec(pW, Input(UInt(bitWidth.W)))
    val out = Vec(pR, Output(UInt(bitWidth.W)))
    val push = Input(Bool())
    val pop = Input(Bool())
    val empty = Output(Bool())
    val full = Output(Bool())
    val debug = new Bundle {
      val overwrite = Output(Bool())
      val overread = Output(Bool())
      val error = Output(Bool())
    }
  })

  // TODO: Assert pW and pR evenly divide p
  val p = max(pW,pR)

  // Register for tracking number of elements in FILO
  val elements = Module(new IncDincCtr(pW, pR, depth))
  elements.io.input.inc_en := io.push
  elements.io.input.dinc_en := io.pop

  // Create physical mems
  val m = (0 until p).map{ i => Module(new Mem1D(depth/p, true, bitWidth))}

  // Create head and reader sub counters
  val subAccessor = Module(new SingleSCounter(1))
  subAccessor.io.input.max := (p).S
  subAccessor.io.input.enable := io.push | io.pop
  subAccessor.io.input.stride := Mux(io.push, pW.S, -pR.S)
  subAccessor.io.input.gap := 0.S
  subAccessor.io.input.start := 0.S
  subAccessor.io.input.reset := reset
  subAccessor.io.input.saturate := false.B
  val subAccessor_prev = Mux(subAccessor.io.output.count(0) - pR.S < 0.S, (p-pR).S, subAccessor.io.output.count(0) - pR.S)

  // Create head and reader counters
  val accessor = Module(new SingleSCounter(1))
  accessor.io.input.max := (depth/p).S
  accessor.io.input.enable := (io.push & subAccessor.io.output.done) | (io.pop & subAccessor_prev === 0.S)
  accessor.io.input.stride := Mux(io.push, 1.S, -1.S)
  accessor.io.input.gap := 0.S
  accessor.io.input.start := 0.S
  accessor.io.input.reset := reset
  accessor.io.input.saturate := false.B


  // Connect pusher
  if (pW == pR) {
    m.zip(io.in).foreach { case (mem, data) => 
      mem.io.w.addr := accessor.io.output.count(0).asUInt
      mem.io.w.data := data
      mem.io.w.en := io.push
    }
  } else {
    (0 until pW).foreach { w_i => 
      (0 until (p / pW)).foreach { i => 
        m(w_i + i*pW).io.w.addr := accessor.io.output.count(0).asUInt
        m(w_i + i*pW).io.w.data := io.in(w_i)
        m(w_i + i*pW).io.w.en := io.push & (subAccessor.io.output.count(0) === (i*pW).S)
      }
    }
  }

  // Connect popper
  if (pW == pR) {
    m.zip(io.out).foreach { case (mem, data) => 
      mem.io.r.addr := (accessor.io.output.count(0) - 1.S).asUInt
      mem.io.r.en := io.pop
      data := mem.io.output.data
    }
  } else {
    (0 until pR).foreach { r_i => 
      val rSel = Wire(Vec( (p/pR), Bool()))
      val rData = Wire(Vec( (p/pR), UInt(32.W)))
      (0 until (p / pR)).foreach { i => 
        m(r_i + i*pR).io.r.addr := (accessor.io.output.count(0) - 1.S).asUInt
        m(r_i + i*pR).io.r.en := io.pop & (subAccessor_prev === (i*pR).S)
        rSel(i) := subAccessor_prev === i.S
        // if (i == 0) { // Strangeness from inc-then-read nuisance
        //   rSel((p/pR)-1) := subReader.io.output.count(0) === i.U
        // } else {
        //   rSel(i-1) := subReader.io.output.count(0) === i.U
        // }
        rData(i) := m(r_i + i*pR).io.output.data
      }
      io.out(r_i) := chisel3.util.PriorityMux(rSel, rData)
    }
  }

  // Check if there is data
  io.empty := elements.io.output.empty
  io.full := elements.io.output.full

  // Debug signals
  io.debug.overread := elements.io.output.overread
  io.debug.overwrite := elements.io.output.overwrite
  io.debug.error := elements.io.output.overwrite | elements.io.output.overread

  // // Old empty and error tracking
  // val ovW = Module(new SRFF())
  // val ovR = Module(new SRFF())
  // val www_c = accessor.io.output.countWithoutWrap(0)*(p/pW).U + subAccessor.io.output.count(0)
  // val w_c = accessor.io.output.count(0)*(p/pW).U + subAccessor.io.output.count(0)
  // val rww_c = reader.io.output.countWithoutWrap(0)*(p/pR).U + subReader.io.output.count(0)
  // val r_c = reader.io.output.count(0)*(p/pR).U + subReader.io.output.count(0)
  // val hasData = Module(new SRFF())
  // hasData.io.input.set := (w_c === r_c) & io.push & !(ovR.io.output.data | ovW.io.output.data)
  // hasData.io.input.reset := (r_c + 1.U === www_c) & io.pop & !(ovR.io.output.data | ovW.io.output.data)
  // io.empty := !hasData.io.output.data

  // // Debugger
  // val overwrite = hasData.io.output.data & (w_c === r_c) & io.push // TODO: Need to handle sub-counters
  // val fixed_overwrite = (www_c === r_c + 1.U) & io.pop
  // ovW.io.input.set := overwrite
  // ovW.io.input.reset := fixed_overwrite
  // io.debug.overwrite := ovW.io.output.data
  // val overread = !hasData.io.output.data & (w_c === r_c) & io.pop // TODO: Need to handle sub-counters
  // val fixed_overread = (w_c + 1.U === rww_c) & io.push
  // ovR.io.input.set := overread
  // ovR.io.input.reset := fixed_overread
  // io.debug.overread := ovR.io.output.data

  // io.debug.error := ovR.io.output.data | ovW.io.output.data

}
