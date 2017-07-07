import spatial.dsl._
import org.virtualized._
import spatial.targets._

object SHA extends SpatialApp { // Regression (Dense) // Args: none
  override val target = AWS_F1

  type ULong = FixPt[FALSE, _32, _0]
  @struct case class byte_pack(a: Int8, b: Int8, c: Int8, d: Int8)

  @virtualize
  def main() = {
  	// Setup off-chip data
  	val BLOCK_SIZE = 8192
  	val SHA_BLOCKSIZE = 64

    val CONST1 = 0x5a827999L
    val CONST2 = 0x6ed9eba1L
    val CONST3 = 0x8f1bbcdcL
    val CONST4 = 0xca62c1d6L

    val raw_text = loadCSV1D[String]("/remote/regression/data/machsuite/sha_txt.csv", "\n").apply(0)
    // val raw_text = loadCSV1D[String]("/home/mattfel/txt", "\n").apply(0)
    val data_text = argon.lang.String.string2num(raw_text)
    // val data_text = loadCSV1D[Int8]("/home/mattfel/txt",",")
    val len = ArgIn[Int]
    setArg(len, data_text.length)
    val text_dram = DRAM[Int8](len)
    val hash_dram = DRAM[ULong](16)//(5)

    // println("Hashing: " + argon.lang.String.num2string(data_text) + " (len: " + data_text.length + ")")
    println("Hashing: " + raw_text + " (len: " + data_text.length + ")")
    // printArray(data_text, "text as data: ")
    setMem(text_dram, data_text)

  	Accel{
  		val buffer = SRAM[Int8](BLOCK_SIZE)
  		val sha_digest = RegFile[ULong](5)
  		val sha_data = SRAM[ULong](16)
  		val count_lo = Reg[Int](0)
  		val count_hi = Reg[Int](0)

  		def asLong(r: Reg[ULong]): ULong = {r.value.apply(31::0).as[ULong]}

  		def sha_transform(): Unit = {
	      val W = SRAM[ULong](80)
		    val A = Reg[ULong]
		    val B = Reg[ULong]
		    val C = Reg[ULong]
		    val D = Reg[ULong]
		    val E = Reg[ULong]

	      Foreach(80 by 1) { i =>
	      	W(i) = if (i < 16) {sha_data(i)} else {W(i-3) ^ W(i-8) ^ W(i-14) ^ W(i-16)}
	      }

				A := sha_digest(0)
				B := sha_digest(1)
				C := sha_digest(2)
				D := sha_digest(3)
				E := sha_digest(4)

		    Sequential.Foreach(20 by 1) { i => 
		    	val temp = ((A << 5) | (A >> (32 - 5))) + E + W(i) + CONST1 + ((B & C) | (~B & D))
		    	E := D; D := C; C := ((B << 30) | (B >> (32 - 30))); B := A; A := temp
		    }
		    Sequential.Foreach(20 until 40 by 1) { i => 
		    	val temp = ((A << 5) | (A >> (32 - 5))) + E + W(i) + CONST2 + (B ^ C ^ D)
		    	E := D; D := C; C := ((B << 30) | (B >> (32 - 30))); B := A; A := temp
		    }
		    Sequential.Foreach(40 until 60 by 1) { i => 
		    	val temp = ((A << 5) | (A >> (32 - 5))) + E + W(i) + CONST3 + ((B & C) | (B & D) | (C & D))
		    	E := D; D := C; C := ((B << 30) | (B >> (32 - 30))); B := A; A := temp
		    }
		    Sequential.Foreach(60 until 80 by 1) { i => 
		    	val temp = ((A << 5) | (A >> (32 - 5))) + E + W(i) + CONST4 + (B ^ C ^ D)
		    	E := D; D := C; C := ((B << 30) | (B >> (32 - 30))); B := A; A := temp
		    }

				Pipe{sha_digest(0) = sha_digest(0) + A}
				Pipe{sha_digest(1) = sha_digest(1) + B}
				Pipe{sha_digest(2) = sha_digest(2) + C}
				Pipe{sha_digest(3) = sha_digest(3) + D}
				Pipe{sha_digest(4) = sha_digest(4) + E}
  		}
  		def sha_update(count: Index): Unit = {
	  		if (count_lo + (count << 3) < count_lo) {count_hi :+= 1}
				count_lo :+= count << 3
    		count_hi :+= count >> 29
    		Sequential.Foreach(0 until count by SHA_BLOCKSIZE) { base => 
    			val numel = min(count - base, SHA_BLOCKSIZE.to[Index])
    			// TODO: Can make this one writer only
    			if (numel == SHA_BLOCKSIZE) {Pipe{
			      Sequential.Foreach(SHA_BLOCKSIZE/4 by 1){ i => 
			        sha_data(i) = (buffer(base + i*4).as[ULong]) | (buffer(base + i*4+1).as[ULong] << 8) | (buffer(base + i*4 + 2).as[ULong] << 16) | (buffer(base + i*4+3).as[ULong] << 24)
			      }
			      sha_transform()
		      }} else {
						Sequential(0 until numel by 1) { i => 
			        sha_data(i) = (buffer(base + i*4).as[ULong]) | (buffer(base + i*4+1).as[ULong] << 8) | (buffer(base + i*4 + 2).as[ULong] << 16) | (buffer(base + i*4+3).as[ULong] << 24)
						}	      	
		      }
				}

  		}

			Pipe{sha_digest(0) = 0x67452301L.to[ULong]}
			Pipe{sha_digest(1) = 0xefcdab89L.to[ULong]}
			Pipe{sha_digest(2) = 0x98badcfeL.to[ULong]}
			Pipe{sha_digest(3) = 0x10325476L.to[ULong]}
			Pipe{sha_digest(4) = 0xc3d2e1f0L.to[ULong]}

  		Sequential.Foreach(len by BLOCK_SIZE) { chunk => 
  			val count = min(BLOCK_SIZE.to[Int], (len - chunk))
	  		buffer load text_dram(chunk::chunk+count)
	  		sha_update(count)

	  		// def byte_reverse(x: ULong): ULong = {
	  		// 	byte_pack(x(31::24).as[Int8],x(23::16).as[Int8],x(15::8).as[Int8],x(7::0).as[Int8]).as[ULong]
	  		// }

				// Final sha
				// TODO: This last bit is probably wrong for any input that is not size 8192
				val lo_bit_count = count_lo.value.to[ULong]
				val hi_bit_count = count_hi.value.to[ULong]
    		val count_final = ((lo_bit_count.to[Int8] >> 3) & 0x3f.to[Int8]).to[Int]
    		sha_data(count_final) = 0x80
    		if (count_final > 56) { Pipe{
    			Foreach(count_final+1 until 16 by 1) { i => sha_data(i) = 0 }
    			sha_transform()
    			sha_data(14) = 0
    		}} else {
    			Foreach(count_final+1 until 16 by 1) { i => sha_data(i) = 0 }
    		}
    		Pipe{sha_data(14) = hi_bit_count}
    		Pipe{sha_data(15) = lo_bit_count}
    		sha_transform()
  		}


  		hash_dram store sha_digest
  	}

  	val hashed_result = getMem(hash_dram)
  	val hashed_gold = Array[ULong](1453245918L,3827204465L,1028678455L,1470518419L,1762507208L,0,0,0,0,0,0,0,0,0,0,0)
  	printArray(hashed_gold, "Expected: ")
  	printArray(hashed_result, "Got: ")

  	val cksum = hashed_gold == hashed_result
  	println("PASS: " + cksum + " (SHA)")

  }
}


object JPEG extends SpatialApp { // Regression (Dense) // Args: none
  override val target = AWS_F1

  @virtualize
  def main() = {
  	Accel{}
  }
}
