package templates

import chisel3._
import chisel3.util
import chisel3.util.Mux1H
import scala.math._



class FIFO(val pR: Int, val pW: Int, val depth: Int, val numWriters: Int, val numReaders: Int, val bitWidth: Int = 32) extends Module {
  def this(tuple: (Int, Int, Int, Int, Int)) = this(tuple._1, tuple._2, tuple._3, tuple._4, tuple._5)
  def this(p: Int, depth: Int) = this(p, p, 1, 1, depth)

  val io = IO( new Bundle {
    val in = Vec(pW*numWriters, Input(UInt(bitWidth.W)))
    val out = Vec(pR*numReaders, Output(UInt(bitWidth.W)))
    val enq = Vec(numWriters, Input(Bool()))
    val deq = Vec(numReaders, Input(Bool()))
    val numel = Output(UInt(32.W))
    val almostEmpty = Output(Bool())
    val almostFull = Output(Bool())
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

  val enq_options = Vec((0 until numWriters).map{ q => io.enq(q)}.toList)
  val deq_options = Vec((0 until numReaders).map{ q => io.deq(q)}.toList)

  // Register for tracking number of elements in FIFO
  val elements = Module(new IncDincCtr(pW, pR, depth))
  elements.io.input.inc_en := enq_options.reduce{_|_}
  elements.io.input.dinc_en := deq_options.reduce{_|_}

  // Create physical mems
  val m = (0 until p).map{ i => Module(new Mem1D(depth/p, true, bitWidth))}

  // Create head and reader sub counters
  val subWriter = Module(new SingleCounter(1))
  val subReader = Module(new SingleCounter(1))
  subWriter.io.input.stop := (p/pW).S
  subWriter.io.input.enable := enq_options.reduce{_|_}
  subWriter.io.input.stride := 1.S
  subWriter.io.input.gap := 0.S
  subWriter.io.input.start := 0.S
  subWriter.io.input.reset := reset
  subWriter.io.input.saturate := false.B
  subReader.io.input.stop := (p/pR).S
  subReader.io.input.enable := deq_options.reduce{_|_}
  subReader.io.input.stride := 1.S
  subReader.io.input.gap := 0.S
  subReader.io.input.start := 0.S
  subReader.io.input.reset := reset
  subReader.io.input.saturate := false.B

  // Create head and reader counters
  val writer = Module(new SingleCounter(1))
  val reader = Module(new SingleCounter(1))
  writer.io.input.stop := (depth/p).S
  writer.io.input.enable := enq_options.reduce{_|_} & subWriter.io.output.done
  writer.io.input.stride := 1.S
  writer.io.input.gap := 0.S
  writer.io.input.start := 0.S
  writer.io.input.reset := reset
  writer.io.input.saturate := false.B
  reader.io.input.stop := (depth/p).S
  reader.io.input.enable := deq_options.reduce{_|_} & subReader.io.output.done
  reader.io.input.stride := 1.S
  reader.io.input.gap := 0.S
  reader.io.input.start := 0.S
  reader.io.input.reset := reset
  reader.io.input.saturate := false.B  

  // Connect enqer
  if (pW == pR) {
    m.zipWithIndex.foreach { case (mem, i) => 
      val data_options = Vec((0 until numWriters).map{ q => io.in(q*pW + i)}.toList)
      mem.io.w.addr := writer.io.output.count(0).asUInt
      mem.io.w.data := Mux1H(enq_options, data_options)
      mem.io.w.en := enq_options.reduce{_|_}
    }
  } else {
    (0 until pW).foreach { w_i => 
      (0 until (p / pW)).foreach { i => 
        val data_options = Vec((0 until numWriters).map{ q => io.in(q*pW + w_i)}.toList)
        m(w_i + i*pW).io.w.addr := writer.io.output.count(0).asUInt
        m(w_i + i*pW).io.w.data := Mux1H(enq_options, data_options)
        m(w_i + i*pW).io.w.en := enq_options.reduce{_|_} & (subWriter.io.output.count(0) === i.S)
      }
    }
  }

  // Connect deqper
  if (pW == pR) {
    m.zipWithIndex.foreach { case (mem, i) => 
      mem.io.r.addr := reader.io.output.count(0).asUInt
      mem.io.r.en := deq_options.reduce{_|_}
      io.out(i) := mem.io.output.data
    }
  } else {
    (0 until pR).foreach { r_i => 
      val rSel = Wire(Vec( (p/pR), Bool()))
      val rData = Wire(Vec( (p/pR), UInt(32.W)))
      (0 until (p / pR)).foreach { i => 
        m(r_i + i*pR).io.r.addr := reader.io.output.count(0).asUInt
        m(r_i + i*pR).io.r.en := deq_options.reduce{_|_} & (subReader.io.output.count(0) === i.S)
        rSel(i) := subReader.io.output.count(0) === i.S
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
  io.almostEmpty := elements.io.output.almostEmpty
  io.almostFull := elements.io.output.almostFull
  io.numel := elements.io.output.numel.asUInt

  // Debug signals
  io.debug.overread := elements.io.output.overread
  io.debug.overwrite := elements.io.output.overwrite
  io.debug.error := elements.io.output.overwrite | elements.io.output.overread

  var wId = 0
  def connectEnqPort(data: Vec[UInt], en: Bool): Unit = {
    (0 until data.length).foreach{ i => 
      io.in(wId*pW + i) := data(i)
    }
    io.enq(wId) := en
    wId += 1
  }

  var rId = 0
  def connectDeqPort(en: Bool): Vec[UInt] = {
    io.deq(rId) := en
    rId += 1
    io.out
  }

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

class FILO(val pR: Int, val pW: Int, val depth: Int, val numWriters: Int, val numReaders: Int, val bitWidth: Int = 32) extends Module {
  def this(tuple: (Int, Int, Int, Int, Int)) = this(tuple._1, tuple._2, tuple._3, tuple._4, tuple._5)
  def this(p: Int, depth: Int) = this(p, p, 1, 1, depth)

  val io = IO( new Bundle {
    val in = Vec(pW*numWriters, Input(UInt(bitWidth.W)))
    val out = Vec(pR*numReaders, Output(UInt(bitWidth.W)))
    val push = Vec(numWriters, Input(Bool()))
    val pop = Vec(numReaders, Input(Bool()))
    val empty = Output(Bool())
    val full = Output(Bool())
    val almostEmpty = Output(Bool())
    val almostFull = Output(Bool())
    val numel = Output(UInt(32.W)) // TODO: Should probably be signed fixpt
    val debug = new Bundle {
      val overwrite = Output(Bool())
      val overread = Output(Bool())
      val error = Output(Bool())
    }
  })

  // TODO: Assert pW and pR evenly divide p
  val p = max(pW,pR)

  val push_options = Vec((0 until numWriters).map{ q => io.push(q)}.toList)
  val pop_options = Vec((0 until numReaders).map{ q => io.pop(q)}.toList)

  // Register for tracking number of elements in FILO
  val elements = Module(new IncDincCtr(pW, pR, depth))
  elements.io.input.inc_en := push_options.reduce{_|_}
  elements.io.input.dinc_en := pop_options.reduce{_|_}

  // Create physical mems
  val m = (0 until p).map{ i => Module(new Mem1D(depth/p, true, bitWidth))}

  // Create head and reader sub counters
  val subAccessor = Module(new SingleSCounter(1))
  subAccessor.io.input.stop := (p).S
  subAccessor.io.input.enable := push_options.reduce{_|_} | pop_options.reduce{_|_}
  subAccessor.io.input.stride := Mux(push_options.reduce{_|_}, pW.S, -pR.S)
  subAccessor.io.input.gap := 0.S
  subAccessor.io.input.start := 0.S
  subAccessor.io.input.reset := reset
  subAccessor.io.input.saturate := false.B
  val subAccessor_prev = Mux(subAccessor.io.output.count(0) - pR.S < 0.S, (p-pR).S, subAccessor.io.output.count(0) - pR.S)

  // Create head and reader counters
  val accessor = Module(new SingleSCounter(1))
  accessor.io.input.stop := (depth/p).S
  accessor.io.input.enable := (push_options.reduce{_|_} & subAccessor.io.output.done) | (pop_options.reduce{_|_} & subAccessor_prev === 0.S)
  accessor.io.input.stride := Mux(push_options.reduce{_|_}, 1.S, -1.S)
  accessor.io.input.gap := 0.S
  accessor.io.input.start := 0.S
  accessor.io.input.reset := reset
  accessor.io.input.saturate := false.B


  // Connect pusher
  if (pW == pR) {
    m.zipWithIndex.foreach { case (mem, i) => 
      val data_options = Vec((0 until numWriters).map{ q => io.in(q*pW + i)}.toList)
      mem.io.w.addr := accessor.io.output.count(0).asUInt
      mem.io.w.data := Mux1H(push_options, data_options)
      mem.io.w.en := push_options.reduce{_|_}
    }
  } else {
    (0 until pW).foreach { w_i => 
      (0 until (p / pW)).foreach { i => 
        val data_options = Vec((0 until numWriters).map{ q => io.in(q*pW + w_i)}.toList)
        m(w_i + i*pW).io.w.addr := accessor.io.output.count(0).asUInt
        m(w_i + i*pW).io.w.data := Mux1H(push_options, data_options)
        m(w_i + i*pW).io.w.en := push_options.reduce{_|_} & (subAccessor.io.output.count(0) === (i*pW).S)
      }
    }
  }

  // Connect popper
  if (pW == pR) {
    m.zipWithIndex.foreach { case (mem, i) => 
      mem.io.r.addr := (accessor.io.output.count(0) - 1.S).asUInt
      mem.io.r.en := pop_options.reduce{_|_}
      io.out(i) := mem.io.output.data
    }
  } else {
    (0 until pR).foreach { r_i => 
      val rSel = Wire(Vec( (p/pR), Bool()))
      val rData = Wire(Vec( (p/pR), UInt(32.W)))
      (0 until (p / pR)).foreach { i => 
        m(r_i + i*pR).io.r.addr := (accessor.io.output.count(0) - 1.S).asUInt
        m(r_i + i*pR).io.r.en := pop_options.reduce{_|_} & (subAccessor_prev === (i*pR).S)
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
  io.almostEmpty := elements.io.output.almostEmpty
  io.almostFull := elements.io.output.almostFull
  io.numel := elements.io.output.numel.asUInt()

  // Debug signals
  io.debug.overread := elements.io.output.overread
  io.debug.overwrite := elements.io.output.overwrite
  io.debug.error := elements.io.output.overwrite | elements.io.output.overread

  var wId = 0
  def connectPushPort(data: Vec[UInt], en: Bool): Unit = {
    (0 until data.length).foreach{ i => 
      io.in(wId*pW + i) := data(i)
    }
    io.push(wId) := en
    wId += 1
  }

  var rId = 0
  def connectPopPort(en: Bool): Vec[UInt] = {
    io.pop(rId) := en
    rId += 1
    io.out
  }
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
