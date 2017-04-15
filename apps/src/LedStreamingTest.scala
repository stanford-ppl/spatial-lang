import spatial._
import org.virtualized._
import forge._

object LedStreamingTest extends SpatialApp {
  import IR._

  override val target = targets.DE1

  type UINT2 = FixPt[FALSE,_2,_0]
  type UINT3 = FixPt[FALSE,_3,_0]
  type UINT5 = FixPt[FALSE,_5,_0]
  type UINT6 = FixPt[FALSE,_6,_0]
  type UINT9 = FixPt[FALSE,_9,_0]
  type UINT7 = FixPt[FALSE,_7,_0]
  type UINT32 = FixPt[False,_32,_0]

  //TODO: Here we will need to make two case classes to define StreamIn and StreamOut.
  // In Spatial, the case class uses little endian, meaning that LSB is stored first.
  // For example, say you want to describe RGB using a case class. Then you will write: 
  // @struct case class BGR(b: ..., g: ..., r: ...)
  //
  // On DE1SoC, the input RGB stream uses bits [23:19] for r, [15:10] for g, and [7:3] for b
  // the output RGB stream uses bits [15:11] for r, [10:5] for g, and [4:0] for b

  @virtualize

  val outputLEDR: Bus = target.LEDR
  val output = StreamOut[UINT32](outputLEDR)
  def main() {
    // val onboardVideo = target.VideoCamera
    // val outputVideo: Bus = target.VGA
    // val input  = StreamIn[bBgGrR](onboardVideo)
    // val output = StreamOut[BGR](outputVideo)

    Accel(*) {
      outputLEDR := 2.to[Int]
    }
  }
}
