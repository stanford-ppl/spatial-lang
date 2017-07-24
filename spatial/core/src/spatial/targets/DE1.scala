package spatial.targets
import argon.core.State
import spatial.analysis.AreaAnalyzer
import spatial.models.altera._
import spatial.models.{AreaMetric, AreaModel, LatencyModel}

object DE1 extends FPGATarget {
  val name = "DE1"
  def burstSize = 96 

  case object VideoCamera extends Bus {def length = 16}
  case object VGA extends Bus {def length = 16}
  case object SliderSwitch extends Bus {def length = 10}
  case object LEDR extends Bus {
    def length = 10; 
    val LEDR0 = 1;
    val LEDR1 = 2;
    val LEDR2 = 4;
    val LEDR3 = 8;
    val LEDR4 = 16;
    val LEDR5 = 32;
    val LEDR6 = 64;
    val LEDR7 = 128;
    val LEDR8 = 256;
    val LEDR9 = 512;
  }

  case object GPInput1 extends Bus { def length = 32 }
  case object GPOutput1 extends Bus { def length = 32 }
  case object GPInput2 extends Bus { def length = 32 }
  case object GPOutput2 extends Bus { def length = 32 }

  // FIXME: No models for DE1 yet
  override type Area = AlteraArea
  override type Sum = AlteraAreaSummary
  override def areaMetric: AreaMetric[AlteraArea] = AlteraAreaMetric
  override lazy val areaModel: AreaModel[Area,Sum] = new StratixVAreaModel
  override lazy val latencyModel: LatencyModel = new StratixVLatencyModel
  override def capacity: AlteraAreaSummary = AlteraAreaSummary(alms=262400,regs=524800,dsps=1963,bram=2567,channels=13)
}
