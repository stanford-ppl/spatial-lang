import spatial.dsl._
import org.virtualized._

object Switch2Video extends SpatialApp {


  override val target = targets.DE1

  type UINT2 = FixPt[FALSE,_2,_0]
  type UINT3 = FixPt[FALSE,_3,_0]
  type UINT4 = FixPt[FALSE,_4,_0]
  type UINT5 = FixPt[FALSE,_5,_0]
  type UINT6 = FixPt[FALSE,_6,_0]
  type UINT7 = FixPt[FALSE,_7,_0]
  type UINT8 = FixPt[FALSE,_8,_0]
  type UINT10 = FixPt[FALSE,_10,_0]
  type UINT32 = FixPt[FALSE,_32,_0]

  @struct case class bBgGrR(tlll: UINT3, b: UINT5, tll: UINT2, g: UINT6, tl: UINT3, r: UINT5)
  @struct class BGR(b1: UINT2, b2: UINT3, g1: UINT2, g2: UINT4, r1: UINT2, r2: UINT3)
  @struct class sw3(b1: UINT3, b2: UINT4, b3: UINT3)

  @virtualize
  def main() {
    val switch = target.SliderSwitch
    val outputVideo: Bus = target.VGA
//   val onboardVideo = target.VideoCamera

//    val input  = StreamIn[bBgGrR](onboardVideo)
    val swInput = StreamIn[sw3](switch)
    val output = StreamOut[BGR](outputVideo)

    val zeros = 0.to[UINT2]

    Accel(*) {
//      val rgbBits = input.value()
      val swBits = swInput.value()
      val f0 = swBits.b1
      val f1 = swBits.b2
      val f2 = swBits.b3
      output := BGR(zeros, f0, zeros, f1, zeros, f2)
    }
  }
}

object DumSwitch extends SpatialApp {


  override val target = targets.DE1

  type UINT2 = FixPt[FALSE,_2,_0]
  type UINT3 = FixPt[FALSE,_3,_0]
  type UINT4 = FixPt[FALSE,_4,_0]
  type UINT5 = FixPt[FALSE,_5,_0]
  type UINT6 = FixPt[FALSE,_6,_0]
  type UINT7 = FixPt[FALSE,_7,_0]
  type UINT8 = FixPt[FALSE,_8,_0]
  type UINT10 = FixPt[FALSE,_10,_0]
  type UINT32 = FixPt[FALSE,_32,_0]

  @struct case class bBgGrR(tlll: UINT3, b: UINT5, tll: UINT2, g: UINT6, tl: UINT3, r: UINT5)
  @struct class BGR(b1: UINT2, b2: UINT3, g1: UINT2, g2: UINT4, r1: UINT2, r2: UINT3)
  @struct class sw3(b1: UINT3, b2: UINT4, b3: UINT3)

  @virtualize
  def main() {
    // val switch = target.SliderSwitch
//   val onboardVideo = target.VideoCamera

//    val input  = StreamIn[bBgGrR](onboardVideo)
    // val swInput = StreamIn[sw3](switch)
    // val output = StreamOut[BGR](outputVideo)

    // val zeros = 0.to[UINT2]

    Accel(*) {
//      val rgbBits = input.value()
      // val swBits = swInput.value()
      // val f0 = swBits.b1
      // val f1 = swBits.b2
      // val f2 = swBits.b3
      // output := BGR(zeros, f0, zeros, f1, zeros, f2)
    }
  }
}

