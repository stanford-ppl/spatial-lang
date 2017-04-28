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
      readWrite <- 1 to 10;      // 10
      regs_PMU <- 1 to 16        // 16
    ) {
      mcu = MUCost(sIn=sIns_PMU, sOut=sOuts_PMU, vIn=vIns_PMU, vOut=vOuts_PMU, read=readWrite, write=readWrite, regsMax=regs_PMU)

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

    val MUCost(sIns_PMU,sOuts_PMU,vIns_PMU,vOuts_PMU,readWrite,_,regsMax_PMU,_) = mcu
    READ_WRITE = readWrite

    val pmuText = s"r/w=$readWrite, sIn_PMU=$sIns_PMU, sOut_PMU=$sOuts_PMU, vIn_PMU=$vIns_PMU, vOut_PMU=$vOuts_PMU"
    val pmuSettings = s"$sIns_PMU, $sOuts_PMU, $vIns_PMU, $vOuts_PMU, $readWrite"

    // Can't have less than REDUCE_STAGES stages (otherwise no room to do reduce)
    val results = (REDUCE_STAGES to 10).flatMap{stages =>
      STAGES = stages
      Console.print("stages = " + stages)
      val start = System.currentTimeMillis()

      val result = (2 to 16 by 2).par.map{regsMax_PCU =>
        val maxSOut = Math.min(10, regsMax_PCU)  // Can't have more outputs than the number of live registers
        val maxVOut = Math.min(6, regsMax_PCU)

        val entries = new Array[String](9*6*maxSOut*maxVOut)

        var pass = 0
        var fail = 0
        var first: String = ""

        for (
          //stages    <- 1 to 10;     // 10
          //sIns_PCU  <- 1 to 10;     // 10
          vIns_PCU  <- 2 to 10;       // 9
          sIns_PCU  <- 1 +: (2 to 10 by 2); // 6
          sOuts_PCU <- 1 to maxSOut;   //
          vOuts_PCU <- 1 to maxVOut    //
        ) {
          val n = pass + fail + 1
          //val perc = (100 * n) / 3465

          var others = ArrayBuffer[CU]()
          val pcu = CUCost(sIn=sIns_PCU, sOut=sOuts_PCU, vIn=vIns_PCU, vOut=vOuts_PCU, comp=stages, regsMax=regsMax_PMU)

          val text: String = s"stages=$stages, sIn_PCU=$sIns_PCU, sOut_PCU=$sOuts_PCU, vIn_PCU=$vIns_PCU, vOut_PCU=$vOuts_PCU, " + pmuText

          val settingsCSV: String = s"$sIns_PCU, $sOuts_PCU, $vIns_PCU, $vOuts_PCU, $stages, " + pmuSettings

          try {
            var util = Utilization()

            for (orig <- cus) {
              val split = splitCU(orig, pcu, mcu, others)
              retime(split, others)

              for (cu <- split) {
                val cost = getUtil(cu, others)

                util += cost
                others += cu
              }
            }
            val pcuOnly = others.filter(_.isPCU).map{cu => getUtil(cu, others.filterNot(_ == cu)) }.fold(Utilization()){_+_}
            val pmuOnly = others.filter(_.isPMU).map{cu => getUtil(cu, others.filterNot(_ == cu)) }.fold(Utilization()){_+_}

            val stats = Statistics(
              /** Utilization **/
              total = util,
              pcuOnly = pcuOnly,
              pmuOnly = pmuOnly,
              /** PCUS **/
              sIn_PCU  = sIns_PCU,
              sOut_PCU = sOuts_PCU,
              vIn_PCU  = vIns_PCU,
              vOut_PCU = vOuts_PCU,
              stages   = stages,
              regs_PCU = regsMax_PCU,
              /** PMUs **/
              sIn_PMU   = sIns_PMU,
              sOut_PMU  = sOuts_PMU,
              vIn_PMU   = vIns_PMU,
              vOut_PMU  = vOuts_PMU,
              readWrite = readWrite,
              regs_PMU  = regsMax_PMU
            )

            if (pass == 0) first = text
            pass += 1

            //Console.println(s"$n [$perc%]: " + text + s": PASS [PCUs:$nPCUs/PMUs:$nPMUs]")
            entries(n-1) = "P" + (settingsCSV + "," + stats.toCSV)
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

    invalid.println("SIns_PCU, SOuts_PCU, VIns_PCU, Vouts_PCU, Stages, SIns_PMU, SOuts_PMU, VIns_PMU, VOuts_PMU, R/W")
    valid.println("SIns_PCU, SOuts_PCU, VIns_PCU, Vouts_PCU, Stages, SIns_PMU, SOuts_PMU, VIns_PMU, VOuts_PMU, R/W, " + Statistics.header)

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
