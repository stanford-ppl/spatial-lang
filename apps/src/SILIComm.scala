import spatial._              // import spatial language
import org.virtualized._      // virtualized needed for function defines
import spatial.targets.DE1    // de1 specific target

object SILIComm extends SpatialApp {
  import IR._
  override val target = targets.DE1;

  type BIT = FixPt[FALSE,_1,_0]

  @struct class TENBIT(SW0: BIT, SW1: BIT, SW2: BIT,
    SW3: BIT, SW4: BIT, SW5: BIT,
    SW6: BIT, SW7: BIT, SW8: BIT,
    SW9: BIT)

  /*
  @virtualize
    def ledSwitchTest(){
      val receiveTarget  = target.SliderSwitch    // Receive Line
      val transmitTarget = target.LEDR            // Transmit Line
      val rx = StreamIn[TENBIT](receiveTarget)
      val tx = StreamOut[TENBIT](transmitTarget)

      Accel(*){
        val dataIn = rx.value()
        tx := dataIn
      }
    }
    */

  @virtualize
  def modulateInit(bitsIn: Int){

    val receiveTarget  = target.SliderSwitch    // Receive Line
    val transmitTarget = target.LEDR            // Transmit Line
    val rx = StreamIn[TENBIT](receiveTarget)
    val tx = StreamOut[TENBIT](transmitTarget)

    val period_time = 50000000;                 // 50 Million Cycles = 1 Second Period
    val io1 = StreamIn[Int](receiveTarget)//= HostIO[Int]

    Accel(*){
      // State Definitions
      val IDLE = 0
      val NIB_00 = 1
      val NIB_01 = 2
      val NIB_11 = 3
      val NIB_10 = 4
      val DONE = 5
      // End State Definitions

      val ON = TENBIT(0,0,0,0,0,0,0,0,0,1)
      val OFF= TENBIT(0,0,0,0,0,0,0,0,0,0)

      val f1 = FIFO[Int](128);
      val nibs_transmitted = Reg[Int]
      val curr_nib = Reg[Int]
      val counter_reg = Reg[Int];
      val data_in = rx.value();                    // Take in bits to transmit

      /*
      volatile char userInput;
      setReg(hostIO, userInput & 0x.....)
        ....
      setReg(hostIO, (userInput >> 2 ) & 0x.....)
      */

      f1.enq(io1.value)

      FSM[Int]{state => state.to[Int] != DONE}{ state =>     // FSM

        if (state == IDLE) {                        // Transmit stays High
          tx := ON
          Foreach (0 until period_time) { i =>      // Wait 1 Period
            counter_reg := i;
          }
          nibs_transmitted := 0;
        } else if(state == NIB_00) {                // Transmit low for 1 period
          tx := OFF
          Foreach (0 until period_time) { i =>      // Wait 1 Period
            counter_reg := i
          }
          tx := ON                               // Set TX High
          curr_nib := f1.deq()
          nibs_transmitted := nibs_transmitted + 1;
        } else if(state == NIB_01) {                // Transmit low for 2 periods
          tx := OFF
          Foreach (0 until 2*period_time) { i =>
            counter_reg := i
          }
          tx := ON
          curr_nib := f1.deq()
          nibs_transmitted := nibs_transmitted + 1;
        } else if(state == NIB_11) {                // Transmit low for 3 periods
          tx := OFF
          Foreach (0 until 3*period_time) { i =>
            counter_reg := i
          }
          tx := ON
          curr_nib := f1.deq()
          nibs_transmitted := nibs_transmitted + 1;
        } else if(state == NIB_10) {                // Transmit low for 4 periods
          tx := OFF
          Foreach (0 until 4*period_time) { i =>
            counter_reg := i
          }
          tx := ON
          curr_nib := f1.deq()
          nibs_transmitted := nibs_transmitted + 1;
        }
      } {state => mux(data_in.SW9.to[Int] == 0, IDLE,                                // If Switch 9 is off, stay in idle
        mux(nibs_transmitted.value == 4, DONE,                          // Check if we have finished transmitting
          mux(curr_nib.value == 0, NIB_00,                             // Change state depending on current nibble
            mux(curr_nib.value == 1, NIB_01,
              mux(curr_nib.value == 11, NIB_11,
                mux(curr_nib.value == 10, NIB_10, IDLE))))))}
    }
  }


  @virtualize
  def main(){
    val N = 100;
    modulateInit(1010101);
  }
}