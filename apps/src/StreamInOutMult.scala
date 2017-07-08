import spatial.dsl._
import org.virtualized._
import spatial.SpatialCompiler
import spatial.interpreter.IStream
import spatial.interpreter.Interpreter

object StreamInOutMult extends SpatialApp {
  import spatial.targets.DE1

  @virtualize def main(): Unit = {
    val in  = StreamIn[Int](DE1.GPInput1)
    val out = StreamOut[Int](DE1.GPOutput1)
    Accel(*) {
      out := in + 4
    }
  }
}

object StreamInOutMult2 extends SpatialCompiler {
  import spatial.targets.DE1

  @virtualize def prog(): Unit = {
    val in  = StreamIn[Int](DE1.GPInput1)
    val out = StreamOut[Int](DE1.GPOutput1)
    Accel(*) {
      out := in + 4
    }
  }

  override def stagingArgs = scala.Array[java.lang.String]("--interpreter")

  def runI() {
    initConfig(stagingArgs)
    compileProgram(() => prog())
  }

  val in = "GPInput1"
  val out = "GPOutput1"  

  def main(args: scala.Array[java.lang.String]) {

    IStream.addStreamIn("GPInput1")
    val l: List[Int] = List(3, 4, 2, 6)
    val le = l.map(_.s)   
    le.foreach(x => IStream.streamsIn(in).put(x))

    val interpreter = new Thread(new Runnable {
      def run() {
        runI()
      }
    })

    interpreter.start()
    Thread.sleep(2000)
    Interpreter.closed = true
    interpreter.join()
    Console.println(IStream.streamsOut(out))

  }
}
