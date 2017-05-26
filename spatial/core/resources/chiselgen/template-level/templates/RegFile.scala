package templates

import chisel3._
import templates.Utils.log2Up
import chisel3.util.{MuxLookup, Mux1H}
import Utils._


/*
           Registers Layout                                       
                                                  
        0 -> 1 -> 2 ->  3                                
                                                  
        4 -> 5 -> 6 ->  7                                
                                                  
        8 -> 9 -> 10 -> 11                           
                                                  
                                                  
                                                  
                                                  
                                                  
                                                  
                                                  
                                                  
                                                  
                                                  
                                                  
*/

// This exposes all registers as output ports now

class ShiftRegFile(val dims: List[Int], val stride: Int, val wPar: Int, val isBuf: Boolean, val bitWidth: Int) extends Module {

  def this(tuple: (List[Int], Int, Int, Boolean, Int)) = this(tuple._1, tuple._2, tuple._3, tuple._4, tuple._5)
  val io = IO(new Bundle { 
    // Signals for dumping data from one buffer to next
    val dump_data = Vec(dims.reduce{_*_}, Input(UInt(bitWidth.W)))
    val dump_en = Input(Bool())

    val data_in  = Vec(wPar*dims.last, Input(UInt(bitWidth.W))) // TODO: Should probalby use stride, not wpar
    val w_rowAddr   = Vec(wPar*dims.last, Input(UInt(32.W))) // TODO: optimize later
    val w_colAddr   = Vec(wPar*dims.last, Input(UInt(32.W))) // TODO: optimize later
    // val w_rowAddr   = Vec(wPar*dims.last, Input(UInt(log2Up(((dims.head+stride-1)/stride)*stride + 1).W))) // TODO: Not sure about math for 3+ dims
    // val w_colAddr   = Vec(wPar*dims.last, Input(UInt(log2Up(((dims.head+stride-1)/stride)*stride + 1).W))) // TODO: Not sure about math for 3+ dims
    val w_en     = Vec(wPar*dims.last, Input(Bool()))
    val shift_en = Vec(dims.last, Input(Bool()))
    val reset    = Input(Bool())
    val data_out = Vec(dims.reduce{_*_}, Output(UInt(bitWidth.W)))
  })
  
  // if (!isBuf) {io.dump_en := false.B}

  // val size_rounded_up = ((dims.head+stride-1)/stride)*stride // Unlike shift reg, for shift reg file it is user's problem if width does not match (no functionality guarantee)
  val registers = List.fill(dims.reduce{_*_})(Reg(UInt(bitWidth.W))) // Note: Can change to use FF template
  for (i <- 0 until dims.reduce{_*_}) {
    io.data_out(i) := registers(i)
  }

  if (!isBuf) {
    when(io.reset) {
      for (i <- 0 until (dims.last*dims.head)) {
        registers(i) := 0.U(bitWidth.W)
      }
    } .elsewhen(io.shift_en.reduce{_|_}) {
      for (i <- 0 until (stride)) {
        for (row <- 0 until dims.last) {
          registers(row*dims.head + i) := Mux(io.shift_en(row), io.data_in(row), registers(row*dims.head + i))
        }
      }
      for (i <- stride until (dims.head)) {
        for (row <- 0 until dims.last) {
          registers(row*dims.head + i) := Mux(io.shift_en(row), registers(row*dims.head + i-stride), registers(row*dims.head + i))
        }
      }
    } .elsewhen(io.w_en.reduce{_|_}) { // TODO: Assume we only write to one place at a time
      val activeEn = io.w_en.reduce{_|_}
      val activeRowAddr = chisel3.util.Mux1H(io.w_en, io.w_rowAddr)
      val activeColAddr = chisel3.util.Mux1H(io.w_en, io.w_colAddr)
      val activeData = chisel3.util.Mux1H(io.w_en, io.data_in)
      for (i <- 0 until dims.head) { // Note here we just use width, i.e. if width doesn't match, user will get unexpected answer
        for (j <- 0 until dims.last) { 
          when(j.U === activeRowAddr & i.U === activeColAddr) {
            registers(j*dims.head + i) := activeData
          }
        }
      }
    }
  } else {
    when(io.reset) {
      for (i <- 0 until (dims.reduce{_*_})) {
        registers(i) := 0.U(bitWidth.W)
      }
    }.elsewhen(io.dump_en) {
      for (i <- 0 until dims.reduce{_*_}) {
        registers(i) := io.dump_data(i)
      }
    }.otherwise{
      for (i <- 0 until dims.reduce{_*_}) {
        registers(i) := registers(i)
      }      
    }
  }
  
  for (i <- 0 until dims.reduce{_*_}) {
    // io.data_out(j * width + i) := registers((dims.head - width + i) + j*dims.head) // FIXME: not sure about row calcutation  
    io.data_out(i) := registers(i) // FIXME: not sure about row calcutation  
  }

  // var wIdMap = (0 until numBufs).map{ i => (i -> 0) }.toMap
  var wId = 0
  def connectWPort(data: UInt, row_addr: UInt, col_addr: UInt, en: Bool, port: List[Int]) {
    io.data_in(wId) := data
    io.w_en(wId) := en
    // If there is write port, tie down shift ens
    for (j <- 0 until dims.last) {
      io.shift_en(j) := false.B
    }
    io.w_rowAddr(wId) := row_addr
    io.w_colAddr(wId) := col_addr
    wId = wId + 1
  }

  def connectShiftPort(data: UInt, row_addr: UInt, en: Bool, port: List[Int]) {
    for (j <- 0 until dims.last) {
      // If there is shift port, tie down wens
      io.w_en(j) := false.B
      when(j.U === row_addr) {
        io.data_in(j) := data
        io.shift_en(j) := en   
      }
    }
  }

  def readValue(row_addr: UInt, col_addr:UInt, port: Int): UInt = { // This randomly screws up sometimes, so I don't use it anywhere anymore
    // chisel seems to have broke MuxLookup here...
    val result = Wire(UInt(bitWidth.W))
    val regvals = (0 until dims.reduce{_*_}).map{ i => 
      (i.U -> io.data_out(i)) 
    }
    val flat = row_addr*dims.head.U + col_addr
    result := chisel3.util.MuxLookup(flat(31,0), 0.U, regvals)
    result

    // val result = Wire(UInt(bitWidth.W))
    // val flat = row_addr*width.U + col_addr
    // val bitvec = Vec((0 until dims.reduce{_*_}).map{ i => i.U === flat })
    // for (i <- 0 until dims.reduce{_*_}) {
    //   when(i.U === flat) {
    //     result := io.data_out(i)
    //   }
    // }
    // result

    // // // Sum hack because chisel keeps messing things up
    // val result = Wire(UInt(bitWidth.W))
    // val flat = row_addr*width.U + col_addr
    // result := (0 until width).map { i=> 
    //   (0 until height).map{ j => Mux(j.U === row_addr && i.U === col_addr, io.data_out(i), 0.U) }.reduce{_+_}}.reduce{_+_}
    // result

  }
  
}



// TODO: Currently assumes one write port, possible read port on every buffer
class NBufShiftRegFile(val dims: List[Int], val stride: Int, val numBufs: Int, val wPar: Int, val bitWidth: Int) extends Module { 

  def this(tuple: (List[Int], Int, Int, Int, Int)) = this(tuple._1, tuple._2, tuple._3, tuple._4, tuple._5)
  val io = IO(new Bundle { 
    val sEn = Vec(numBufs, Input(Bool()))
    val sDone = Vec(numBufs, Input(Bool()))
    val data_in  = Vec(wPar*dims.last, Input(UInt(bitWidth.W))) // TODO: Should probalby use stride, not wpar
    val w_rowAddr   = Vec(wPar*dims.last, Input(UInt(32.W)))
    val w_colAddr   = Vec(wPar*dims.last, Input(UInt(32.W)))
    // val w_rowAddr   = Vec(wPar*dims.last, Input(UInt(log2Up(((width+stride-1)/stride)*stride + 1).W)))
    // val w_colAddr   = Vec(wPar*dims.last, Input(UInt(log2Up(((width+stride-1)/stride)*stride + 1).W)))
    val w_en     = Vec(wPar*dims.last, Input(Bool()))
    val shift_en = Vec(dims.last, Input(Bool()))
    val reset    = Input(Bool())
    val data_out = Vec(dims.reduce{_*_}*numBufs, Output(UInt(bitWidth.W)))
  })
  

  val sEn_latch = (0 until numBufs).map{i => Module(new SRFF())}
  val sDone_latch = (0 until numBufs).map{i => Module(new SRFF())}

  val swap = Wire(Bool())

  // Latch whether each buffer's stage is enabled and when they are done
  (0 until numBufs).foreach{ i => 
    sEn_latch(i).io.input.set := io.sEn(i)
    sEn_latch(i).io.input.reset := swap
    sEn_latch(i).io.input.asyn_reset := reset
    sDone_latch(i).io.input.set := io.sDone(i)
    sDone_latch(i).io.input.reset := swap
    sDone_latch(i).io.input.asyn_reset := reset
  }
  val anyEnabled = sEn_latch.map{ en => en.io.output.data }.reduce{_|_}
  swap := sEn_latch.zip(sDone_latch).map{ case (en, done) => en.io.output.data === done.io.output.data }.reduce{_&_} & anyEnabled

  val shiftRegs = (0 until numBufs).map{i => Module(new ShiftRegFile(dims, stride, wPar, isBuf = {i>0}, bitWidth))}

  for (i <- 0 until numBufs) {
    for (j <- 0 until dims.reduce{_*_}) {
      io.data_out(i*dims.reduce{_*_} + j) := shiftRegs(i).io.data_out(j)
    }
  }

  shiftRegs(0).io.data_in := io.data_in
  shiftRegs(0).io.w_rowAddr := io.w_rowAddr
  shiftRegs(0).io.w_colAddr := io.w_colAddr
  shiftRegs(0).io.w_en := io.w_en
  shiftRegs(0).io.shift_en := io.shift_en
  shiftRegs(0).io.reset := io.reset

  for (i <- 1 until numBufs) {
    shiftRegs(i).io.dump_en := swap
    shiftRegs(i).io.dump_data := shiftRegs(i-1).io.data_out
    shiftRegs(i).io.w_en.foreach{_ := false.B}
    shiftRegs(i).io.shift_en.foreach{_ := false.B}
    shiftRegs(i).io.reset := io.reset
  }


  // var wIdMap = (0 until numBufs).map{ i => (i -> 0) }.toMap
  var wId = 0
  def connectWPort(data: UInt, row_addr: UInt, col_addr: UInt, en: Bool, ports: List[Int]) {
    if (ports.length == 1) {
      val port = ports.head
      assert(port == 0) // Only support writes to port 0 for now
      // io.w_port := port.U
      io.data_in(wId) := data
      io.w_en(wId) := en
      // If there is write port, tie down shift ens
      for (j <- 0 until dims.last) {
        io.shift_en(j) := false.B
      }
      io.w_rowAddr(wId) := row_addr
      io.w_colAddr(wId) := col_addr
      wId = wId + 1      
    } else {
      // broadcasting not implemented yet
    }
  }

  def connectShiftPort(data: UInt, row_addr: UInt, en: Bool, ports: List[Int]) {
    if (ports.length == 1) {
      val port = ports.head
      assert(port == 0) // Only support writes to port 0 for now
      // io.w_port := port.U
      for (j <- 0 until dims.last) {
        // If there is shift port, tie down wens
        io.w_en(j) := false.B
        when(j.U === row_addr) {
          io.data_in(j) := data
          io.shift_en(j) := en   
        }
      }      
    } else {
      // broadcasting not implemented yet
    }
  }

  def readValue(row_addr: UInt, col_addr: UInt, port: Int): UInt = { // This randomly screws up sometimes, so I don't use it anywhere anymore
    // chisel seems to have broke MuxLookup here...
    val result = Wire(UInt(bitWidth.W))
    val regvals = (0 until numBufs*dims.reduce{_*_}).map{ i => 
      (i.U -> io.data_out(i)) 
    }
    val flat = (port*dims.reduce{_*_}).U + row_addr*dims.last.U + col_addr
    result := chisel3.util.MuxLookup(flat(31,0), 0.U, regvals)
    result

    // val result = Wire(UInt(bitWidth.W))
    // val flat = row_addr*width.U + col_addr
    // val bitvec = Vec((0 until dims.reduce{_*_}).map{ i => i.U === flat })
    // for (i <- 0 until dims.reduce{_*_}) {
    //   when(i.U === flat) {
    //     result := io.data_out(i)
    //   }
    // }
    // result

    // // // Sum hack because chisel keeps messing things up
    // val result = Wire(UInt(bitWidth.W))
    // val flat = row_addr*width.U + col_addr
    // result := (0 until width).map { i=> 
    //   (0 until height).map{ j => Mux(j.U === row_addr && i.U === col_addr, io.data_out(i), 0.U) }.reduce{_+_}}.reduce{_+_}
    // result

  }

  def connectStageCtrl(done: Bool, en: Bool, ports: List[Int]) {
    ports.foreach{ port => 
      io.sEn(port) := en
      io.sDone(port) := done
    }
  }

  
}

class LUT(val dims: List[Int], val inits: List[Float], val numReaders: Int, val width: Int, val fracBits: Int) extends Module {

  def this(tuple: (List[Int], List[Float], Int, Int, Int)) = this(tuple._1, tuple._2, tuple._3, tuple._4, tuple._5)
  val io = IO(new Bundle { 
    val addr = Vec(numReaders*dims.length, Input(UInt(32.W)))
    // val en = Vec(numReaders, Input(Bool()))
    val data_out = Vec(numReaders, Output(new types.FixedPoint(true, 32, 0)))
  })

  assert(dims.reduce{_*_} == inits.length)
  val options = (0 until dims.reduce{_*_}).map { i => 
    val initval = (inits(i)*scala.math.pow(2,fracBits)).toInt
    // initval.U
    ( i.U -> initval.S(32.W) )
  }

  val flat_addr = (0 until numReaders).map{ k => 
    val base = k*dims.length
    (0 until dims.length).map{ i => 
      (io.addr(i + base) * (dims.drop(i).reduce{_*_}/dims(i)).U(32.W))(31,0) // TODO: Why is chisel being so stupid with this type when used in the MuxLookup
    }.reduce{_+_}
  }

  // val active_addr = Mux1H(io.en, flat_addr)

  // io.data_out := Mux1H(onehot, options)
  (0 until numReaders).foreach{i =>
    io.data_out(i) := MuxLookup(flat_addr(i), 0.S, options).asUInt
  }
  // val selected = MuxLookup(active_addr, 0.S, options)

  var rId = 0
  def connectRPort(addrs: List[UInt], en: Bool): Int = {
    (0 until addrs.length).foreach{ i => 
      val base = rId * addrs.length
      io.addr(base + i) := addrs(i)
    }
    // io.en(rId) := en
    rId = rId + 1
    rId - 1
  }
  
}
