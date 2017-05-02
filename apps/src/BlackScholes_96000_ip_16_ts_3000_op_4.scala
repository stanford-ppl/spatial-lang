import spatial._
import org.virtualized._

object BlackScholes_96000_ip_16_ts_3000_op_4 extends SpatialApp {
  import IR._

  type T = FixPt[TRUE, _16, _16]

  val margin = 0.5f // Validates true if within +/- margin
val ip = 16
val op = 4
val ts = 3000

  final val inv_sqrt_2xPI = 0.39894228040143270286f

  @virtualize
  def CNDF(x: T) = {
    val ax = abs(x)

    //val xNPrimeofX = exp((ax ** 2) * -0.05f) * inv_sqrt_2xPI //TODO exp is not supported
    val xNPrimeofX = abs((ax ** 2) * -0.05f.to[T]) * inv_sqrt_2xPI.to[T]
    val xK2 = 1.to[T] / ((ax * 0.2316419f.to[T]) + 1.0f.to[T])

    val xK2_2 = xK2 ** 2
    val xK2_3 = xK2_2 * xK2
    val xK2_4 = xK2_3 * xK2
    val xK2_5 = xK2_4 * xK2

    val xLocal_10 = xK2 * 0.319381530f.to[T]
    val xLocal_20 = xK2_2 * -0.356563782f.to[T]
    val xLocal_30 = xK2_3 * 1.781477937f.to[T]
    val xLocal_31 = xK2_4 * -1.821255978f.to[T]
    val xLocal_32 = xK2_5 * 1.330274429f.to[T]

    val xLocal_21 = xLocal_20 + xLocal_30
    val xLocal_22 = xLocal_21 + xLocal_31
    val xLocal_23 = xLocal_22 + xLocal_32
    val xLocal_1 = xLocal_23 + xLocal_10

    val xLocal0 = xLocal_1 * xNPrimeofX
    val xLocal  = -xLocal0 + 1.0f.to[T]

    mux(x < 0.0f.to[T], xLocal0, xLocal)
  }

  @virtualize
  def BlkSchlsEqEuroNoDiv(sptprice: T, strike: T, rate: T,
    volatility: T, time: T, otype: Int) = {

    //val xLogTerm = log( sptprice / strike ) //TODO log is not supported
    val xLogTerm = abs( sptprice / strike )
    val xPowerTerm = (volatility ** 2) * 0.5f.to[T]
    val xNum = (rate + xPowerTerm) * time + xLogTerm
    //val xDen = volatility * sqrt(time) //TODO sqrt is not supported
    val xDen = volatility * abs(time)

    val xDiv = xNum / (xDen ** 2)
    val nofXd1 = CNDF(xDiv)
    val nofXd2 = CNDF(xDiv - xDen)

    //val futureValueX = strike * exp(-rate * time) //TODO exp is not supported
    val futureValueX = strike * abs(-rate * time)

    val negNofXd1 = -nofXd1 + 1.0f.to[T]
    val negNofXd2 = -nofXd2 + 1.0f.to[T]

    val optionPrice1 = (sptprice * nofXd1) - (futureValueX * nofXd2)
    val optionPrice2 = (futureValueX * negNofXd2) - (sptprice * negNofXd1)
    mux(otype == 0, optionPrice2, optionPrice1)
  }

  @virtualize
  def blackscholes(
    stypes:      Array[Int],
    sprices:     Array[T],
    sstrike:     Array[T],
    srate:       Array[T],
    svolatility: Array[T],
    stimes:      Array[T]
  ): Array[T] = {
    val B  = ts (96 -> 96 -> 19200)
    val OP = op (1 -> 1)
    val IP = ip (1 -> 96)

    val size = stypes.length; bound(size) = 9995328

    val N = ArgIn[Int]
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
          typeBlk   load types(i::i+B par IP)
          priceBlk  load prices(i::i+B par IP)
          strikeBlk load strike(i::i+B par IP)
          rateBlk   load rate(i::i+B par IP)
          volBlk    load vol(i::i+B par IP)
          timeBlk   load times(i::i+B par IP)
        }

        Foreach(B par IP){ j =>
          val price = BlkSchlsEqEuroNoDiv(priceBlk(j), strikeBlk(j), rateBlk(j), volBlk(j), timeBlk(j), typeBlk(j))
          optpriceBlk(j) = price
        }
        optprice(i::i+B par IP) store optpriceBlk
      }
    }
    getMem(optprice)
  }

  @virtualize
  def main(): Unit = {
    val N = args(0).to[Int]

    val types  = Array.fill(N)(random[Int](2))
    val prices = Array.fill(N)(random[T])
    val strike = Array.fill(N)(random[T])
    val rate   = Array.fill(N)(random[T])
    val vol    = Array.fill(N)(random[T])
    val time   = Array.fill(N)(random[T])

    val out = blackscholes(types, prices, strike, rate, vol, time)

    printArray(out, "result: ")

    //val cksum = out.zip(gold){ case (o, g) => (g < (o + margin)) && g > (o - margin)}.reduce{_&&_}
    //println("PASS: " + cksum + " (BlackSholes)")


  }
}
