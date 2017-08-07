package fringe.fringeZynq.bigIP
import fringe.FringeGlobals
import chisel3._
import chisel3.util._
import scala.collection.mutable.Set

trait ZynqBlackBoxes {

  // To avoid creating the same IP twice
  val createdIP = Set[String]()

  class Divider(val dividendWidth: Int, val divisorWidth: Int, val signed: Boolean, val latency: Int) extends Module {
    val io = IO(new Bundle {
      val dividend = Input(UInt(dividendWidth.W))
      val divisor = Input(UInt(divisorWidth.W))
      val out = Output(UInt(dividendWidth.W))
    })

    val fractionBits = 2

    val m = Module(new DivModBBox(dividendWidth, divisorWidth, signed, false, fractionBits, latency))
    m.io.aclk := clock
    m.io.s_axis_dividend_tvalid := true.B
    m.io.s_axis_dividend_tdata := io.dividend
    m.io.s_axis_divisor_tvalid := true.B
    m.io.s_axis_divisor_tdata := io.divisor
    io.out := m.io.m_axis_dout_tdata(dividendWidth-1, fractionBits)
  }

  class Modulo(val dividendWidth: Int, val divisorWidth: Int, val signed: Boolean, val latency: Int) extends Module {
    val io = IO(new Bundle {
      val dividend = Input(UInt(dividendWidth.W))
      val divisor = Input(UInt(divisorWidth.W))
      val out = Output(UInt(dividendWidth.W))
    })

    val fractionBits = dividendWidth

    val m = Module(new DivModBBox(dividendWidth, divisorWidth, signed, true, fractionBits, latency))
    m.io.aclk := clock
    m.io.s_axis_dividend_tvalid := true.B
    m.io.s_axis_dividend_tdata := io.dividend
    m.io.s_axis_divisor_tvalid := true.B
    m.io.s_axis_divisor_tdata := io.divisor
    io.out := m.io.m_axis_dout_tdata
  }

  class DivModBBox(val dividendWidth: Int, val divisorWidth: Int, val signed: Boolean, val isMod: Boolean, val fractionBits: Int, val latency: Int) extends BlackBox {
    val io = IO(new Bundle {
      val aclk = Input(Clock())
      val s_axis_dividend_tvalid = Input(Bool())
      val s_axis_dividend_tdata = Input(UInt(dividendWidth.W))
      val s_axis_divisor_tvalid = Input(Bool())
      val s_axis_divisor_tdata = Input(UInt(divisorWidth.W))
      val m_axis_dout_tvalid = Output(Bool())
      val m_axis_dout_tdata = Output(UInt(dividendWidth.W))
    })

    val signedString = if (signed) "Signed" else "Unsigned"
    val modString = if (isMod) "Remainder" else "Fractional"
    val moduleName = s"div_${dividendWidth}_${divisorWidth}_${latency}_${signedString}_${modString}"
    override def desiredName = s"div_${dividendWidth}_${divisorWidth}_${latency}_${signedString}_${modString}"

    // Print required stuff into a tcl file
    if (!createdIP.contains(moduleName)) {
      FringeGlobals.tclScript.println(
s"""
## Integer Divider
create_ip -name div_gen -vendor xilinx.com -library ip -version 5.1 -module_name $moduleName
set_property -dict [list CONFIG.latency_configuration {Manual} CONFIG.latency {$latency}] [get_ips $moduleName]
set_property -dict [list CONFIG.dividend_and_quotient_width {$dividendWidth} CONFIG.divisor_width {$divisorWidth} CONFIG.remainder_type {$modString} CONFIG.clocks_per_division {1} CONFIG.fractional_width {$fractionBits} CONFIG.operand_sign {$signedString}] [get_ips $moduleName]
set_property -dict [list CONFIG.ACLK_INTF.FREQ_HZ $$CLOCK_FREQ_HZ] [get_ips $moduleName]

""")

      FringeGlobals.tclScript.flush
      createdIP += moduleName
    }
  }

  class Multiplier(val aWidth: Int, val bWidth: Int, val outWidth: Int, val signed: Boolean, val latency: Int) extends Module {
    val io = IO(new Bundle {
      val a = Input(UInt(aWidth.W))
      val b = Input(UInt(bWidth.W))
      val out = Output(UInt(outWidth.W))
    })

    val fractionBits = aWidth

    val m = Module(new MultiplierBBox(aWidth, bWidth, outWidth, signed, latency))
    m.io.CLK := clock
    m.io.A := io.a
    m.io.B := io.b
    io.out := m.io.P
  }


  class MultiplierBBox(val aWidth: Int, val bWidth: Int, val outWidth: Int, val signed: Boolean, val latency: Int) extends BlackBox {
    val io = IO(new Bundle {
      val CLK = Input(Clock())
      val A = Input(UInt(aWidth.W))
      val B = Input(UInt(bWidth.W))
      val P = Output(UInt(outWidth.W))
    })

    val signedString = if (signed) "Signed" else "Unsigned"
    val moduleName = s"mul_${aWidth}_${bWidth}_${outWidth}_${latency}_${signedString}"
    override def desiredName = s"mul_${aWidth}_${bWidth}_${outWidth}_${latency}_${signedString}"

    // Print required stuff into a tcl file
    if (!createdIP.contains(moduleName)) {
      FringeGlobals.tclScript.println(
s"""
## Integer Multiplier
create_ip -name mult_gen -vendor xilinx.com -library ip -version 12.0 -module_name $moduleName
set_property -dict [list CONFIG.MultType {Parallel_Multiplier} CONFIG.PortAType {$signedString}  CONFIG.PortAWidth {$aWidth} CONFIG.PortBType {$signedString} CONFIG.PortBWidth {$bWidth} CONFIG.Multiplier_Construction {Use_Mults} CONFIG.OptGoal {Speed} CONFIG.Use_Custom_Output_Width {true} CONFIG.OutputWidthHigh {$outWidth} CONFIG.PipeStages {$latency}] [get_ips $moduleName]
set_property -dict [list CONFIG.clk_intf.FREQ_HZ $$CLOCK_FREQ_HZ] [get_ips $moduleName]

""")

      FringeGlobals.tclScript.flush
      createdIP += moduleName
    }
  }

}


