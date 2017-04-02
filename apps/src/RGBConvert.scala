import spatial._
import org.virtualized._
import forge._

object RGBConvert extends SpatialApp {
  import IR._

  override val target = targets.DE1

  type UINT2 = FixPt[FALSE,_2,_0]
  type UINT3 = FixPt[FALSE,_3,_0]
  type UINT5 = FixPt[FALSE,_5,_0]
  type UINT6 = FixPt[FALSE,_6,_0]
  type UINT8 = FixPt[FALSE,_8,_0]

//  @struct case class RtGtBt(rt: Rt, gt: Gt, bt: Bt)
//  @struct case class RGB24(r: UINT5, rt: UINT3, g: UINT6, gt: UINT2, b:UINT5, bt: UINT3)
  @struct case class RGB24(r: UINT8, g: UINT8,  b:UINT8)
  @struct case class RGB(r: UINT5, g: UINT6, b: UINT5)

  @virtualize
  def main() {
    val onboardVideo = target.VideoCamera
    val outputVideo: Bus = target.VGA
    val input  = StreamIn[RGB24](onboardVideo)
    val output = StreamOut[RGB](outputVideo)

    Accel(*) {
      Pipe {
        val pixel = input.value()
        val r = pixel.r.to[UINT5]
        val g = pixel.g.to[UINT6]
        val b = pixel.b.to[UINT5]
        output := RGB(r,g,b) 
      }
    }
  }
}
