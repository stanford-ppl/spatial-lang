import spatial._
import org.virtualized._
import forge._

object BitSelects extends SpatialApp {
  import IR._

  @virtualize
  def main() {
    Accel {
      val x = 9.to[Int]
      val v = x.as32b
      println(v)
      println("bit 0: " + x(0))
      println("bit 1: " + x(1))
      println("bit 2: " + x(2))
      println("bit 3: " + x(3))

      assert(x(0), "First bit should be 1")
      assert(!x(1), "Second bit should be 0")
      assert(!x(2), "Third bit should be 0")
      assert(x(3), "Fourth bit should be 1")
    }
  }
}

/*object UserVectors extends SpatialApp {
  import IR._

  type Q4 = FixPt[FALSE,_4,_0]

  @virtualize
  def main() {
    Accel {
      val A = 10.to[Q4]
      val B = 11.to[Q4]
      val C = 12.to[Q4]
      val v = Vector.LittleEndian(A, B, C)
      val vb = v.as12b
      val x = vb(7::0) // bits from (B,C)   0b1011,1100

      val v = Vector.BigEndian(A, B, C)
      val vb = v.as12b
      val x = vb(7::0) // bits from (B,C)



      val x = v.le(0) // C
              v.be(0) // A

      val vb = v.as12b
      println(vb) // Should be 0b1010,1011,1100

      vb.le(0)

      val x = vb(7::0)
      println(x.as8b) // Should be 0b1011,1100

      assert(v(0) == C, "0th element of vector should be C")
      assert(v(1) == B, "1st element of vector should be B")
      assert(v(2) == A, "2nd element of vector should be A")
    }
  }
}*/

object TwiddlingWithStructs extends SpatialApp {
  import IR._

  type UInt8 = FixPt[FALSE,_8,_0]
  type SInt8 = FixPt[TRUE,_8,_0]

  @struct class UBytes(a: UInt8, b: UInt8, c: UInt8, d: UInt8)
  @struct class SBytes(a: SInt8, b: SInt8, c: SInt8, d: SInt8)

  @virtualize
  def main() {
    Accel {
      val x = 255.to[Int]
      println(x.as32b)
      val y = x.as[UBytes]
      println("ua: " + y.a)
      println("ub: " + y.b)
      println("uc: " + y.c)
      println("ud: " + y.d)
      assert(y.d == 255.to[UInt8], "d should be 255 (0b11111111)")
      assert(y.c == 0.to[UInt8], "c should be 0")
      assert(y.b == 0.to[UInt8], "b should be 0")
      assert(y.a == 0.to[UInt8], "a should be 0")

      val z = x.as[SBytes]
      println("sa: " + z.a)
      println("sb: " + z.b)
      println("sc: " + z.c)
      println("sd: " + z.d)
      assert(z.d == -1.to[SInt8], "d should be -1 (0b11111111)")
    }
  }
}