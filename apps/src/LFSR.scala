import spatial._
import org.virtualized._

object LFSR extends SpatialApp {
  import IR._

  type UInt16 = FixPt[FALSE,_16,_0]
  type Bit = Boolean
  type UInt15 = FixPt[FALSE,_15,_0]

  @struct class NextState(lsb: Bit, msbs: UInt15)

  @virtualize
  def main() {

    val output = StreamOut[UInt16](GPOutput)


    Accel(*) {
      val state_reg = Reg[UInt16](1)
      val state = state_reg.value

      output := state

      val lsb = state(10) ^ state(12) ^ state(13) ^ state(15)
      val msbs = state.take15(0).as[UInt15]

      val next = NextState(lsb, msbs).as[UInt16]

      println(next.as16b)

      state_reg := next
    }

  }
}
