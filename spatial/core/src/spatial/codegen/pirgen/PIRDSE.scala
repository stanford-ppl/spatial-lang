package spatial.codegen.pirgen

import argon.Config
import spatial.{SpatialConfig, SpatialExp}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import java.io.PrintStream
import java.nio.file.{Files, Paths}

import scala.util.control.Breaks._

trait PIRDSE extends PIRSplitting with PIRRetiming {
  val IR: SpatialExp with PIRCommonExp
  import IR._

  override val name = "Plasticine DSE"
  override val recurse = Always

  val mappingIn = mutable.HashMap[Expr, List[CU]]()

  val cus = ArrayBuffer[CU]()

  override def process[S:Type](b: Block[S]) = {
    visitBlock(b)
    dse()
    b
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]) {
    if (mappingIn.contains(lhs)) cus ++= mappingIn(lhs)
  }

  def dse() {
    val prevVerbosity = Config.verbosity
    this.silence()
    Config.verbosity = -1
    Console.println(s"Running design space exploration")

    val unrestrictedPCU = CUCost(sIn=100,sOut=100,vIn=100,vOut=100,comp=10000)
    var mcu = MUCost()
    var foundMCU = false
    breakable{ for(
      sIns_PMU <- 1 to 10;       // 10
      sOuts_PMU <- 0 to 2;       // 2
      vIns_PMU <- 2 to 6;        // 5
      vOuts_PMU <- 1 to 1;       // 1
      readWrite <- 1 to 10       // 10
    ) {
      mcu = MUCost(sIn=sIns_PMU, sOut=sOuts_PMU, vIn=vIns_PMU, vOut=vOuts_PMU, read=readWrite, write=readWrite)

      var others = ArrayBuffer[CU]()

      try {
        for (orig <- cus) {
          val split = splitCU(orig, unrestrictedPCU, mcu, others)
          retime(split, others)
          split.foreach{cu => others += cu }
        }
        foundMCU = true
        break // Ugly, but it works.
      }
      catch {case e:SplitException => }
    }}

    if (!foundMCU) throw new Exception("Unable to find minimum MCU parameters")

    val MUCost(sIns_PMU,sOuts_PMU,vIns_PMU,vOuts_PMU,readWrite,_) = mcu
    READ_WRITE = readWrite

    val results = (1 to 10).flatMap{stages =>
      STAGES = stages
      Console.print("stages = " + stages)
      val start = System.currentTimeMillis()

      val result = (1 to 10).par.map{sIns_PCU =>
        val maxOut = stages

        val entries = new Array[String](9*maxOut*maxOut)

        var pass = 0
        var fail = 0
        var first: String = ""

        for (
          //stages    <- 1 to 10;     // 10
          //sIns_PCU  <- 1 to 10;     // 10
          vIns_PCU  <- 2 to 10;       // 9
          sOuts_PCU <- 1 to maxOut;   //
          vOuts_PCU <- 1 to maxOut    //
        ) {
          val n = pass + fail + 1
          val perc = (100 * n) / 3465

          var others = ArrayBuffer[CU]()
          val pcu = CUCost(sIn=sIns_PCU, sOut=sOuts_PCU, vIn=vIns_PCU, vOut=vOuts_PCU, comp=stages)

          val text: String = s"stages=$stages, sIn_PCU=$sIns_PCU, sOut_PCU=$sOuts_PCU, vIn_PCU=$vIns_PCU, vOut_PCU=$vOuts_PCU, " +
            s"r/w=$readWrite, sIn_PMU=$sIns_PMU, sOut_PMU=$sOuts_PMU, vIn_PMU=$vIns_PMU, vOut_PMU=$vOuts_PMU"

          val settingsCSV: String = s"$sIns_PCU, $sOuts_PCU, $vIns_PCU, $vOuts_PCU, $stages, $sIns_PMU, $sOuts_PMU, $vIns_PMU, $vOuts_PMU"

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
            val pcuOnly = others.filter(_.isPCU).map{cu => getUtil(cu, others.filterNot(_ == cu)) }.fold(Utilization()){_+_}

            val nALUs = (LANES * nPCUs * stages) + (nPMUs * readWrite)
            val nMems = nPMUs
            val nSIns = (sIns_PCU * nPCUs) + (sIns_PMU * nPMUs)
            val nSOut = (sOuts_PCU * nPCUs) + (sOuts_PMU * nPMUs)
            val nVIns = (vIns_PCU * nPCUs) + (vIns_PMU * nPMUs)
            val nVOut = (vOuts_PCU * nPCUs) + (vOuts_PMU * nPMUs)

            val aluUtil = stats.alus.toFloat / nALUs
            val memUtil = stats.mems.toFloat / nMems
            val sInUtil  = stats.sclIn.toFloat / nSIns
            val sOutUtil = stats.sclOut.toFloat / nSOut
            val vInUtil = stats.vecIn.toFloat / nVIns
            val vOutUtil = stats.vecOut.toFloat / nVOut

            /** PCU only **/
            val aluUtil_PCU  = pcuOnly.alus.toFloat / (LANES * nPCUs * stages)
            val sInUtil_PCU  = pcuOnly.sclIn.toFloat / (sIns_PCU * nPCUs)
            val sOutUtil_PCU = pcuOnly.sclOut.toFloat / (sOuts_PCU * nPCUs)
            val vInUtil_PCU  = pcuOnly.vecIn.toFloat / (vIns_PCU * nPCUs)
            val vOutUtil_PCU = pcuOnly.vecOut.toFloat / (vOuts_PCU * nPCUs)

            val avgSIn_PCU  = pcuOnly.sclIn.toFloat / nPCUs
            val avgSOut_PCU = pcuOnly.sclOut.toFloat / nPCUs
            val avgVIn_PCU  = pcuOnly.vecIn.toFloat / nPCUs
            val avgVOut_PCU = pcuOnly.vecOut.toFloat / nPCUs

            val sInPerStage_PCU  = pcuOnly.sclIn.toFloat / (pcuOnly.stages + pcuOnly.addr)
            val sOutPerStage_PCU = pcuOnly.sclOut.toFloat / (pcuOnly.stages + pcuOnly.addr)
            val vInPerStage_PCU  = pcuOnly.vecIn.toFloat / (pcuOnly.stages + pcuOnly.addr)
            val vOutPerStage_PCU = pcuOnly.vecOut.toFloat / (pcuOnly.stages + pcuOnly.addr)

            if (pass == 0) first = text
            pass += 1

            //Console.println(s"$n [$perc%]: " + text + s": PASS [PCUs:$nPCUs/PMUs:$nPMUs]")
            entries(n-1) = "P" + (settingsCSV + ", " + stats.toString +
              s",$nALUs, $nMems, $nSIns, $nSOut, $nVIns, $nVOut," +
              s",$aluUtil, $memUtil, $sInUtil, $sOutUtil, $vInUtil, $vOutUtil, " +
              s",$aluUtil_PCU, $sInUtil_PCU, $sOutUtil_PCU, $vInUtil_PCU, $vOutUtil_PCU, " +
              s",$avgSIn_PCU, $avgSOut_PCU, $avgVIn_PCU, $avgVOut_PCU, " +
              s",$sInPerStage_PCU, $sOutPerStage_PCU, $vInPerStage_PCU, $vOutPerStage_PCU")
          }
          catch {case e:SplitException =>
            fail += 1
            //Console.println(s"$n [$perc%]: " + text + ": FAIL")
            //dbg(e.msg)
            entries(n-1) = "F" + settingsCSV
          }
        }
        (entries, pass, fail, first)
      }

      val end = System.currentTimeMillis()
      Console.println(" [" + (end - start)/1000 + " sec]")
      result
    }


    val pwd = sys.env("SPATIAL_HOME")
    val dir = s"$pwd/csvs"
    Files.createDirectories(Paths.get(dir))

    val name = Config.name
    val valid = new PrintStream(s"$dir/$name.csv")
    val invalid = new PrintStream(s"$dir/${name}_invalid.csv")

    Config.verbosity = prevVerbosity
    Console.print(s"Writing results to file $dir/$name.csv...")

    val header = Utilization()
    invalid.println("SIns_PCU, SOuts_PCU, VIns_PCU, Vouts_PCU, Stages, SIns_PMU, SOuts_PMU, VIns_PMU, VOuts_PMU")
    valid.println  ("SIns_PCU, SOuts_PCU, VIns_PCU, Vouts_PCU, Stages, SIns_PMU, SOuts_PMU, VIns_PMU, VOuts_PMU, " + header.heading +
      ", #ALU, #SRAM, #SIn, #SOut, #Vin, #Vout," +
      ", ALU Util, SRAM Util, SIn Util, SOut Util, VecIn Util, VecOut Util, " +
      ", ALU Util (PCU), SIn Util (PCU), SOut Util (PCU), VIn Util (PCU), VOut Util (PCU), " +
      ", SIn/PCU, SOut/PCU, VIn/PCU, VOut/PCU," +
      ", SIn/Stage (PCU), SOut/Stage (PCU), VIn/Stage (PCU), VOut/Stage (PCU)")

    results.foreach{case (entries, _, _, _) =>
      entries.foreach{entry =>
        if (entry.startsWith("P")) valid.println(entry.drop(1))
        else invalid.println(entry.drop(1))
      }
    }
    valid.close()
    invalid.close()

    val pass = results.map(_._2).sum
    val fail = results.map(_._3).sum
    val first = results.find{x => x._2 > 0}.map(_._4)

    Console.println("done.")
    Console.println(s"Pass: $pass (${100.0f * pass.toFloat / (pass + fail)}%)")
    Console.println(s"Fail: $fail (${100.0f * fail.toFloat / (pass + fail)}%)")
    if (first.isDefined) Console.println(s"Smallest: ${first.get}")
  }

}
