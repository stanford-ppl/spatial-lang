package spatial.models.altera

import argon.core._
import argon.nodes._
import forge._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

class StratixVAreaModel extends AlteraAreaModel {
  private val areaFile = "StratixV-Routing.csv"

  override val MAX_PORT_WIDTH: Int = 40
  override def bramWordDepth(width: Int): Int = {
    if      (width == 1) 16384
    else if (width == 2) 8192
    else if (width <= 5) 4096
    else if (width <= 10) 2048
    else if (width <= 20) 1024
    else 512
  }

  // TODO: Move these to the first couple of lines in the CSV file
  //                    LUT7      LUT6      LUT5      LUT4      LUT3      MEM64     MEM32     MEM16     Regs       DSPs    BRAM
  val maxValues = Array(262400.0, 262400.0, 524800.0, 524800.0, 524800.0, 131200.0, 262400.0, 262400.0, 1049600.0, 1963.0, 2567.0,
    /* RBRAM    R. LUTs   F. Regs    Unavail   Impl.     Needed  */
       10268.0, 524800.0, 1049600.0, 262400.0, 262400.0, 262400.0)

  import AreaNeuralModel._
  class LUTRoutingModel extends AreaNeuralModel(name="RoutingLUTs", filename=areaFile, OUT=RLUTS, LAYER2=6)
  class RegFanoutModel extends AreaNeuralModel(name="FanoutRegisters", filename=areaFile, OUT=FREGS, LAYER2=6)
  class UnavailALMsModel extends AreaNeuralModel(name="UnavailableALMs", filename=areaFile, OUT=UNVAIL, LAYER2=6)

  lazy val lutModel = new LUTRoutingModel
  lazy val regModel = new RegFanoutModel
  lazy val almModel = new UnavailALMsModel

  def init(): Unit = {
    lutModel.init()
    regModel.init()
    almModel.init()
  }

  @stateful override def areaOf(e: Exp[_], d: Def, inHwScope: Boolean, inReduce: Boolean): AlteraArea = d match {
    case FltAdd(_,_) if e.tp == FloatType => AlteraArea(lut3=397,lut4=29,lut5=125,lut6=34,lut7=5,regs=606,mem16=50) // ~372 ALMs, 1 DSP (around 564)
    case FltSub(_,_) if e.tp == FloatType => AlteraArea(lut3=397,lut4=29,lut5=125,lut6=34,lut7=5,regs=606,mem16=50)
    case FltMul(_,_) if e.tp == FloatType => AlteraArea(lut3=152,lut4=10,lut5=21,lut6=2,dsps=1,regs=335,mem16=43)   // ~76 ALMs, 1 DSP (around 1967)
    case FltDiv(_,_) if e.tp == FloatType => AlteraArea(lut3=2384,lut4=448,lut5=149,lut6=385,lut7=1,regs=3048,mem32=25,mem16=9)
    case FltLt(a,_)  if a.tp == FloatType => AlteraArea(lut4=42,lut6=26,regs=33)
    case FltLeq(a,_) if a.tp == FloatType => AlteraArea(lut4=42,lut6=26,regs=33)
    case FltNeq(a,_) if a.tp == FloatType => AlteraArea(lut4=42,lut6=26,regs=33)
    case FltEql(a,_) if a.tp == FloatType => AlteraArea(lut4=42,lut6=26,regs=33)
    case FltExp(_)   if e.tp == FloatType => AlteraArea(lut3=368,lut4=102,lut5=137,lut6=38,mem16=24,regs=670,dsps=5,sram=2)
    case FltSqrt(_)  if e.tp == FloatType => AlteraArea(lut3=476,lut4=6,lut5=6,mem32=11,regs=900)
    case FixPtToFltPt(_) if e.tp == FloatType => AlteraArea(lut4=50,lut6=132,regs=238)
    case FltPtToFixPt(x) if x.tp == FloatType => AlteraArea(lut4=160,lut6=96,regs=223+nbits(e))

    case x: DenseTransfer[_,_] if x.isLoad  => AlteraArea(lut3=410,lut4=50,lut5=70,lut6=53,          regs=920,  channels=1) // ~353 ALMs
    case x: DenseTransfer[_,_] if x.isStore => AlteraArea(lut3=893,lut4=91,lut5=96,lut6=618,lut7=10, regs=4692, channels=1) // ~1206 ALMs

    case _ => super.areaOf(e,d,inHwScope,inReduce)
  }

  @stateful def summarize(area: AlteraArea): AlteraAreaSummary = {
    // TODO
    AlteraAreaSummary(0,0,0,0,0)
  }

}


