import spatial.dsl._
import org.virtualized._
import spatial.targets._

object SHA extends SpatialApp { // Regression (Dense) // Args: none
  override val target = AWS_F1

  /*
  TODO: Optimize/parallelize many of the memory accesses here and pipeline as much as possible
  
  MachSuite Concerns: 
  	- Mix rows math seemed wrong in their implementation
  	- Not exactly sure what was going on with their expand_key step
	*/
  @virtualize
  def main() = {
  	// Setup off-chip data
    val raw_text = "We choose to go to the moon in this decade and do the other things" //args(0)
    val data_text = argon.lang.String.string2num(raw_text)
    val len = ArgIn[Int32]
    val text_dram = DRAM[Int8](128)

    println("Hashing: " + raw_text + " (len: " + raw_text.length() + ")")
    printArray(data_text, "text as data: ")
    setArg(len, raw_text.length())
    setMem(text_dram, data_text)

  	Accel{
  		val text_sram = SRAM[Int8](128)
  		text_sram load text_dram(0::len)

  		// Foreach()

  		text_dram store text_sram
  	}

  	val ciphertext = getMem(text_dram)
  	val ciphertext_gold = Array[Int8](2,1,1)
  	printArray(ciphertext_gold, "Expected: ")
  	printArray(ciphertext, "Got: ")

  	// // Debugging
  	// val key_dbg = getMem(key_debug)
  	// printArray(key_dbg, "Key: ")

  	// val cksum = ciphertext_gold.zip(ciphertext){_ == _}.reduce{_&&_}
  	// println("PASS: " + cksum + " (AES) * For retiming, need to fix ^ reduction if not parallelized")

  }
}
