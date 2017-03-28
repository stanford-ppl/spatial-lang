package templates

import chisel3._
import chisel3.util.log2Ceil
import chisel3.util.MuxLookup


// Note: Can use counter template (with stride)
// Constant bounds in these counters may save area however
class TmpCounter(max: Int, reset_val: Int, stride: Int = 1) extends Module {
  val io = IO(new Bundle {
    val reset = Input(Bool())
    val en    = Input(Bool())
    val count = Output(UInt(log2Ceil(max+stride).W))
    val wrap  = Output(Bool())
  })

  def wrapAround(n: UInt, max: UInt) = 
    Mux(n >= max, n-max, n)

  val x = RegInit(reset_val.asUInt(log2Ceil(max+stride).W))
  when(io.reset) {
    x := reset_val.U(log2Ceil(max+stride).W)
  } .elsewhen(io.en) {
    x := wrapAround(x + stride.U, max.U)
  }
  io.count := x
  io.wrap := (x+stride.U >= max.U) && io.en
}


// ENHANCEMENT: currently this assumes read col par = 1, read row par = kernel height, and write row/col par is 1 and 1
// See comments below: first should implement read col par, and also read row par == 1
class LineBuffer(val num_lines: Int, val line_size: Int, val extra_rows_to_buffer: Int, val COL_PAR: Int, val ROW_PAR: Int) extends Module {

  def this(tuple: (Int, Int, Int, Int, Int)) = this(tuple._1, tuple._2, tuple._3, tuple._4, tuple._5)
  val io = IO(new Bundle {
    val data_in  = Input(UInt(32.W))  // ENHANCEMENT: Use banked SRAMs to enq multiple at once (write par != 1)
    val w_en     = Input(UInt(1.W))   // TODO: Bool()
    // val r_en     = Input(UInt(1.W))
    // val w_done   = Input(UInt(1.W))

    // Buffering signals
    val sEn = Vec(num_lines, Input(Bool()))
    val sDone = Vec(num_lines, Input(Bool()))

    // val r_done   = Input(UInt(1.W)) // Like double buffering

    val reset    = Input(UInt(1.W))
    val col_addr = Input(UInt(log2Ceil(line_size+1).W)) // From each row, read COL_PAR px starting at this col_addr
    // val row_addr = Input(UInt(1.W)) // ENHANCEMENT: Eventually will be a Vec, but for now ROW_PAR is 1 or num_lines only
    // val data_out = Vec(ROW_PAR, Vec(COL_PAR, Output(UInt(32.W)))) // TODO: Don't use Vec(Vec) since Chisel will switch inputs and outputs
    val data_out = Vec(ROW_PAR, Output(UInt(32.W)))
    // val row_wrap = Output(UInt(1.W))
  })
  
  // ENHANCEMENT: should r_en RAMs (currently not supported, but saves power)
  // ENHANCEMENT: enq multiple @ once since banked -- COL_WRITE_PAR, ROW_WRITE_PAR (and change names of 2 PARs above to COL/ROW_READ_PAR)
  // ENHANCEMENT: could keep internal state of whether we are initializing (false means steady state),
  //                val initializing_state = Reg(init=reset_val.asUInt(log2Ceil(max+1).W))
  //                ...
  //              and also other info, e.g. set an output high when line fills
  //                row_wrap := WRITE_countRowNum.io.wrap
  //              etc. 
  
  // Buffering logic
  val sEn_latch = (0 until num_lines).map{i => Module(new SRFF())}
  val sDone_latch = (0 until num_lines).map{i => Module(new SRFF())}
  val swap = Wire(Bool())
  // Latch whether each buffer's stage is enabled and when they are done
  (0 until num_lines).foreach{ i => 
    sEn_latch(i).io.input.set := io.sEn(i)
    sEn_latch(i).io.input.reset := swap
    sDone_latch(i).io.input.set := io.sDone(i)
    sDone_latch(i).io.input.reset := swap
  }
  val anyEnabled = sEn_latch.map{ en => en.io.output.data }.reduce{_|_}
  swap := sEn_latch.zip(sDone_latch).map{ case (en, done) => en.io.output.data === done.io.output.data }.reduce{_&_} & anyEnabled

  assert(ROW_PAR == 1 || ROW_PAR == num_lines)

  // --------------------------------------------------------------------------------------------------------------------------------
  // Declare buffer data structure in SRAM
  // --------------------------------------------------------------------------------------------------------------------------------
  // ENHANCEMENT: support read parallelism
  //   - col_par = # banks
  //   - row_par = # copies of above, i.e. # scratchpads
  //  Banking is needed otherwise parallel col reads are not possible (row is currently possible since there
  //  is 1 SRAM per row, i.e. 1 bank and row_par copies of it, i.e. row_par scratchpads)
  //   - E.g. this would enable parallelism of 3x3 to be 18 instead of 9, and get processing done 2x faster
  //     - would need (for stride 1) to process 1 2 3 4, then 3 4 5 6, etc., so need to read 2 px per row into shift register
  //  Later, also add support for both R and W parallelization
  //   - W par less useful since conv is often compute bound
  val linebuffer = List.fill(num_lines + extra_rows_to_buffer)(Mem(line_size, UInt(32.W)))
  
  // --------------------------------------------------------------------------------------------------------------------------------
  // Write logic
  // --------------------------------------------------------------------------------------------------------------------------------
  
  // Inner counter over row width -- keep track of write address in current row
  val WRITE_countRowPx = Module(new TmpCounter(line_size, 0))
  WRITE_countRowPx.io.en := io.w_en
  WRITE_countRowPx.io.reset := io.reset
  val px = WRITE_countRowPx.io.count
  
  // Outer counter over number of SRAM -- keep track of current row
  val WRITE_countRowNum = Module(new TmpCounter(num_lines + extra_rows_to_buffer, 0))
  WRITE_countRowNum.io.reset := io.reset
  WRITE_countRowNum.io.en := swap // could replace RHS with a w_done input signal (it could also reset WRITE_countRowPx)
  val cur_row = WRITE_countRowNum.io.count
  
  // Write data_in into line buffer
  for (i <- 0 until (num_lines + extra_rows_to_buffer)) {
    when((cur_row === i.U) & (io.w_en === 1.U)) {
      linebuffer(i).write(px, io.data_in)
    }
  }
    
  // --------------------------------------------------------------------------------------------------------------------------------
  // Read logic
  // --------------------------------------------------------------------------------------------------------------------------------
  
  // ENHANCEMENT: Support row_addr to only read from one row if ROW_PAR == 1
  // - Rather than crossbar from each line to each output, it becomes just a mux (1 output),
  //   but now the sel of that mux also depends on row_addr
  
  // ENHANCEMENT: Support col_addr, to read from more than 1 col (needs banking)
  
  // Read data_out from line buffer
  // This requires a crossbar, i.e. mux from each line (num_lines + extra_rows_to_buffer) to each output (num_lines)
  // ENHANCEMENT: May save some area using a single counter with many outputs and adders/mux for each (to do mod/wrap) but 
  // multiple counters (which start/reset @ various #s) is simpler to write
  var READ_inner_pointers = Array.tabulate(num_lines) { i =>  // ENHANCEMENT: num_lines -> row par
    val READ_index_counter = Module(new TmpCounter(num_lines + extra_rows_to_buffer, i, extra_rows_to_buffer)) // i is reset and initial value
    READ_index_counter.io.reset := io.reset
    READ_index_counter.io.en := swap
    READ_index_counter.io.count // Return this to READ_inner_pointers(i)
  }
  var linebuf_read_wires_map = Array.tabulate(num_lines + extra_rows_to_buffer) { i =>
    // when(io.r_en) {  // ENHANCEMENT: r_en to save power, i.e. make the below wire RHS of -> into a reg
    (i.U -> linebuffer(i).read(io.col_addr))
    // }
  }
  for (i <- 0 until (num_lines)) { // ENHANCEMENT: num_lines -> row par
    io.data_out(i) := MuxLookup(READ_inner_pointers(i), 0.U, linebuf_read_wires_map)
  }
  
  def connectStageCtrl(done: Bool, en: Bool, ports: List[Int]) {
    ports.foreach{ port => 
      io.sEn(port) := en
      io.sDone(port) := done
    }
  }

}
