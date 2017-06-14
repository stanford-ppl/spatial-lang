import spatial.dsl._
import org.virtualized._

object MuxVideo extends SpatialApp {


  override val target = targets.DE1

//  type UINT2 = FixPt[FALSE,_2,_0]
//  type UINT3 = FixPt[FALSE,_3,_0]
//  type UINT8 = FixPt[FALSE,_8,_0]
//  type UINT7 = FixPt[FALSE,_7,_0]
//  type UINT32 = FixPt[FALSE,_32,_0]
  type UINT10 = FixPt[FALSE,_10,_0]
  type UINT5 = FixPt[FALSE,_5,_0]
  type UINT6 = FixPt[FALSE,_6,_0]

//  @struct case class bBgGrR(tlll: UINT3, b: UINT5, tll: UINT2, g: UINT6, tl: UINT3, r: UINT5)
  @struct case class BGR(b: UINT5, g: UINT6, r: UINT5)

  @virtualize
  def main() {
    val switch = target.SliderSwitch
    val onboardVideo = target.VideoCamera
    val outputVideo: Bus = target.VGA
    val swInput = StreamIn[UINT10](switch)
//    val input  = StreamIn[bBgGrR](onboardVideo)
    val input = StreamIn[BGR](onboardVideo)
    val output = StreamOut[BGR](outputVideo)

    Accel(*) {
      // Version 1
      val pixel = input.value()
      val gray = (pixel.r.to[UINT6] + pixel.g + pixel.b.to[UINT6]) / 3
      output := mux(swInput.value() > 4, BGR(pixel.b, pixel.g, pixel.r), BGR(gray.to[UINT5], gray.to[UINT6], gray.to[UINT5]))
    }
  }
}

