import org.virtualized._
import spatial._

object MemReduceTest extends SpatialApp {
  import IR._

  @virtualize
  def main() {
    val y = ArgOut[Int]

    Accel {
      val accum = SRAM[Int](32, 32)
      // TODO: Use MemReduce to generate entries for 
      // matrix A as defined in README.md. 
   /*   MemReduce(accum)(0 until 32, 0 until 32) { (row,col) =>          
         val sram_seq = SRAM[Int](32,32)
         Foreach(0 until 32, 0 until 32) { (r, c) => 
            sram_seq(r,c) = 0
         }
         sram_seq(row, col) = 32*(row + col)
         sram_seq 
      } { (sr1, sr2) => sr1 + sr2 }*/
      MemReduce(accum)(0 until 32) { row => 
        val sram_seq = SRAM[Int](32,32)
         Foreach(0 until 32, 0 until 32) { (r, c) => 
            sram_seq(r,c) = 0
         }
         Foreach(0 until 32) { col =>
            sram_seq(row, col) = 32*(row + col)
         }
         sram_seq
      }  { (sr1, sr2) => sr1 + sr2 }

      y := accum(2,2)
    }
    
    val result = getArg(y)
    println("expected: " + 18)
    println("result: " + result)
  }
}
