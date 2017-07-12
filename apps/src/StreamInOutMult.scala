import spatial.dsl._
import org.virtualized._
import spatial.SpatialCompiler
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


