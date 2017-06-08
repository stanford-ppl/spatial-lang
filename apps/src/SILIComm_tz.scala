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

  @virtualize
    def ledSwitchTest(){
      val receiveTarget  = target.SliderSwitch    // Receive Line
      val transmitTarget = target.LEDR            // Transmit Line
      val gpout1: Bus = target.GPOutput1
      val rx = StreamIn[TENBIT](receiveTarget)
      val tx = StreamOut[TENBIT](transmitTarget)
      val dummyStreamOut = BufferedOut[UInt32](target.VGA)
      val ON = TENBIT(0,0,0,0,0,0,0,0,0,1)
      val OFF= TENBIT(0,0,0,0,0,0,0,0,0,0)
      val ACT= TENBIT(0,1,0,0,0,0,0,0,0,0)

      Accel(*){
        val dataIn = Reg[TENBIT]
        val tx_reg = Reg[TENBIT]
        val tx_on = Reg[Int]

        FSM[Int]{state => state.to[Int] < 6 }{ state =>
          dataIn := rx.value
          if(state == 0){
            tx_reg := ON
          }else if(state == 1){
            tx_reg := ACT
            tx_on := 1
          }else if(state == 2){
            tx_reg := OFF
            tx_on := 0
          }
          tx := tx_reg.value
          Foreach(0 until 50000000){ i =>
            dummyStreamOut(1,1) = i.to[UInt32]
          }
          }{state => mux(dataIn.value == OFF, 0, 
                      mux(tx_on.value == 0, 1, 2))}
      }
    }

    @virtualize
    def main(){
      ledSwitchTest();
    }
}
