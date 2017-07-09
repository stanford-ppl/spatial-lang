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
    		}} else {Pipe{
    			Foreach(count_final+1 until 16 by 1) { i => sha_data(i) = 0 }
        }}
    		Pipe{sha_data(14) = hi_bit_count}
    		Pipe{sha_data(15) = lo_bit_count}
    		sha_transform()
  		}


  		hash_dram store sha_digest
  	}

  	val hashed_result = getMem(hash_dram)
  	val hashed_gold = Array[ULong](1754467640L,1633762310L,3755791939L,3062269980L,2187536409L,0,0,0,0,0,0,0,0,0,0,0)
  	printArray(hashed_gold, "Expected: ")
  	printArray(hashed_result, "Got: ")

  	val cksum = hashed_gold == hashed_result
  	println("PASS: " + cksum + " (SHA)")

  }
}


object JPEG extends SpatialApp { // DISABLED Regression (Dense) // Args: none
  override val target = AWS_F1
  type UInt8 = FixPt[FALSE, _8, _0]
  type UInt = FixPt[FALSE, _32, _0]

  @virtualize
  def main() = {

    val M_SOI = 216 // Start of image
    val M_SOF0 = 192 // Baseline DCT ( Huffman ) 
    val M_SOS = 218 // Start of Scan ( Head of Compressed Data )
    val M_DHT = 196
    val M_DQT = 219
    val M_EOI = 217
    val DCTSIZE2 = 64

    val jpg_data = loadCSV1D[UInt8]("/remote/regression/data/machsuite/jpeg_input.csv", ",")
    val numel = jpg_data.length
    assert(!(jpg_data(0) != 255.to[UInt8] || jpg_data(1) != M_SOI.to[UInt8]), "Not a jpeg file!")

    val jpg_size = ArgIn[Int]
    setArg(jpg_size, numel)
    val jpg_dram = DRAM[UInt8](jpg_size)
    setMem(jpg_dram, jpg_data)

  	Accel{
      val jpg_sram = SRAM[UInt8](5207)
      jpg_sram load jpg_dram

      val izigzag_index = LUT[Int](64)( 0.to[Int], 1.to[Int], 8.to[Int], 16.to[Int], 9.to[Int], 2.to[Int], 3.to[Int], 10.to[Int],
                                    17.to[Int], 24.to[Int], 32.to[Int], 25.to[Int], 18.to[Int], 11.to[Int], 4.to[Int], 5.to[Int],
                                    12.to[Int], 19.to[Int], 26.to[Int], 33.to[Int], 40.to[Int], 48.to[Int], 41.to[Int], 34.to[Int],
                                    27.to[Int], 20.to[Int], 13.to[Int], 6.to[Int], 7.to[Int], 14.to[Int], 21.to[Int], 28.to[Int],
                                    35.to[Int], 42.to[Int], 49.to[Int], 56.to[Int], 57.to[Int], 50.to[Int], 43.to[Int], 36.to[Int],
                                    29.to[Int], 22.to[Int], 15.to[Int], 23.to[Int], 30.to[Int], 37.to[Int], 44.to[Int], 51.to[Int],
                                    58.to[Int], 59.to[Int], 52.to[Int], 45.to[Int], 38.to[Int], 31.to[Int], 39.to[Int], 46.to[Int],
                                    53.to[Int], 60.to[Int], 61.to[Int], 54.to[Int], 47.to[Int], 55.to[Int], 62.to[Int], 63.to[Int]
                                  )

      val p_jinfo_quant_tbl_quantval = SRAM[UInt](4,DCTSIZE2)

      val unread_marker = Reg[UInt8](0)
      val scan_ptr = Reg[Int](1)

      def next_marker(): Unit = {
        val found = Reg[Boolean](false)
        found.reset
        FSM[Int](offset => offset != -1.to[Int]){offset => 
          val addr = scan_ptr + offset
          if (jpg_sram(addr) == 255.to[UInt8]) {
            found := true
            scan_ptr := addr + 1
          }
        }{offset => mux(found || offset + scan_ptr > 5207, -1, offset + 1)}
      }

      def get_sof(): Unit = {
        val length = Reg[Int](0)
      }

      def get_dqt(): Unit = {
        val length = Reg[Int](0)
        length := (jpg_sram(scan_ptr + 1).as[Int] << 8) | (jpg_sram(scan_ptr + 2).as[Int]) - 2
        scan_ptr :+= 2
        // if (length != 65) quit()
        FSM[Int](iter => iter != -1) { iter => 
          val tmp = jpg_sram(scan_ptr + 1)
          scan_ptr :+= 1
          val prec = tmp >> 4
          val num = (tmp & 0x0f).as[Int]

          Foreach(DCTSIZE2 by 1){ i => 
            val tmp = Reg[UInt](0)
            if (prec == 1.to[UInt8]) {
              scan_ptr :+= 2
              tmp := (jpg_sram(scan_ptr).as[UInt] << 8) | jpg_sram(scan_ptr).as[UInt]
            } else {
              scan_ptr :+= 1
              tmp := jpg_sram(scan_ptr).as[UInt]
            } 
            p_jinfo_quant_tbl_quantval(num, izigzag_index(i)) = tmp
          }
          length := length - (DCTSIZE2 + 1) - mux(prec == 1.to[UInt8], DCTSIZE2, 0)
        }{iter => mux(length == 0.to[Int], -1, iter + 1)}

        Foreach(4 by 1, 64 by 1){(i,j) => println(" " + i + "," + izigzag_index(j) + " = " + p_jinfo_quant_tbl_quantval(i,izigzag_index(j)))}

      }

      // Read markers
      FSM[Int,Int](0)(sow_SOI => sow_SOI < 2){sow_SOI => 
        if (sow_SOI == 0.to[Int]) {
          Pipe{unread_marker := jpg_sram(1)}
        } else {
          next_marker()
          unread_marker := jpg_sram(scan_ptr)
        }

        if (unread_marker.value == M_SOI.to[UInt8]) {
          // Continue normally
        } else if (unread_marker.value == M_SOF0.to[UInt8]) {
          get_sof()
        } else if (unread_marker.value == M_DQT.to[UInt8]) {
          get_dqt()
        } else {
          // Do nothing (probably 0xe0)
        }
      }{sow_SOI => 1}
    }

    val gold_bmp = loadCSV2D[UInt8]("/remote/regression/data/machsuite/jpeg_input.csv", ",", "\n")
    printMatrix(gold_bmp, "gold")
  }
}
