import spatial.dsl._
import org.virtualized._
import spatial.SpatialCompiler
import spatial.interpreter.IStream
import spatial.interpreter.Interpreter

trait ParticleFilter extends SpatialStream {

  type SReal = FltPt[_32,_0]

  @struct case class SQuaternion(r: SReal)

  @virtualize def prog() = {

    val in  = StreamIn[SQuaternion](In)
    val out = StreamOut[SQuaternion](Out)

//    val m = Matrix(

    val q1 = SQuaternion(3f)

    val a = ArgIn[SQuaternion]
    setArg(a, q1)

    Accel(*) {
      val i = Reg[Int]
      val state = SRAM[SQuaternion](100)
//      if (i.value == 0)
//        state(0) = SQuaternion(2)

      i := i + 1
      out := SQuaternion(in.r + a.value.r + q1.r + i.value.to[SReal])
    }

  }

  val listIn: List[SReal] = List(3f, 4f, 2f, 6f)
  val inputs = listIn.map(x => SQuaternion(x).s)

  val outMaxLength = listIn.length
  

}

object ParticleFilterInterpreter extends ParticleFilter with SpatialStreamInterpreter 

object ParticleFilterCompiler extends ParticleFilter with SpatialApp {
  def main() =
    prog()
}



