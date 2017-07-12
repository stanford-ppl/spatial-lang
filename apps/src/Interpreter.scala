import spatial.dsl._
import org.virtualized._
import spatial.SpatialCompiler
import spatial.interpreter.Streams
import argon.interpreter.{Interpreter => AInterpreter}
import spatial.interpreter.Interpreter
import spatial.SpatialConfig
import argon.core.Config
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

  def prog(): Unit
  def inputs: Map[Bus, List[Exp[_]]]
  def outs: List[Bus]
  def forceExit(): scala.Boolean

  private var __stagingArgs = scala.Array[java.lang.String]()
  override def stagingArgs = scala.Array[java.lang.String]("--interpreter") ++ __stagingArgs

  def runI() {    
    initConfig(stagingArgs)
    compileProgram(() => prog())
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

    Config.forceExit = () => forceExit()
    Config.exit = () => exit()

    inputs.foreach { case (bus, content) =>
      Streams.addStreamIn(bus)
      content.foreach(x => Streams.streamsIn(bus).put(x))
    }

    outs.foreach(Streams.addStreamOut)

    runI()

    exit()
  }

}


object StreamInOutMult2 extends SpatialStream with SpatialStreamInterpreter {
  
  @virtualize def prog(): spatial.dsl.Unit = {
    val in  = StreamIn[Int](In1)
    val out = StreamOut[Int](Out1)
    Accel(*) {
      out := in + 4
    }
  }

  val outs = List(In1)

  val inputs = Map[Bus, List[Exp[_]]](
    (In1 -> List[Int](3, 4, 2, 6).map(_.s))
  )


  def forceExit() =
    Streams.streamsOut(Out1).size == 4
  

}
