package templates

import chisel3._
import types._

/**
 * FF: Flip-flop with the ability to set enable and init
 * value as IO
 * @param w: Word width
 */

class FFIn(val w: Int) extends Bundle {
  val data   = UInt(w.W)
  val init = UInt(w.W)
  val enable = Bool()
  val reset = Bool() // Asynchronous reset

  override def cloneType = (new FFIn(w)).asInstanceOf[this.type] // See chisel3 bug 358
}
class FFOut(val w: Int) extends Bundle {
  val data  = UInt(w.W)

  override def cloneType = (new FFOut(w)).asInstanceOf[this.type] // See chisel3 bug 358
}

class FF(val w: Int) extends Module {
  val io = IO(new Bundle{
    val input = Input(new FFIn(w))
    val output = Output(new FFOut(w))
  })
  
  val ff = RegInit(io.input.init)
  ff := Mux(io.input.reset, io.input.init, Mux(io.input.enable, io.input.data, ff))
  io.output.data := Mux(io.input.reset, io.input.init, ff)

  def write[T](data: T, en: Bool, reset: Bool, port: List[Int]) {
    data match {
      case d: UInt =>
        io.input.data := d
      case d: types.FixedPoint =>
        io.input.data := d.number
    }
    io.input.enable := en
    io.input.reset := reset
    // Ignore port
  }

  def write[T](data: T, en: Bool, reset: Bool, port: Int) {
    write(data, en, reset, List(port))
  }

  def read(port: Int) = {
    io.output.data
  }

}

class NBufFF(val numBufs: Int, val w: Int) extends Module {

  // Define overloaded
  def this(tuple: (Int, Int)) = this(tuple._1, tuple._2)

  val io = IO(new Bundle {
    val sEn = Vec(numBufs, Input(Bool()))
    val sDone = Vec(numBufs, Input(Bool()))
    val broadcast = Input(new FFIn(w))
    val input = Input(new FFIn(w))
    val writerStage = Input(UInt(5.W)) // TODO: Not implemented anywhere, not sure if needed
    val output = Vec(numBufs, Output(new FFOut(w)))
    // val swapAlert = Output(Bool()) // Used for regchains
  })

  def bitsToAddress(k:Int) = {(scala.math.log(k)/scala.math.log(2)).toInt + 1}
  // def rotate[T](x: Vec[T], i:Int)={ // Chisel is so damn annoying with types, so this method doesn't work
  //   val temp = x.toList
  //   val result = x.drop(i)++x.take(i)
  //   Vec(result.toArray)
  // }

  val ff = (0 until numBufs).map{i => Module(new FF(w))}

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
  // io.swapAlert := ~swap & anyEnabled & (0 until numBufs).map{ i => sEn_latch(i).io.output.data === (sDone_latch(i).io.output.data | io.sDone(i))}.reduce{_&_} // Needs to go high when the last done goes high, which is 1 cycle before swap goes high

  val stateIn = Module(new NBufCtr())
  stateIn.io.input.start := 0.U 
  stateIn.io.input.max := numBufs.U
  stateIn.io.input.enable := swap
  stateIn.io.input.countUp := false.B

  val statesOut = (0 until numBufs).map{  i => 
    val c = Module(new NBufCtr())
    c.io.input.start := i.U
    c.io.input.max := numBufs.U
    c.io.input.enable := swap
    c.io.input.countUp := false.B
    c
  }

  ff.zipWithIndex.foreach{ case (f,i) => 
    val wrMask = stateIn.io.output.count === i.U
    val normal =  Wire(new FFIn(w))
    normal.data := io.input.data
    normal.init := io.input.init
    normal.enable := io.input.enable & wrMask
    normal.reset := io.input.reset
    f.io.input := Mux(io.broadcast.enable, io.broadcast, normal)
  }

  io.output.zip(statesOut).foreach{ case (wire, s) => 
    val sel = (0 until numBufs).map{ i => s.io.output.count === i.U }
    wire.data := chisel3.util.Mux1H(sel, Vec(ff.map{f => f.io.output.data}))
  }

  def write[T](data: T, en: Bool, reset: Bool, port: Int) {
    write(data, en, reset, List(port))
  }

  def write[T](data: T, en: Bool, reset: Bool, ports: List[Int]) {

    if (ports.length == 1) {
      val port = ports(0)
      data match { 
        case d: UInt => 
          io.input.data := d
        case d: types.FixedPoint => 
          io.input.data := d.number
      }
      io.input.enable := en
      io.input.reset := reset
      io.writerStage := port.U
    } else {
      data match { 
        case d: UInt => 
          io.broadcast.data := d
        case d: types.FixedPoint => 
          io.broadcast.data := d.number
      }
      io.broadcast.enable := en
      io.broadcast.reset := reset      
    }
  }

  def chain_pass[T](dat: T, en: Bool) { // Method specifically for handling reg chains that pass counter values between metapipe stages
    dat match {
      case data: UInt => 
        io.input.data := data
      case data: FixedPoint => 
        io.input.data := data.number
    }
    io.input.enable := en
    io.input.reset := false.B
    io.writerStage := 0.U

  }

  def connectStageCtrl(done: Bool, en: Bool, ports: List[Int]) {
    ports.foreach{ port => 
      io.sEn(port) := en
      io.sDone(port) := done
    }
  }

  def connectUnwrittenPorts(ports: List[Int]) { // TODO: Remnant from maxj?
    // ports.foreach{ port => 
    //   io.input(port).enable := false.B
    // }
  }
 
  def connectUnreadPorts(ports: List[Int]) { // TODO: Remnant from maxj?
    // Used for SRAMs
  }

  def connectUntouchedPorts(ports: List[Int]) {
    ports.foreach{ port => 
      io.sEn(port) := false.B
      io.sDone(port) := false.B
    }
  }

  def connectDummyBroadcast() {
    io.broadcast.enable := false.B
  }

  def read(port: Int) = {
    io.output(port).data
  }

}

class FFNoInit(val w: Int) extends Module {
  val io = IO(new Bundle{
    val input = Input(new FFIn(w))
    val output = Output(new FFOut(w))
  })

  val ff = Module(new FF(w))
  ff.io.input.data := io.input.data
  ff.io.input.enable := io.input.enable
  ff.io.input.reset := io.input.reset
  ff.io.input.init := 0.U(w.W)
  io.output.data := ff.io.output.data
}

class FFNoInitNoReset(val w: Int) extends Module {
  val io = IO(new Bundle{
    val input = Input(new FFIn(w))
    val output = Output(new FFOut(w))
  })

  val ff = Module(new FF(w))
  ff.io.input.data := io.input.data
  ff.io.input.enable := io.input.enable
  ff.io.input.reset := false.B
  ff.io.input.init := 0.U(w.W)
  io.output.data := ff.io.output.data
}

class FFNoReset(val w: Int) extends Module {
  val io = IO(new Bundle{
    val input = Input(new FFIn(w))
    val output = Output(new FFOut(w))
  })

  val ff = Module(new FF(w))
  ff.io.input.data := io.input.data
  ff.io.input.enable := io.input.enable
  ff.io.input.reset := false.B
  ff.io.input.init := io.input.init
  io.output.data := ff.io.output.data
}

class TFF() extends Module {

  // Overload with null string input for testing
  def this(n: String) = this()

  val io = IO(new Bundle {
    val input = new Bundle {
      val enable = Input(Bool())
    }
    val output = new Bundle {
      val data = Output(Bool())      
    }
  })

  val ff = RegInit(false.B)
  ff := Mux(io.input.enable, ~ff, ff)
  io.output.data := ff
}

class SRFF(val strongReset: Boolean = false) extends Module {

  // Overload with null string input for testing
  def this(n: String) = this()

  val io = IO(new Bundle {
    val input = new Bundle {
      val set = Input(Bool()) // Set overrides reset.  Asyn_reset overrides both
      val reset = Input(Bool())
      val asyn_reset = Input(Bool())
    }
    val output = new Bundle {
      val data = Output(Bool())      
    }
  })

  if (!strongReset) { // Set + reset = on
    val ff = RegInit(false.B)
    ff := Mux(io.input.asyn_reset, false.B, Mux(io.input.set, 
                                    true.B, Mux(io.input.reset, false.B, ff)))
    io.output.data := Mux(io.input.asyn_reset, false.B, ff)
  } else { // Set + reset = off
    val ff = RegInit(false.B)
    ff := Mux(io.input.asyn_reset, false.B, Mux(io.input.reset, 
                                    false.B, Mux(io.input.set, true.B, ff)))
    io.output.data := Mux(io.input.asyn_reset, false.B, ff)

  }
}


