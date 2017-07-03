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
    val max_chars = 64 // 512 bits
    val raw_text = "We choose to go to the moon" //args(0)
    val data_text = argon.lang.String.string2num(raw_text)
    val len = ArgIn[Int32]
    val text_dram = DRAM[Int8](max_chars)
    val hash_dram = DRAM[Int8](max_chars)

    println("Hashing: " + raw_text + " (len: " + raw_text.length() + ")")
    printArray(data_text, "text as data: ")
    setArg(len, raw_text.length())
    setMem(text_dram, data_text)

  	Accel{
  		val text_sram = SRAM[Int8](max_chars)
  		text_sram load text_dram(0::len)

      // Pack chars into Int32's
      val packed_sram = SRAM[Int32](max_chars/4)
      Sequential.Foreach(max_chars/4 by 1){ i => 
        packed_sram(i) = (text_sram(i*4).as[Int32] << 24) | (text_sram(i*4+1).as[Int32] << 16) | (text_sram(i*4 + 2).as[Int32] << 8) | text_sram(i*4+3).as[Int32]
      }

      // Unpack Int32's into chars
      Foreach(max_chars/4 by 1){ i =>
        Pipe{text_sram(i*4) = packed_sram(i).apply(31::24).as[Int8]}
        Pipe{text_sram(i*4+1) = packed_sram(i).apply(23::16).as[Int8]}
        Pipe{text_sram(i*4+2) = packed_sram(i).apply(15::8).as[Int8]}
        Pipe{text_sram(i*4+3) = packed_sram(i).apply(7::0).as[Int8]}
      }
  		hash_dram store text_sram
  	}

  	val ciphertext = getMem(hash_dram)
    val ciphertext_string = argon.lang.String.num2string(ciphertext)
  	val ciphertext_gold = "9e55f2cf1066c55ff61ba2dab12e5bbba8d1158bda614a870b7d5c5bc6b8fce5"
  	println("Expected: " + ciphertext_gold)
  	println("Got:      " + ciphertext_string)

  	// // Debugging
  	// val key_dbg = getMem(key_debug)
  	// printArray(key_dbg, "Key: ")

  	val cksum = ciphertext_gold == ciphertext_string
  	println("PASS: " + cksum + " (SHA)")

  }
}
