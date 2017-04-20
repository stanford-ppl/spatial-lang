// See LICENSE.txt for license details.
package templates

import chisel3._


/**
 * NBufCtr: 1-dimensional counter. Basically a cheap, wrapping counter because  
             chisel is retarted and optimizes away a Vec(1) to a single val,
             but still forces you to index the thing and hence only gets the
             first bit
 */
class NBufCtr() extends Module {
  val io = IO(new Bundle {
    val input = new Bundle {
      val start    = Input(UInt(32.W)) // TODO: Currently resets to "start" but wraps to 0, is this normal behavior?
      val max      = Input(UInt(32.W))
      val countUp  = Input(Bool())
      val enable = Input(Bool())
    }
    val output = new Bundle {
      val count      = Output(UInt(32.W))
    }
  })

  val cnt = Reg(UInt(32.W))  // Because chisel f***ing broke reg init randomly on 3/7/17, work around

  val effectiveCnt = Mux(cnt + io.input.start >= io.input.max, cnt + io.input.start - io.input.max, cnt + io.input.start)

  val nextCntDown = Mux(io.input.enable, Mux(cnt === 0.U, io.input.max-1.U, cnt-1.U), cnt)
  val nextCntUp = Mux(io.input.enable, Mux(cnt + 1.U === io.input.max, 0.U, cnt+1.U), cnt)
  cnt := Mux(reset, 0.U, Mux(io.input.countUp, nextCntUp, nextCntDown))

  io.output.count := effectiveCnt
}


/**
 * IncDincCtr: 1-dimensional counter, used in tracking number of elements when you push and pop
               from a fifo
 */
class IncDincCtr(inc: Int, dinc: Int, max: Int) extends Module {
  val io = IO(new Bundle {
    val input = new Bundle {
      val inc_en     = Input(Bool())
      val dinc_en    = Input(Bool())
    }
    val output = new Bundle {
      val overread      = Output(Bool())
      val overwrite      = Output(Bool())
      val empty         = Output(Bool())
      val full          = Output(Bool())
    }
  })

  val cnt = RegInit(0.S(32.W))

  val numPushed = Mux(io.input.inc_en, inc.S, 0.S)
  val numPopped = Mux(io.input.dinc_en, dinc.S, 0.S)
  cnt := cnt + numPushed - numPopped

  io.output.overread := cnt < 0.S
  io.output.overwrite := cnt > max.S
  io.output.empty := cnt === 0.S
  io.output.full := cnt === max.S
}



/**
 * RedxnCtr: 1-dimensional counter. Basically a cheap, wrapping for reductions
 */
class RedxnCtr() extends Module {
  val io = IO(new Bundle {
    val input = new Bundle {
      val max      = Input(UInt(32.W))
      val enable = Input(Bool())
      val reset = Input(Bool())
    }
    val output = new Bundle {
      val done      = Output(Bool())
    }
  })

  val cnt = RegInit(0.U(32.W))

  val nextCntUp = Mux(io.input.enable, Mux(cnt + 1.U === io.input.max, 0.U, cnt+1.U), cnt)
  cnt := Mux(io.input.reset, 0.U, nextCntUp)

  io.output.done := cnt + 1.U === io.input.max
}

/**
 * SingleCounter: 1-dimensional counter. Counts upto 'max', each time incrementing
 * by 'stride', beginning at zero.
 * @param w: Word width
 */
class SingleCounter(val par: Int) extends Module {
  val io = IO(new Bundle {
    val input = new Bundle {
      val start    = Input(UInt(32.W)) // TODO: Currently resets to "start" but wraps to 0, is this normal behavior?
      val max      = Input(UInt(32.W))
      val stride   = Input(UInt(32.W))
      val gap      = Input(UInt(32.W))
      // val wrap     = BoolInput(()) // TODO: This should let 
      //                                   user specify (8 by 3) ctr to go
      //                                   0,3,6 (wrap) 1,4,7 (wrap) 2,5...
      //                                   instead of default
      //                                   0,3,6 (wrap) 0,3,6 (wrap) 0,3...
      val reset  = Input(Bool())
      val enable = Input(Bool())
      val saturate = Input(Bool())
    }
    val output = new Bundle {
      val count      = Vec(par, Output(UInt(32.W)))
      val countWithoutWrap = Vec(par, Output(UInt(32.W))) // Rough estimate of next val without wrap, used in FIFO
      val done   = Output(Bool())
      val extendedDone = Output(Bool())
      val saturated = Output(Bool())
    }
  })

  if (par > 0) {
    val base = Module(new FF(32))
    val init = io.input.start
    base.io.input.init := init
    base.io.input.reset := io.input.reset
    base.io.input.enable := io.input.reset | io.input.enable

    val count = base.io.output.data
    val newval = count + (io.input.stride * par.U) + io.input.gap
    val isMax = newval >= io.input.max
    val wasMax = RegNext(isMax, false.B)
    val wasEnabled = RegNext(io.input.enable, false.B)
    val next = Mux(isMax, Mux(io.input.saturate, count, init), newval)
    base.io.input.data := Mux(io.input.reset, init, next)

    (0 until par).foreach { i => io.output.count(i) := count + i.U*io.input.stride }
    (0 until par).foreach { i => 
      io.output.countWithoutWrap(i) := Mux(count === 0.U, io.input.max, count) + i.U*io.input.stride
    }
    io.output.done := io.input.enable & isMax
    io.output.saturated := io.input.saturate & isMax
    io.output.extendedDone := (io.input.enable | wasEnabled) & (isMax | wasMax)
  } else { // Forever21 counter
    io.output.saturated := false.B
    io.output.extendedDone := false.B
    io.output.done := false.B
  }
}


/*
     outermost    middle   innermost
      |     |    |     |    |     |
      |     |    |     |    |     |
      |_____|    |_____|    |_____|
      _| | |_    __| |    _____|
     |   |   |  |    |   |   
count(0) 1   2  3    4   5

*/

/**
 * Counter: n-depth counter. Counts up to each max. Lists go from
            outermost (slowest) to innermost (fastest) counter.
 * @param w: Word width
 */
class Counter(val par: List[Int]) extends Module {
  val depth = par.length
  val numWires = par.reduce{_+_}

  val io = IO(new Bundle {
    val input = new Bundle {
      val starts    = Vec(depth, Input(UInt(32.W)))
      val maxes      = Vec(depth, Input(UInt(32.W)))
      val strides   = Vec(depth, Input(UInt(32.W)))
      val gaps      = Vec(depth, Input(UInt(32.W)))
      val reset  = Input(Bool())
      val enable = Input(Bool())
      val saturate = Input(Bool())
      val isStream = Input(Bool()) // If a stream counter, do not need enable on to report done
    }
    val output = new Bundle {
      val counts      = Vec(numWires, Output(UInt(32.W))) 
      // val countBases  = Vec(depth, UInt(32.WOutput())) // For debugging the base of each ctr
      val done   = Output(Bool())
      val extendedDone   = Output(Bool()) // Tool for ensuring done signal is stable for one rising edge
      val saturated = Output(Bool())
    }
  })

  // Create counters
  val ctrs = (0 until depth).map{ i => Module(new SingleCounter(par(i))) }

  // Wire up the easy inputs from IO
  ctrs.zipWithIndex.foreach { case (ctr, i) =>
    ctr.io.input.start := io.input.starts(i)
    ctr.io.input.max := io.input.maxes(i)
    ctr.io.input.stride := io.input.strides(i)
    ctr.io.input.gap := io.input.gaps(i)
    ctr.io.input.reset := io.input.reset
    ctr.io.input.gap := 0.U
  }

  // Wire up the enables between ctrs
  ctrs(depth-1).io.input.enable := io.input.enable
  (0 until depth-1).foreach { i =>
    ctrs(i).io.input.enable := ctrs(i+1).io.output.done & io.input.enable
  }

  // Wire up the saturates between ctrs
  ctrs(0).io.input.saturate := io.input.saturate
  (1 until depth).foreach { i =>
    ctrs(i).io.input.saturate := io.input.saturate & ctrs.take(i).map{ ctr => ctr.io.output.saturated }.reduce{_&_}
  }

  // Wire up the outputs
  par.zipWithIndex.foreach { case (p, i) => 
    val addr = par.take(i+1).reduce{_+_} - par(i) // i+1 to avoid reducing empty list
    (0 until p).foreach { k => io.output.counts(addr+k) := ctrs(i).io.output.count(k) }
  }

  // // Wire up countBases for easy debugging
  // ctrs.zipWithIndex.map { case (ctr,i) => 
  //   io.output.countBases(i) := ctr.io.output.count(0)
  // }

  // Wire up the done, saturated, and extendedDone signals
  val isDone = ctrs.map{_.io.output.done}.reduce{_&_}
  val wasDone = RegNext(isDone, false.B)
  val isSaturated = ctrs.map{_.io.output.saturated}.reduce{_&_}
  val wasWasDone = RegNext(wasDone, false.B)
  io.output.done := Mux(io.input.isStream, true.B, io.input.enable) & isDone & ~wasDone
  io.output.extendedDone := io.input.enable & isDone & ~wasWasDone
  io.output.saturated := io.input.saturate & isSaturated

}


