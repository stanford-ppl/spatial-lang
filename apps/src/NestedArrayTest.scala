import spatial._
import org.virtualized._

object NestedArrayTest extends SpatialApp {
  import IR._

  type T = Int

  @virtualize def main(): Unit = {
    Accel { }
    val N = 32

    val sigReal = Array.tabulate(N){ i => i }

    /* Initialize DFT matrices (for real and imaginary terms). */
    val DFTReal = Array.tabulate(N){i =>
      Array.tabulate(N){j =>
        val nextEntr = -i.to[T]*j.to[T]*(2.0 * PI / N.to[Float]).to[T]
        cos(nextEntr.to[Float]).to[T]
      }
    }

    val reRe = Array.empty[T](N)

    var p = 0 // NOTE: These are illegal right now (but we don't give an error yet)
    for (i <- DFTReal) {
      var nextEntr = 0.to[T]
      var q = 0
      for (j <- i) {
        nextEntr = nextEntr + j.to[T] * sigReal(q).to[T]
        q = q + 1
      }
      reRe(p) = nextEntr
      p = p + 1
    }
  }
}