package spatial.codegen.pirgen

import argon.Config
import spatial.SpatialExp

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import java.io.PrintStream
import java.nio.file.{Files, Paths}

trait PIRDSE extends PIRSplitting with PIRRetiming {
  val IR: SpatialExp with PIRCommonExp
  import IR._

  override val name = "Plasticine DSE"
  override val recurse = Always

  val mappingIn = mutable.HashMap[Expr, List[CU]]()

  val cus = ArrayBuffer[CU]()

  override def process[S:Type](b: Block[S]) = {
    super.run(b)
    dse()
    b
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]) {
    if (isControlNode(lhs) && mappingIn.contains(lhs))
      cus ++= mappingIn(lhs)
  }

  def dse() {
    dbg(s"Running design space exploration")
    this.silence()

    val pwd = sys.env("SPATIAL_HOME")
    val dir = s"$pwd/csvs"
    Files.createDirectories(Paths.get(dir))

    val name = Config.name
    val valid = new PrintStream(s"$dir/$name.csv")
    val invalid = new PrintStream(s"$dir/${name}_invalid.csv")

    val header = Utilization()
    invalid.println("Scl/Bus, SIns_PCU, VIns_PCU, Vouts_PCU, Stages, SIns_PMU, VIns_PMU, VOuts_PMU")
    valid.println  ("Scl/Bus, SIns_PCU, VIns_PCU, Vouts_PCU, Stages, SIns_PMU, VIns_PMU, VOuts_PMU, " + header.heading +
                    ", #ALU,#SRAM,#Vin,#Vout, ALU Util, SRAM Util, VecIn Util, VecOut Util, " +
                    ", SIn/Unit, SOut/Unit, VIn/Unit, VOut/Unit, SIn/Stage, VIn/Stage")

    // Total: ~38,000 combinations...
    var pass = 0
    var fail = 0
    var first: String = ""

    for (vIns_PCU <- 2 to 6 ; // 5
        vOuts_PCU <- 1 to 3 ; // 3
        stages <- 0 to 10 ; // 10
        sbus <- List(1,2,4) ; // 3
        sIns_PCU <- 2 to Math.min(vIns_PCU*sbus,16) by 2;
        vIns_PMU <- vIns_PCU to 6;
        vOuts_PMU <- vOuts_PCU to 3;
        sIns_PMU <- sIns_PCU to Math.min(vIns_PMU*sbus,16) by 2) {

    // 8  --- implies existence of a vIns*sbus : sIns crossbar (or some other selection mechanism)
      STAGES = stages
      SCALARS_PER_BUS = sbus

      var others = ArrayBuffer[CU]()
      val pcu = CUCost(sIn=sIns_PCU, vIn=vIns_PCU, vOut=vOuts_PCU, comp=stages)
      val mcu = MUCost(sIn=sIns_PMU, vIn=vIns_PMU, vOut=vOuts_PMU, read=READ_WRITE, write=READ_WRITE)

      val text: String = s"sbus=$sbus, sIn_PCU=$sIns_PCU, vIn_PCU=$vIns_PCU, vOut_PCU=$vOuts_PCU, comps=$stages, " +
                         s"sIn_PMU=$sIns_PMU, vIn_PMU=$vIns_PMU, vOut_PMU=$vOuts_PMU, read/write=$READ_WRITE"

      val settingsCSV: String = s"$sbus, $sIns_PCU, $vIns_PCU, $vOuts_PCU, $stages, $sIns_PMU, $vIns_PMU, $vOuts_PMU"

      try {
        var stats = Utilization()

        for (orig <- cus) {
          val split = splitCU(orig, pcu, mcu, others)
          retime(split, others)

          for (cu <- split) {
            val cost = getUtil(cu, others)

            stats += cost
            others += cu
          }
        }
        val nPCUs = stats.pcus
        val nPMUs = stats.pmus

        val nALUs = (LANES * nPCUs * stages) + (nPMUs * READ_WRITE)
        val nMems = nPMUs
        val nVIns = (vIns_PCU * nPCUs) + (vIns_PMU * nPMUs)
        val nVOut = (vOuts_PCU * nPCUs) + (vOuts_PMU * nPMUs)

        val aluUtil = stats.alus.toFloat / nALUs
        val memUtil = stats.mems.toFloat / nMems
        val vInUtil = stats.vecIn.toFloat / nVIns
        val vOutUtil = stats.vecOut.toFloat / nVOut

        val avgSIn  = stats.sclIn.toFloat / (nPCUs + nPMUs)
        val avgSOut = stats.sclOut.toFloat / (nPCUs + nPMUs)
        val avgVIn  = stats.vecIn.toFloat / (nPCUs + nPMUs)
        val avgVOut = stats.vecOut.toFloat / (nPCUs + nPMUs)

        val sInPerStage = stats.sclIn.toFloat / (stats.alus.toFloat / LANES)
        val vInPerStage = stats.vecIn.toFloat / (stats.alus.toFloat / LANES)

        if (pass == 0) first = text
        pass += 1

        System.out.println(text + ": PASS")
        valid.println(settingsCSV + ", " + stats.toString +
                      s",$nALUs,$nMems,$nVIns,$nVOut, $aluUtil, $memUtil, $vInUtil, $vOutUtil, " +
                      s",$avgSIn,$avgSOut,$avgVIn,$avgVOut, $sInPerStage, $vInPerStage")
      }
      catch {case e:SplitException =>
        fail += 1
        System.out.println(text + ": FAIL")
        dbg(e.msg)
        invalid.println(settingsCSV)
      }
    }
    valid.close()
    invalid.close()

    Console.println(s"Pass: $pass (${100.0f * pass.toFloat / (pass + fail)})%")
    Console.println(s"Fail: $fail (${100.0f * fail.toFloat / (pass + fail)})%")
    Console.println(s"Smallest: $first")
  }

}
