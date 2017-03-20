import spatial._
import org.virtualized._
import macros._

/*object Video extends SpatialApp {
  import IR._

  override val target = targets.DE1

  type UINT8 = FixPt[FALSE,_8,_0]
  type UINT4 = FixPt[FALSE,_4,_0]

  @struct case class RGB(r: UINT8, g: UINT8, b: UINT8)
  @struct case class RGBA(r: UINT4, g: UINT4, b: UINT4, a: UINT4)

  @virtualize
  def main() {
    val onboardVideo = target.VideoCamera
    val outputVideo: Bus = ??? // target.VGA (not defined yet)
    val input  = StreamIn[RGB](onboardVideo)
    val output = StreamOut[RGBA](outputVideo)

    Accel(*) {
      Pipe {
        val pixel = input.deq()
        val r = pixel.r.to[UINT4]
        val g = pixel.g.to[UINT4]
        val b = pixel.b.to[UINT4]
        val a = 0
        output.enq( RGBA(r,g,b,a) )
      }

    }

  }
}*/
