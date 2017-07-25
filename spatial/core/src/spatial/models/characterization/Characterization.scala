package spatial.models.characterization

import spatial._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

trait AllBenchmarks extends Benchmarks
  with Primitives
  with RegFiles
  with Regs
  with SRAMs

object Characterization extends SpatialCompiler with AllBenchmarks {
  // Untested area extraction code
  def area(dir: JString): Map[JString, scala.Int] = {
    val pwd = "$pwd".!!
    "cd $dir".!
    "make zynq".!
    val reportFile = Source.fromFile("verilog-zynq/project_1/project_1.runs/impl_1/design_1_wrapper_utilization_placed.rpt")4
    val contents = source.getLines.mkString
    val pattern = "(| Slice LUTs                 |\s*) ([0-9]+)".r
    val pattern(prefix, area) = contents
    "cd $pwd".!
    Map((dir, area))
  }

  def storeArea(name: JString, area: Map[JString, scala.Int]) = {
    Console.println(name, area)
  }

  val NUM_PAR_SYNTH: scala.Int = 2
  val stagingArgs = scala.Array("--synth")

  def main(args: scala.Array[JString]) {
    val programs: Seq[NamedSpatialProg] = gens.flatMap(_.expand)

    val chiseled = programs.map(x => {
      //compileProgram(x._2)
      Thread.sleep(1000)
      Console.println(x._1 + " chisel generated ")
      x._1
    })

    val exec = java.util.concurrent.Executors.newFixedThreadPool(NUM_PAR_SYNTH)
    implicit val ec = ExecutionContext.fromExecutor(exec)

    val workers = chiseled.map(x => Future {
      storeArea(x, area(x))
    })

    workers.foreach(Await.ready(_, Duration.Inf))
    Console.println("COMPLETED")
    exec.shutdown()
  }

}
