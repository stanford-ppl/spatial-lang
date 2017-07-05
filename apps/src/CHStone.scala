import spatial.dsl._
import org.virtualized._
import spatial.targets._

object SHA extends SpatialApp { // DISABLED Regression (Dense) // Args: none
  override val target = AWS_F1

  type ULong = FixPt[TRUE, _64, _0]
  @virtualize
  def main() = {
  	// Setup off-chip data
  	val BLOCK_SIZE = 8192
  	val SHA_BLOCKSIZE = 64

    val CONST1 = 0x5a827999L
    val CONST2 = 0x6ed9eba1L
    val CONST3 = 0x8f1bbcdcL
    val CONST4 = 0xca62c1d6L

    val raw_text = "We choose to go to the moon" //args(0)
    val data_text = argon.lang.String.string2num(raw_text)
    val len = ArgIn[Int]
    val text_dram = DRAM[Int8](len)
    val hash_dram = DRAM[ULong](16)//(5)

    println("Hashing: " + raw_text + " (len: " + raw_text.length() + ")")
    printArray(data_text, "text as data: ")
    setArg(len, raw_text.length())
    setMem(text_dram, data_text)

  	Accel{
  		val buffer = SRAM[Int8](BLOCK_SIZE)
  		val sha_digest = RegFile[ULong](5)
  		val sha_data = SRAM[ULong](8192)//(16)
  		val count_lo = Reg[Int](0)
  		val count_hi = Reg[Int](0)
	    val A = Reg[ULong]
	    val B = Reg[ULong]
	    val C = Reg[ULong]
	    val D = Reg[ULong]
	    val E = Reg[ULong]

			Pipe{sha_digest(0) = 0x67452301L.to[ULong]}
			Pipe{sha_digest(1) = 0xefcdab89L.to[ULong]}
			Pipe{sha_digest(2) = 0x98badcfeL.to[ULong]}
			Pipe{sha_digest(3) = 0x10325476L.to[ULong]}
			Pipe{sha_digest(4) = 0xc3d2e1f0L.to[ULong]}

  		Foreach(len by BLOCK_SIZE) { chunk => 
  			val count = min(BLOCK_SIZE.to[Int], (len - chunk))
	  		buffer load text_dram(chunk::count)

	  		if (count_lo + (count << 3) < count_lo) {count_hi :+= 1}

				count_lo :+= count << 3
    		count_hi :+= count >> 29

    		// Byte reverse and pack
	      Sequential.Foreach(SHA_BLOCKSIZE/4 by 1){ i => 
	        sha_data(i) = (buffer(i*4).as[ULong] << 24) | (buffer(i*4+1).as[ULong] << 16) | (buffer(i*4 + 2).as[ULong] << 8) | buffer(i*4+3).as[ULong]
	      }

	      // sha transform
	      val W = SRAM[ULong](80)
	      Foreach(80 by 1) { i =>
	      	W(i) = mux(i < 16, sha_data(i), W(i-3) ^ W(i-8) ^ W(i-14) ^ W(i-16))
	      }

				A := sha_digest(0)
				B := sha_digest(1)
				C := sha_digest(2)
				D := sha_digest(3)
				E := sha_digest(4)

		    Foreach(20 by 1) { i => 
		    	val temp = ((A << 5) | (A >> (32 - 5))) + E// + W(i) + CONST1 + ((B & C) | (~B & D))
		    	println(" " + A.value + " " + ((A.value << 5) | (A.value >> (32 - 5))) + " " + E.value + " " + temp)
		    	E := D; D := C; C := ((B << 30) | (B >> (32 - 30))); B := A; A := temp
		    }
		    Foreach(20 until 40 by 1) { i => 

		    }
  		}


  		Foreach(16 by 1){i => sha_data(i) = mux(i < 5, sha_digest(i), 0)}


      // // Unpack Int32's into chars
      // Foreach(max_chars/4 by 1){ i =>
      //   Pipe{buffer(i*4) = packed_sram(i).apply(31::24).as[Int8]}
      //   Pipe{buffer(i*4+1) = packed_sram(i).apply(23::16).as[Int8]}
      //   Pipe{buffer(i*4+2) = packed_sram(i).apply(15::8).as[Int8]}
      //   Pipe{buffer(i*4+3) = packed_sram(i).apply(7::0).as[Int8]}
      // }

  		hash_dram store sha_digest
  	}

  	// val ciphertext = getMem(hash_dram)
   //  val ciphertext_string = argon.lang.String.num2string(ciphertext)
  	// val ciphertext_gold = "9e55f2cf1066c55ff61ba2dab12e5bbba8d1158bda614a870b7d5c5bc6b8fce5"
  	// println("Expected: " + ciphertext_gold)
  	// println("Got:      " + ciphertext_string)

  	// val cksum = ciphertext_gold == ciphertext_string
  	// println("PASS: " + cksum + " (SHA)")

  }
}
