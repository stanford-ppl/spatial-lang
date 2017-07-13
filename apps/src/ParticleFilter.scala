import spatial.dsl._
import org.virtualized._
import spatial.SpatialCompiler
import spatial.interpreter.Interpreter
import spatial.interpreter.Streams

trait ParticleFilter extends SpatialStream {

  type SReal = FltPt[_32,_0]

  @struct case class SQuaternion(r: SReal)

  val N: scala.Int = 10

  @virtualize def prog() = {

    val inIMU  = StreamIn[SReal](In2)        
    val inV  = StreamIn[SQuaternion](In1)
    val out = StreamOut[SQuaternion](Out1)
    val p = 100 (1 -> 100)

    Accel(*) {
      val i = Reg[Int]
      i := i + 1
      val state = SRAM[SQuaternion](N)

      val fifoV = FIFO[SQuaternion](100)
      val fifoIMU = FIFO[SReal](100)

      breakpoint


      Foreach(N by 1)(x => {
        state(x) = SQuaternion(random[SReal])
      })

      out := state(i)

      Stream(*)(x => {
        breakpoint
        fifoV.enq(inV)
      })

      Stream(*)(x => {
        breakpoint
        fifoIMU.enq(inIMU)
      })

      /*
      Stream(*)(x => {
        fifoIMU.enq(inIMU)        
      })
       */
    }
  }

  //Box-Muller
  //http://www.design.caltech.edu/erik/Misc/Gaussian.html
  def gaussian(mean: SReal, variance: SReal) = {
    val r = random[SReal](1.0)
/*    val x1 = Reg[SReal](true)
    f 
    while ( w >= 1.0 );
         do {
                 x1 = 2.0 * ranf() - 1.0;
                 x2 = 2.0 * ranf() - 1.0;
                 w = x1 * x1 + x2 * x2;
         } 

         w = sqrt( (-2.0 * log( w ) ) / w );
         y1 = x1 * w;
         y2 = x2 * w;
 */
    r
  }

  val outs = List(Out1)


  val inputs = Map[Bus, List[MetaAny[_]]](
    (In1 -> List[SReal](3f, 4f, 2f, 6f).map(SQuaternion.apply)),
    (In2 -> List[SReal](3f, 4f, 2f, 6f))
  )
  
  def forceExit() =
    Streams.streamsOut(Out1).size == 4
  
  

}

object ParticleFilterInterpreter extends ParticleFilter with SpatialStreamInterpreter 

object ParticleFilterCompiler extends ParticleFilter with SpatialApp {
  def main() =
    prog()
}



