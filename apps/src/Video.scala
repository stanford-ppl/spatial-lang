import org.virtualized._
import spatial._

object Video extends SpatialApp {
  import IR._

  override val target = targets.DE1
  type UINT10 = FixPt[FALSE,_10,_0]
  type UINT2 = FixPt[FALSE,_2,_0]
  type UINT3 = FixPt[FALSE,_3,_0]
  type UINT5 = FixPt[FALSE,_5,_0]
  type UINT6 = FixPt[FALSE,_6,_0]
  type UINT8 = FixPt[FALSE,_8,_0]
  type UINT7 = FixPt[FALSE,_7,_0]
  type UINT32 = FixPt[FALSE,_32,_0]

  @struct case class bBgGrR(tlll: UINT3, b: UINT5, tll: UINT2, g: UINT6, tl: UINT3, r: UINT5)
  @struct case class BGR(b: UINT5, g: UINT6, r: UINT5)

  @virtualize 
  def main() { 
//    val io1 = HostIO[Int]
    val switch = target.SliderSwitch
    val swInput = StreamIn[Int](switch)
    val onboardVideo = target.VideoCamera
    val outputVideo: Bus = target.VGA
    val input  = StreamIn[bBgGrR](onboardVideo)
    val output = StreamOut[BGR](outputVideo)

    Accel(*) {
//      io1 := swInput.value()
      val pixel = input.value()
      output := BGR(pixel.b, pixel.g, pixel.r)
    }

//    val r1 = getArg(io1)
//    println("received: " + r1)
  }
}
