package fringe

import util._
import chisel3._
import chisel3.util._
import templates.SRFF
import templates.Utils.log2Up

/**
 * DRAM Memory Access Generator
 * MAG config register format
 */
case class MAGOpcode() extends Bundle {
  val scatterGather = Bool()
//  val isWr = Bool()

  override def cloneType(): this.type = {
    new MAGOpcode().asInstanceOf[this.type]
  }
}

class Command(w: Int) extends Bundle {
  val addr = UInt(w.W)
  val isWr = Bool()
  val size = UInt(w.W)

  override def cloneType(): this.type = {
    new Command(w).asInstanceOf[this.type]
  }
}

// Parallelization and word width information
case class StreamParInfo(w: Int, v: Int)

class MemoryStream(addrWidth: Int) extends Bundle {
  val cmd = Flipped(Decoupled(new Command(addrWidth)))

  override def cloneType(): this.type = {
    new MemoryStream(addrWidth).asInstanceOf[this.type]
  }
}

class LoadStream(p: StreamParInfo) extends MemoryStream(addrWidth = 64) {
  val rdata = Decoupled(Vec(p.v, UInt(p.w.W)))

  override def cloneType(): this.type = {
    new LoadStream(p).asInstanceOf[this.type]
  }
}

class StoreStream(p: StreamParInfo) extends MemoryStream(addrWidth = 64) {
  val wdata = Flipped(Decoupled(Vec(p.v, UInt(p.w.W))))
  val wresp = Decoupled(Bool())

  override def cloneType(): this.type = {
    new StoreStream(p).asInstanceOf[this.type]
  }

}

class AppStreams(loadPar: List[StreamParInfo], storePar: List[StreamParInfo]) extends Bundle {
  val loads = HVec.tabulate(loadPar.size) { i => new LoadStream(loadPar(i)) }
  val stores = HVec.tabulate(storePar.size) { i => new StoreStream(storePar(i)) }

  override def cloneType(): this.type = {
    new AppStreams(loadPar, storePar).asInstanceOf[this.type]
  }

}

class DRAMCommand(w: Int, v: Int) extends Bundle {
  val addr = UInt(64.W)
  val isWr = Bool() // 1
  val tag = UInt(w.W)
  val streamId = UInt(w.W)
  val wdata = Vec(v, UInt(w.W)) // v

  override def cloneType(): this.type = {
    new DRAMCommand(w, v).asInstanceOf[this.type]
  }

}

class DRAMResponse(w: Int, v: Int) extends Bundle {
  val rdata = Vec(v, UInt(w.W)) // v
  val tag = UInt(w.W)
  val streamId = UInt(w.W)

  override def cloneType(): this.type = {
    new DRAMResponse(w, v).asInstanceOf[this.type]
  }

}

class DRAMStream(w: Int, v: Int) extends Bundle {
  val cmd = Decoupled(new DRAMCommand(w, v))
  val resp = Flipped(Decoupled(new DRAMResponse(w, v)))

  override def cloneType(): this.type = {
    new DRAMStream(w, v).asInstanceOf[this.type]
  }
}

class GenericStreams(streamIns: List[StreamParInfo], streamOuts: List[StreamParInfo]) extends Bundle {
  val ins = HVec.tabulate(streamIns.size) { i => Decoupled(new StreamInAccel(streamIns(i))) }
  val outs = HVec.tabulate(streamOuts.size) { i => Flipped(Decoupled(new StreamOutAccel(streamOuts(i)))) }

  override def cloneType(): this.type = {
    new GenericStreams(streamIns, streamOuts).asInstanceOf[this.type]
  }

}

class StreamIO(val w: Int) extends Bundle {
  val tag = UInt(w.W)
  val last = Bool()

  override def cloneType(): this.type = new StreamIO(w).asInstanceOf[this.type]
}

object StreamOut {
  def apply(w: Int) = Decoupled(new StreamIO(w))
}

object StreamIn {
  def apply(w: Int) = Flipped(Decoupled(new StreamIO(w)))
}

class StreamOutAccel(p: StreamParInfo) extends Bundle {
  val data = UInt(p.w.W)
  val tag = UInt(p.w.W)
  val last = Bool()

  override def cloneType(): this.type = { new StreamOutAccel(p).asInstanceOf[this.type] }
}

class StreamInAccel(p: StreamParInfo) extends Bundle {
  val data = UInt(p.w.W)
  val tag = UInt(p.w.W)
  val last = Bool()

  override def cloneType(): this.type = { new StreamInAccel(p).asInstanceOf[this.type] }
}
