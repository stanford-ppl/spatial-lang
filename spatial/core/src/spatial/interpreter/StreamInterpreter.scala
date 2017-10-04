package spatial.interpreter

import spatial.dsl._
import org.virtualized._
import spatial.SpatialCompiler
import argon.interpreter.{Interpreter => AInterpreter}
import argon.core.Const
import scala.collection.JavaConverters._
import argon.lang.typeclasses.Bits

trait SpatialStream extends SpatialCompiler {
  abstract class IBus extends Bus {
    def length: scala.Int = -1
  }

  case object In1 extends IBus
  case object In2 extends IBus
  case object In3 extends IBus
  case object In4 extends IBus
  case object In5 extends IBus

  case object Out1 extends IBus
  case object Out2 extends IBus

}

trait SpatialStreamInterpreter {
  self: SpatialStream =>

  def inputs: Map[Bus, List[MetaAny[_]]]
  def outs: List[Bus]

  def spatial(): Unit

  private var __stagingArgs = scala.Array[java.lang.String]()
  override def stagingArgs = scala.Array[java.lang.String]("--interpreter") ++ __stagingArgs

  def runI() {    
    init(stagingArgs)
    compileProgram(() => spatial())
  }

  def exit() = {
    val out = outs.map(bus => {
      val c = AInterpreter.stringify(Streams.streamsOut(bus))
      s"$bus: $c"
    }).mkString("\n \n")

    Console.println()
    Console.println(s"${Console.GREEN_B}[result]${Console.RESET}\n$out")
    Console.println()
  }
  

  def main(args: scala.Array[java.lang.String]) {

    __stagingArgs ++= args

    config.exit = () => exit()

    inputs.foreach { case (bus, content) =>
      Streams.addStreamIn(bus)
      content.foreach(x => Streams.streamsIn(bus).put(x.s.asInstanceOf[Const[_]].c))
    }

    outs.foreach(Streams.addStreamOut)

    runI()

    exit()
  }

}

trait SpatialStreamCompiler extends SpatialApp {
  self: SpatialStream =>

  def spatial(): Unit
  
  def main() =
    spatial()
}
