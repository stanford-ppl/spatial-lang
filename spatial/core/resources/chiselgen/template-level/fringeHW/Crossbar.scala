package fringe

import chisel3._
import chisel3.util._

import templates.Utils.log2Up
import scala.language.reflectiveCalls

case class SwitchParams(val numIns: Int, val numOuts: Int)

/**
 * Crossbar config register format
 */
case class CrossbarConfig(p: SwitchParams) extends Bundle {
  var outSelect = Vec(p.numOuts, UInt(log2Up(1+p.numIns).W))

  override def cloneType(): this.type = {
    new CrossbarConfig(p).asInstanceOf[this.type]
  }
}

/**
 * Core logic inside a crossbar
 */
class CrossbarCore[T<:Data](val t: T, val p: SwitchParams, val registerOutput: Boolean = false) extends Module {
  val io = IO(new Bundle {
    val ins = Input(Vec(p.numIns, t.cloneType))
    val outs = Output(Vec(p.numOuts, t.cloneType))
    val config = Input(CrossbarConfig(p))
  })

  io.outs.zipWithIndex.foreach { case(out,i) =>
    val outMux = Module(new MuxNType(t, 1 + p.numIns)) // 0'th input is always 0
    outMux.io.ins(0) := 0.U
    for (ii <- 0 until p.numIns) {
      outMux.io.ins(ii+1) := io.ins(ii)
    }

    outMux.io.sel := io.config.outSelect(i)
    out := (if (registerOutput) RegNext(outMux.io.out, 0.U) else outMux.io.out)
  }
}

/**
 * Crossbar that connects every input to every output
 */
//class Crossbar(val w: Int, val numInputs: Int, val numOutputs: Int, val inst: CrossbarConfig) extends ConfigurableModule[CrossbarOpcode] {
//  val io = new ConfigInterface {
//    val ins = Input(Vec(numInputs, Bits(w.W)))
//    val outs = Output(Vec(numOutputs, Bits(w.W)))
//  }
//
//  val configType = CrossbarOpcode(w, numInputs, numOutputs)
//  val configIn = CrossbarOpcode(w, numInputs, numOutputs)
//  val configInit = CrossbarOpcode(w, numInputs, numOutputs, Some(inst))
//  val config = Reg(configType, configIn, configInit)
//  when (io.config_enable) {
//    configIn := configType.cloneType().fromBits(Fill(configType.getWidth, io.config_data))
//  } .otherwise {
//    configIn := config
//  }
//
//  io.outs.zipWithIndex.foreach { case(out,i) =>
//    val outMux = Module(new MuxN(numInputs, w))
//    outMux.io.ins := io.ins
//    outMux.io.sel := config.outSelect(i)
//    out := outMux.io.out
//  }
//}


/**
 * Crossbar of Vecs that connects every input to every output
 */
//class CrossbarVec(val w: Int, val v: Int, val numInputs: Int, val numOutputs: Int, val inst: CrossbarConfig) extends ConfigurableModule[CrossbarOpcode] {
//  val io = new ConfigInterface {
//    val ins = Input(Vec(numInputs, Vec(v, Bits(w.W))))
//    val outs = Output(Vec(numOutputs, Vec(v, Bits(w.W))))
//  }
//
//  val configType = CrossbarOpcode(w, numInputs, numOutputs)
//  val configIn = CrossbarOpcode(w, numInputs, numOutputs)
//  val configInit = CrossbarOpcode(w, numInputs, numOutputs, Some(inst))
//  val config = Reg(configType, configIn, configInit)
//  when (io.config_enable) {
//    configIn := configType.cloneType().fromBits(Fill(configType.getWidth, io.config_data))
//  } .otherwise {
//    configIn := config
//  }
//
//  io.outs.zipWithIndex.foreach { case(out,i) =>
//    val outMux = Module(new MuxVec(numInputs, v, w))
//    outMux.io.ins := io.ins
//    outMux.io.sel := config.outSelect(i)
//    out := outMux.io.out
//  }
//}
//

//class CrossbarVecReg(val w: Int, val v: Int, val numInputs: Int, val numOutputs: Int, val inst: CrossbarConfig) extends ConfigurableModule[CrossbarOpcode] {
//  val io = new ConfigInterface {
//    val config_enable = Bool(INPUT)
//    val ins = Vec.fill(numInputs) { Vec.fill(v) { Bits(INPUT,  width = w) } }
//    val outs = Vec.fill(numOutputs) { Vec.fill(v) { Bits(OUTPUT,  width = w) } }
//  }
//
//  val configType = CrossbarOpcode(w, numInputs, numOutputs)
//  val configIn = CrossbarOpcode(w, numInputs, numOutputs)
//  val configInit = CrossbarOpcode(w, numInputs, numOutputs, Some(inst))
//  val config = Reg(configType, configIn, configInit)
//  when (io.config_enable) {
//    configIn := configType.cloneType().fromBits(Fill(configType.getWidth, io.config_data))
//  } .otherwise {
//    configIn := config
//  }
//
//  io.outs.zipWithIndex.foreach { case(out,i) =>
//    val outMux = Module(new MuxVec(numInputs, v, w))
//
//    outMux.io.ins := io.ins
//    outMux.io.sel := config.outSelect(i)
//
//    val outFFs = List.tabulate(v) { i =>
//      val ff = Module(new FF(w))
//      ff.io.control.enable := UInt(1)
//      ff.io.data.in := outMux.io.out(i)
//      ff.io.data.init := UInt(0)
//      ff
//    }
//
//    val outVec = Vec.tabulate(v) { i => outFFs(i).io.data.out }
//
//    out := outVec
//  }
//}
//
//class CrossbarReg(val w: Int, val numInputs: Int, val numOutputs: Int, val inst: CrossbarConfig) extends ConfigurableModule[CrossbarOpcode] {
//  val io = new ConfigInterface {
//    val config_enable = Bool(INPUT)
//    val ins = Vec.fill(numInputs) { Bits(INPUT,  width = w) }
//    val outs = Vec.fill(numOutputs) { Bits(OUTPUT,  width = w) }
//  }
//
//  val configType = CrossbarOpcode(w, numInputs, numOutputs)
//  val configIn = CrossbarOpcode(w, numInputs, numOutputs)
//  val configInit = CrossbarOpcode(w, numInputs, numOutputs, Some(inst))
//  val config = Reg(configType, configIn, configInit)
//  when (io.config_enable) {
//    configIn := configType.cloneType().fromBits(Fill(configType.getWidth, io.config_data))
//  } .otherwise {
//    configIn := config
//  }
//
//  io.outs.zipWithIndex.foreach { case(out,i) =>
//    val outMux = Module(new MuxN(numInputs, w))
//
//    outMux.io.ins := io.ins
//    outMux.io.sel := config.outSelect(i)
//
//    val outFF = Module(new FF(w))
//    outFF.io.control.enable := UInt(1)
//    outFF.io.data.in := outMux.io.out(i)
//
//    out := outFF.io.data.out
//  }
//}
//
//class CrossbarTests(c: Crossbar) extends PlasticineTester(c) {
//  val config = c.inst.outSelect
//  val ins = Array.fill(c.numInputs) { BigInt(rnd.nextInt(c.w)) }
//  poke(c.io.ins, ins)
////  c.io.ins.zip(ins) foreach {case(inp, in) => poke(inp, in) }
//  val outs = Array.tabulate(c.numOutputs) { i => ins(config(i))}
//  expect(c.io.outs, outs)
////  c.io.outs.zip(outs) foreach {case(out, exp) => expect(out, exp)}
//}
//
//object CrossbarTest {
//  def main(args: Array[String]): Unit = {
//    val (appArgs, chiselArgs) = args.splitAt(args.indexOf("end"))
//
//    if (appArgs.size != 1) {
//      println("Usage: bin/sadl CrossbarTest <pisa config>")
//      sys.exit(-1)
//    }
//
//    val pisaFile = appArgs(0)
//    val configObj = Parser(pisaFile).asInstanceOf[CrossbarConfig]
//    val bitwidth = 8
//    val inputs = 4
//    val outputs = 8
//
//    chiselMainTest(args, () => Module(new Crossbar(bitwidth, inputs, outputs, configObj))) {
//      c => new CrossbarTests(c)
//    }
//  }
//}
//
//class CrossbarVecTests(c: CrossbarVecReg) extends PlasticineTester(c) { }
//object CrossbarVecTest {
//  def main(args: Array[String]): Unit = {
//    val (appArgs, chiselArgs) = args.splitAt(args.indexOf("end"))
//
//    if (appArgs.size != 4) {
//      println("Usage: bin/sadl CrossbarTest <pisa config>")
//      sys.exit(-1)
//    }
//
//    val w = appArgs(0).toInt
//    val v = appArgs(1).toInt
//    val inputs = appArgs(2).toInt
//    val outputs = appArgs(3).toInt
//
//    val configObj = CrossbarConfig.getRandom(outputs)
//    chiselMainTest(args, () => Module(new CrossbarVecReg(w, v, inputs, outputs, configObj))) {
//      c => new CrossbarVecTests(c)
//    }
//  }
//}
