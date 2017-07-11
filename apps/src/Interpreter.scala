import spatial.dsl._
import org.virtualized._
import spatial.SpatialCompiler
import spatial.interpreter.IStream
import spatial.interpreter.Interpreter
import spatial.SpatialConfig

trait SpatialStream extends SpatialCompiler {
  case object In extends Bus {
    def length: scala.Int = 42
  }
  case object Out extends Bus {
    def length: scala.Int = 42
  }
}

trait SpatialStreamInterpreter {
  self: SpatialStream =>

  def prog(): Unit
  def inputs: List[Exp[_]]
  def outMaxLength: scala.Int

  override def stagingArgs = scala.Array[java.lang.String]("--interpreter", "--debug")

  def runI() {    
    initConfig(stagingArgs)
    compileProgram(() => prog())
  }


  def main(args: scala.Array[java.lang.String]) {

    IStream.addStreamIn(In)

    inputs.foreach(x => IStream.streamsIn(In).put(x))

    val interpreter = new Thread(new Runnable {
      def run() {
        runI()
      }
    })

    interpreter.start()

    if (!SpatialConfig.debug) {
      while (!Interpreter.closed && (!IStream.streamsOut.contains(Out) || IStream.streamsOut(Out).size < outMaxLength)) {
        Thread.sleep(1000)
      }

      Interpreter.closed = true
      interpreter.join()

      Console.println(IStream.streamsOut(Out))
    }
  }

}


object StreamInOutMult2 extends SpatialStream with SpatialStreamInterpreter {

  @virtualize def prog(): spatial.dsl.Unit = {
    val in  = StreamIn[Int](In)
    val out = StreamOut[Int](Out)
    Accel(*) {
      out := in + 4
    }
  }

  val listIn: List[Int] = List(3, 4, 2, 6)
  val inputs = listIn.map(x => x.s)

  val outMaxLength = listIn.length
  

}
