import spatial.dsl._
import org.virtualized._
import spatial.SpatialCompiler
import spatial.interpreter.IStream
import spatial.interpreter.Interpreter

object ParticleFilter extends SpatialStreamInterpreter {

  type SReal = FltPt[_32,_0]  
  @struct case class SQuaternion(r: SReal)

  @virtualize def prog() = {

    val in  = StreamIn[SReal](In)
    val out = StreamOut[SQuaternion](Out)

    Accel(*) {
      out := SQuaternion(in + 4)
    }
  }

  val listIn: List[SReal] = List(3f, 4f, 2f, 6f)
  val inputs = listIn.map(x => x.s)

  val outMaxLength = listIn.length
  

}



