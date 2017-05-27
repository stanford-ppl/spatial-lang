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


class multidimRegW(val N: Int, val w: Int) extends Bundle {
  val addr = Vec(N, UInt(32.W))
  val data = UInt(w.W)
  val en = Bool()
  val shiftEn = Bool()

  override def cloneType = (new multidimRegW(N, w)).asInstanceOf[this.type] // See chisel3 bug 358
}


// This exposes all registers as output ports now
class ShiftRegFile(val dims: List[Int], val stride: Int, val wPar: Int, val isBuf: Boolean, val bitWidth: Int) extends Module {

  def this(tuple: (List[Int], Int, Int, Boolean, Int)) = this(tuple._1, tuple._2, tuple._3, tuple._4, tuple._5)
  val io = IO(new Bundle { 
    // Signals for dumping data from one buffer to next
    val dump_data = Vec(dims.reduce{_*_}, Input(UInt(bitWidth.W)))
    val dump_en = Input(Bool())

    val w = Vec(wPar, Input(new multidimRegW(dims.length, bitWidth)))

    val reset    = Input(Bool())
    val data_out = Vec(dims.reduce{_*_}, Output(UInt(bitWidth.W)))
  })
  
  // if (!isBuf) {io.dump_en := false.B}

  // val size_rounded_up = ((dims.head+stride-1)/stride)*stride // Unlike shift reg, for shift reg file it is user's problem if width does not match (no functionality guarantee)
  val registers = List.fill(dims.reduce{_*_})(Reg(UInt(bitWidth.W))) // Note: Can change to use FF template
  for (i <- 0 until dims.reduce{_*_}) {
    io.data_out(i) := registers(i)
  }


  // val flat_sh_addrs = if (dims.length == 1) 0.U else {
  //     io.w.addr.dropRight(1).zipWithIndex.map{ case (addr, i) =>
  //     addr * (dims.drop(i).reduce{_*_}/dims(i)).U
  //   }.reduce{_+_}
  // }



  if (!isBuf) {
    // Connect a w port to each reg
    (dims.reduce{_*_}-1 to 0 by -1).foreach { i => 
      // Construct n-D coords
      val coords = (0 until dims.length).map { k => 
        if (k + 1 < dims.length) {(i / dims.drop(k+1).reduce{_*_}) % dims(k)} else {i % dims(k)}
      }
      when(io.reset) {
        registers(i) := 0.U(bitWidth.W)
      }.elsewhen(io.dump_en) {
        for (i <- 0 until dims.reduce{_*_}) {
          registers(i) := io.dump_data(i)
        }
      }.otherwise {
        if (wPar > 1) {
          // Address flattening
          val flat_w_addrs = io.w.map{ bundle =>
            bundle.addr.zipWithIndex.map{case (a, i) => a * (dims.drop(i).reduce{_*_}/dims(i)).U}.reduce{_+_}
          }

          val write_here = (0 until wPar).map{ ii => io.w(ii).en & (flat_w_addrs(ii) === i.U) }
          val shift_entry_here =  (0 until wPar).map{ ii => io.w(ii).shiftEn & (flat_w_addrs(ii) === i.U) }
          val write_data = Mux1H(write_here.zip(shift_entry_here).map{case (a,b) => a|b}, io.w)
          // val shift_data = Mux1H(shift_entry_here, io.w)
          val has_writer = write_here.reduce{_|_}
          val has_shifter = shift_entry_here.reduce{_|_}

          // Assume no bozos will shift mid-axis
          val shift_axis = (0 until wPar).map{ ii => io.w(ii).shiftEn & {if (dims.length > 1) {(coords.last != 0).B & io.w(ii).addr.dropRight(1).zip(coords.dropRight(1)).map{case(a,b) => a === b.U}.reduce{_&_} } else {(coords.last != 0).B} }}.reduce{_|_}
          val producing_reg = 0 max (i - 1)
          registers(i) := Mux(shift_axis, registers(producing_reg), Mux(has_writer | has_shifter, write_data.data, registers(i)))
        } else {
          // Address flattening
          val flat_w_addrs = io.w(0).addr.zipWithIndex.map{case (a, i) => a * (dims.drop(i).reduce{_*_}/dims(i)).U}.reduce{_+_}

          val write_here = io.w(0).en & (flat_w_addrs === i.U)
          val shift_entry_here =  io.w(0).shiftEn & (flat_w_addrs === i.U) 
          val write_data = io.w(0).data
          // val shift_data = Mux1H(shift_entry_here, io.w)
          val has_writer = write_here
          val has_shifter = shift_entry_here

          // Assume no bozos will shift mid-axis
          val shift_axis = io.w(0).shiftEn & {if (dims.length > 1) {(coords.last != 0).B & io.w(0).addr.dropRight(1).zip(coords.dropRight(1)).map{case(a,b) => a === b.U}.reduce{_&_} } else {(coords.last != 0).B} }
          val producing_reg = 0 max (i - 1)
          registers(i) := Mux(shift_axis, registers(producing_reg), Mux(has_writer | has_shifter, write_data.data, registers(i)))
        }
      }
    }
  } else {
    when(io.reset) {
      for (i <- 0 until dims.reduce{_*_}) {
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


  var wId = 0
  def connectWPort(wBundle: Vec[multidimRegW], ports: List[Int]) {
    assert(ports.head == 0)
    (0 until wBundle.length).foreach{ i => 
      io.w(wId+i) := wBundle(i)
    }
    wId += wBundle.length
  }

  def connectShiftPort(wBundle: Vec[multidimRegW], ports: List[Int]) {
    assert(ports.head == 0)
    (0 until wBundle.length).foreach{ i => 
      io.w(wId+i) := wBundle(i)
    }
    wId += wBundle.length
  }

  def readValue(addrs: List[UInt], port: Int): UInt = { // This randomly screws up sometimes
    // chisel seems to have broke MuxLookup here...
    val result = Wire(UInt(bitWidth.W))
    val regvals = (0 until dims.reduce{_*_}).map{ i => 
      (i.U -> io.data_out(i)) 
    }
    val flat_addr = addrs.zipWithIndex.map{ case( a,i ) =>
      a * (dims.drop(i).reduce{_*_}/dims(i)).U
    }.reduce{_+_}
    result := chisel3.util.MuxLookup(flat_addr(31,0), 0.U, regvals)
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
    val w = Vec(wPar, Input(new multidimRegW(dims.length, bitWidth)))
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

  shiftRegs(0).io.w := io.w
  shiftRegs(0).io.reset := io.reset
  shiftRegs(0).io.dump_en := false.B

  for (i <- numBufs-1 to 1 by -1) {
    shiftRegs(i).io.dump_en := swap
    shiftRegs(i).io.dump_data := shiftRegs(i-1).io.data_out
    shiftRegs(i).io.w.foreach{p => p.shiftEn := false.B; p.en := false.B; p.data := 0.U}
    shiftRegs(i).io.reset := io.reset
  }


  // var wIdMap = (0 until numBufs).map{ i => (i -> 0) }.toMap
  var wId = 0
  def connectWPort(wBundle: Vec[multidimRegW], ports: List[Int]) {
    assert(ports.head == 0)
    (0 until wBundle.length).foreach{ i => 
      io.w(wId+i) := wBundle(i)
    }
    wId += wBundle.length
  }

  def connectShiftPort(wBundle: Vec[multidimRegW], ports: List[Int]) {
    assert(ports.head == 0)
    (0 until wBundle.length).foreach{ i => 
      io.w(wId+i) := wBundle(i)
    }
    wId += wBundle.length
  }

  def readValue(addrs: List[UInt], port: Int): UInt = { // This randomly screws up sometimes, so I don't use it anywhere anymore
    // chisel seems to have broke MuxLookup here...
    val result = Wire(UInt(bitWidth.W))
    val regvals = (0 until numBufs*dims.reduce{_*_}).map{ i => 
      (i.U -> io.data_out(i)) 
    }
    val flat_addr = (port*dims.reduce{_*_}).U + addrs.zipWithIndex.map{ case( a,i ) =>
      a * (dims.drop(i).reduce{_*_}/dims(i)).U
    }.reduce{_+_}
    result := chisel3.util.MuxLookup(flat_addr(31,0), 0.U, regvals)
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
