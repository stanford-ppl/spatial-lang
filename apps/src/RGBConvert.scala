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
  type UINT9 = FixPt[FALSE,_9,_0]
  type UINT7 = FixPt[FALSE,_7,_0]

  @struct case class bBgGrR(tlll: UINT3, b: UINT5, tll: UINT2, g: UINT6, tl: UINT3, r: UINT5)
  @struct case class BGR(b: UINT5, g: UINT6, r: UINT5)

  @virtualize
  def main() {
    val onboardVideo = target.VideoCamera
    val outputVideo: Bus = target.VGA
    val input  = StreamIn[bBgGrR](onboardVideo)
    val output = StreamOut[BGR](outputVideo)

    Accel(*) {
      val pixel = input.value()
      val r = pixel.r
      val g = pixel.g
      val b = pixel.b
      output := BGR(b,g,r) 
    }
  }
}
