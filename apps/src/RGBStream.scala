import spatial._
import org.virtualized._
import forge._

object RGBStream extends SpatialApp {
  import IR._

  override val target = targets.DE1

  type UINT2 = FixPt[FALSE,_2,_0]
  type UINT3 = FixPt[FALSE,_3,_0]
  type UINT5 = FixPt[FALSE,_5,_0]
  type UINT6 = FixPt[FALSE,_6,_0]
  type UINT9 = FixPt[FALSE,_9,_0]
  type UINT7 = FixPt[FALSE,_7,_0]

//  @struct case class RtGtBt(rt: Rt, gt: Gt, bt: Bt)
//  @struct case class RGB24(r: UINT5, rt: UINT3, g: UINT6, gt: UINT2, b:UINT5, bt: UINT3)
  @struct case class tG(t: UINT3, g: UINT6)
  @struct case class tB(t: UINT2, b: UINT5)
//  @struct case class RtGtBt(r: UINT5, tg: tG, tb: tB, t: UINT3)
 // @struct case class RGB24(r: UINT8, g: UINT8,  b:UINT8)
  @struct case class RtGtBt(tl: UINT3, b: UINT5, tll: UINT2, g: UINT6, tlll: UINT3, r: UINT5)
  @struct case class RGB(r: UINT5, g: UINT6, b: UINT5)

  @virtualize
  def main() {
    val onboardVideo = target.VideoCamera
    val outputVideo: Bus = target.VGA
//    val input  = StreamIn[RGB24](onboardVideo)
    val input  = StreamIn[RtGtBt](onboardVideo)
    val output = StreamOut[RGB](outputVideo)

    Accel(*) {
      Stream {
        val pixel = input.value()
//        val r = pixel.r.to[UINT5]
//        val g = pixel.g.to[UINT6]
//        val b = pixel.b.to[UINT5]
        val r = pixel.tl.to[UINT5]
//        val tg = pixel.tg
        val g = pixel.tll.to[UINT6]
//        val tb = pixel.tb 
        val b = pixel.tlll.to[UINT5]
        output := RGB(r,g,b) 
      }
    }
  }
}
