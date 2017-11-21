package spatial.codegen.pirgen

import java.io.PrintWriter
import java.nio.file.{Files, Paths}

import argon.core._

import scala.collection.mutable

class PIRStats(implicit val codegen:PIRCodegen) extends PIRTraversal {
  override val name = "PIR CU Stats"
  override val recurse = Always
  var IR = codegen.IR

  override protected def process[S:Type](block: Block[S]): Block[S] = {
    statsFile
    tallyCUs
    block
  }

  def statsFile = {
    val pwd = sys.env("SPATIAL_HOME")
    val dir = s"$pwd/csvs"
    Files.createDirectories(Paths.get(dir))
    val file = new PrintWriter(s"$dir/${config.name}_unsplit.csv")
    cus.filter{ cu => cu.allStages.nonEmpty || cu.isPMU }.foreach{ cu =>
      val isPCU = if (cu.isPCU) 1 else 0
      val util = getUtil(cu, cus)
      val line = s"$isPCU, ${cu.lanes},${util.stages},${util.addr},${util.regsMax},${util.vecIn},${util.vecOut},${util.sclIn},${util.sclOut}"
      file.println(line)
    }
    file.close()
  }

  def tallyCUs: Unit = {
    val total = cus.map { cu => getUtil(cu, cus) }.fold(Utilization()){_+_}

    val pcuOnly = cus.filter(_.isPCU).map{cu =>
      val util = getUtil(cu, cus)
      dbgs(s"$cu: ")
      reportUtil(util)
      util
    }.fold(Utilization()){_+_}

    val pmuOnly = cus.filter(_.isPMU).map{cu => getUtil(cu, cus) }.fold(Utilization()){_+_}

    val stats = Statistics(total, pcuOnly, pmuOnly)
    stats.makeReport()
  }

}
