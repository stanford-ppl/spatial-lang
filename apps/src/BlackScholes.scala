import spatial._
import org.virtualized._

object BlackScholes extends SpatialApp { 
  import IR._

  // .as is for constants, .to is for else
  type T = FixPt[TRUE,_16,_16]
  val margin = 0.5.as[T] // Validates true if within +/- margin
  val innerPar = 1
  val outerPar = 1
  val tileSize = 640

  final val inv_sqrt_2xPI = 0.39894228040143270286.as[T]

  @virtualize 
  def CNDF(x: T) = {
    val ax = abs(x)

    val xNPrimeofX = (ax * ax) * -0.05.as[T] * inv_sqrt_2xPI //exp((ax * ax) * -0.05.as[T]) * inv_sqrt_2xPI
    val xK2 = 1.as[T] / ((ax * 0.2316419.as[T]) + 1.0.as[T])

    val xK2_2 = xK2 * xK2
    val xK2_3 = xK2_2 * xK2
    val xK2_4 = xK2_3 * xK2
    val xK2_5 = xK2_4 * xK2

    val xLocal_10 = xK2 * 0.319381530.as[T]
    val xLocal_20 = xK2_2 * -0.356563782.as[T]
    val xLocal_30 = xK2_3 * 1.781477937.as[T]
    val xLocal_31 = xK2_4 * -1.821255978.as[T]
    val xLocal_32 = xK2_5 * 1.330274429.as[T]

    val xLocal_21 = xLocal_20 + xLocal_30
    val xLocal_22 = xLocal_21 + xLocal_31
    val xLocal_23 = xLocal_22 + xLocal_32
    val xLocal_1 = xLocal_23 + xLocal_10

    val xLocal0 = xLocal_1 * xNPrimeofX
    val xLocal  = -xLocal0 + 1.0.as[T]

    mux(x < 0.0.as[T], xLocal0, xLocal)
  }

  @virtualize
  def BlkSchlsEqEuroNoDiv(sptprice: T, strike: T, rate: T, volatility: T, time: T, otype: Int) = {
    val xLogTerm = sptprice / strike /*log( sptprice / strike )*/
    val xPowerTerm = (volatility * volatility) * 0.5.as[T]
    val xNum = (rate + xPowerTerm) * time + xLogTerm
    val xDen = volatility * time * time/*sqrt(time)*/

    val xDiv = xNum / (xDen * xDen)
    val nofXd1 = CNDF(xDiv)
    val nofXd2 = CNDF(xDiv - xDen)
    
    val futureValueX = strike * -rate * time.to[T]//exp(-rate * time.to[T])

    val negNofXd1 = -nofXd1 + 1.0.as[T]
    val negNofXd2 = -nofXd2 + 1.0.as[T]

    val optionPrice1 = (sptprice * nofXd1) - (futureValueX * nofXd2)
    val optionPrice2 = (futureValueX * negNofXd2) - (sptprice * negNofXd1)
    mux(otype == 0, optionPrice2, optionPrice1)
  }

  @virtualize
  def blackscholes (
    stypes:      Array[Int],
    sprices:     Array[T],
    sstrike:     Array[T],
    srate:       Array[T],
    svolatility: Array[T],
    stimes:      Array[T]
  ): Array[T] = {
    val B  = tileSize (96 -> 96 -> 19200)
    val OP = outerPar (1 -> 1)
    val IP = innerPar (1 -> 96)

    val size = stypes.length; bound(size) = 9995328

    lazy val N = ArgIn[Int]
    setArg(N, size)

    val types    = DRAM[Int](N)
    val prices   = DRAM[T](N)
    val strike   = DRAM[T](N)
    val rate     = DRAM[T](N)
    val vol      = DRAM[T](N)
    val times    = DRAM[T](N)
    val optprice = DRAM[T](N)
    setMem(types, stypes)
    setMem(prices, sprices)
    setMem(strike, sstrike)
    setMem(rate, srate)
    setMem(vol, svolatility)
    setMem(times, stimes)

    Accel {
      Foreach(N by B par OP) { i =>
        val typeBlk   = SRAM[Int](B)
        val priceBlk  = SRAM[T](B)
        val strikeBlk = SRAM[T](B)
        val rateBlk   = SRAM[T](B)
        val volBlk    = SRAM[T](B)
        val timeBlk   = SRAM[T](B)
        val optpriceBlk = SRAM[T](B)

        Parallel {
          typeBlk   load types(i::i+B par 16)
          priceBlk  load prices(i::i+B par 16)
          strikeBlk load strike(i::i+B par 16)
          rateBlk   load rate(i::i+B par 16)
          volBlk    load vol(i::i+B par 16)
          timeBlk   load times(i::i+B par 16)
        }

        Foreach(B par IP){ j =>
          val price = BlkSchlsEqEuroNoDiv(priceBlk(j), strikeBlk(j), rateBlk(j), volBlk(j), timeBlk(j), typeBlk(j))
          optpriceBlk(j) = price
        }
        optprice(i::i+B par 16) store optpriceBlk
      }
    }
    getMem(optprice)
  }

  @virtualize
  def main() {
    val N = args(0).to[Int]

    val types  = Array.fill(N)(random[Int](2))
    val prices = Array.fill(N)(random[T])
    val strike = Array.fill(N)(random[T])
    val rate   = Array.fill(N)(random[T])
    val vol    = Array.fill(N)(random[T])
    val ftime  = Array.fill(N)(random[T])
    val time   = ftime.map{ t => t.asInstanceOf[T]}

    val out = blackscholes(types, prices, strike, rate, vol, time)

    val gold = Array.tabulate(N){i => BlkSchlsEqEuroNoDiv(prices(i),strike(i),rate(i),vol(i),ftime(i),types(i)) }

    printArray(gold, "gold: ")
    printArray(out, "result: ")

    val cksum = out.zip(gold){(o,g) => (g < (o + margin)) && g > (o - margin)}.reduce{_&&_}
    println("PASS: " + cksum + " (BlackScholes) * Remember to change the exp, square, and log hacks, which was a hack so we can used fix point numbers")
  }
}
