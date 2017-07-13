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
    		if (count_final > 56) {
    			Foreach(count_final+1 until 16 by 1) { i => sha_data(i) = 0 }
    			Sequential(sha_transform())
    			sha_data(14) = 0
    		} else {
    			Foreach(count_final+1 until 16 by 1) { i => sha_data(i) = 0 }
        }
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
  type UInt2 = FixPt[FALSE, _2, _0]
  type UInt16 = FixPt[FALSE, _16, _0]
  type UInt = FixPt[FALSE, _32, _0]
  @struct case class comp_struct(index: UInt8,
                                 id: UInt8,
                                 h_samp_factor: UInt8,
                                 v_samp_factor: UInt8,
                                 quant_tbl_no: UInt8
                                )
  @struct case class comp_acdc(index: UInt8,
                               dc: UInt8,
                               ac: UInt8
                              )

  @virtualize
  def main() = {

    val M_SOI = 216 // Start of image
    val M_SOF0 = 192 // Baseline DCT ( Huffman ) 
    val M_SOS = 218 // Start of Scan ( Head of Compressed Data )
    val M_DHT = 196
    val M_DQT = 219
    val M_EOI = 217
    val DCTSIZE2 = 64
    val NUM_COMPONENT = 3
    val NUM_HUFF_TBLS = 2
    val RGB_NUM = 3
    val SF4_1_1 = 2
    val SF1_1_1 = 0

    // Validity check values
    val out_length_get_sof = 17
    val out_data_precision_get_sof = 8
    val out_p_jinfo_image_height_get_sof = 59
    val out_p_jinfo_image_width_get_sof = 90
    val out_p_jinfo_num_components_get_sof = 3

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

      val component = SRAM[comp_struct](NUM_COMPONENT)
      val component_acdc = SRAM[comp_acdc](NUM_COMPONENT)
      val izigzag_index = LUT[Int](64)( 0.to[Int], 1.to[Int], 8.to[Int], 16.to[Int], 9.to[Int], 2.to[Int], 3.to[Int], 10.to[Int],
                                    17.to[Int], 24.to[Int], 32.to[Int], 25.to[Int], 18.to[Int], 11.to[Int], 4.to[Int], 5.to[Int],
                                    12.to[Int], 19.to[Int], 26.to[Int], 33.to[Int], 40.to[Int], 48.to[Int], 41.to[Int], 34.to[Int],
                                    27.to[Int], 20.to[Int], 13.to[Int], 6.to[Int], 7.to[Int], 14.to[Int], 21.to[Int], 28.to[Int],
                                    35.to[Int], 42.to[Int], 49.to[Int], 56.to[Int], 57.to[Int], 50.to[Int], 43.to[Int], 36.to[Int],
                                    29.to[Int], 22.to[Int], 15.to[Int], 23.to[Int], 30.to[Int], 37.to[Int], 44.to[Int], 51.to[Int],
                                    58.to[Int], 59.to[Int], 52.to[Int], 45.to[Int], 38.to[Int], 31.to[Int], 39.to[Int], 46.to[Int],
                                    53.to[Int], 60.to[Int], 61.to[Int], 54.to[Int], 47.to[Int], 55.to[Int], 62.to[Int], 63.to[Int]
                                  )

      val scan_ptr = Reg[Int](1)

      val p_jinfo_quant_tbl_quantval = SRAM[UInt16](4,DCTSIZE2)
      val p_jinfo_dc_xhuff_tbl_bits = SRAM[UInt8](NUM_HUFF_TBLS, 36)
      val p_jinfo_dc_xhuff_tbl_huffval = SRAM[UInt8](NUM_HUFF_TBLS, 257)
      val p_jinfo_dc_dhuff_tbl_ml = SRAM[UInt16](NUM_HUFF_TBLS)
      val p_jinfo_dc_dhuff_tbl_maxcode = SRAM[UInt16](NUM_HUFF_TBLS, 36)
      val p_jinfo_dc_dhuff_tbl_mincode = SRAM[UInt16](NUM_HUFF_TBLS, 36)
      val p_jinfo_dc_dhuff_tbl_valptr = SRAM[UInt16](NUM_HUFF_TBLS, 36)
      val p_jinfo_ac_xhuff_tbl_bits = SRAM[UInt8](NUM_HUFF_TBLS, 36)
      val p_jinfo_ac_xhuff_tbl_huffval = SRAM[UInt8](NUM_HUFF_TBLS, 257)
      val p_jinfo_ac_dhuff_tbl_ml = SRAM[UInt16](NUM_HUFF_TBLS)
      val p_jinfo_ac_dhuff_tbl_maxcode = SRAM[UInt16](NUM_HUFF_TBLS, 36)
      val p_jinfo_ac_dhuff_tbl_mincode = SRAM[UInt16](NUM_HUFF_TBLS, 36)
      val p_jinfo_ac_dhuff_tbl_valptr = SRAM[UInt16](NUM_HUFF_TBLS, 36)

      val out_data_comp_vpos = SRAM[UInt16](RGB_NUM)
      val out_data_comp_hpos = SRAM[UInt16](RGB_NUM)

      val p_jinfo_smp_fact = Reg[UInt2](0)
      val p_jinfo_num_components = Reg[UInt8](0)
      val p_jinfo_jpeg_data = Reg[Int](0)
      val p_jinfo_image_height = Reg[UInt16](0)
      val p_jinfo_image_width = Reg[UInt16](0)
      val p_jinfo_MCUHeight = Reg[UInt16](0)
      val p_jinfo_MCUWidth = Reg[UInt16](0)
      val p_jinfo_NumMCU = Reg[UInt16](0)

      def read_word(): UInt16 = {
        val tmp = (jpg_sram(scan_ptr).as[UInt16] << 8) | (jpg_sram(scan_ptr + 1).as[UInt16])
        Pipe{scan_ptr :+= 2}
        tmp
      }
      def read_byte(): UInt8 = {
        val tmp = jpg_sram(scan_ptr)
        Pipe{scan_ptr :+= 1}
        tmp
      }

      def read_markers(): Unit = {
        val unread_marker = Reg[UInt8](0)

        def next_marker(): UInt8 = {
          val potential_marker = Reg[UInt8](0)
          potential_marker.reset
          FSM[Int](whilst => whilst != -1.to[Int]){whilst => 
            if (read_byte() == 255.to[UInt8]) {
              potential_marker := read_byte()
            }
          }{whilst => mux(potential_marker.value != 0.to[UInt8] || whilst + scan_ptr > 5207, -1, whilst)}
          potential_marker.value
        }

        def get_dqt(): Unit = {Sequential{
          val length = Reg[UInt16](0)
          length := read_word() - 2
          println("lenght " + length.value)
          // if (length != 65) quit()
          FSM[Int](iter => iter != -1) { iter => 
            val tmp = read_byte()
            val prec = tmp >> 4
            val num = (tmp & 0x0f).as[Int]

            Foreach(DCTSIZE2 by 1){ i => 
              val tmp = Reg[UInt16](0)
              if (prec == 1.to[UInt8]) {
                tmp := read_word()
              } else {
                tmp := read_byte().as[UInt16]
              } 
              p_jinfo_quant_tbl_quantval(num, izigzag_index(i)) = tmp.value
            }
            length := length - (DCTSIZE2 + 1) - mux(prec == 1.to[UInt8], DCTSIZE2, 0)
          }{iter => mux(length == 0.to[UInt16], -1, iter + 1)}

          // Foreach(4 by 1, 64 by 1){(i,j) => println(" " + i + "," + izigzag_index(j) + " = " + p_jinfo_quant_tbl_quantval(i,izigzag_index(j)))}

        }}

        def get_sof(): Unit = { Sequential{
          val length = Reg[UInt16](0)
          length := read_word()
          val p_jinfo_data_precision = read_byte()
          p_jinfo_image_height := read_word()
          p_jinfo_image_width := read_word()
          p_jinfo_num_components := read_byte()
          println(" " + length.value + " " + p_jinfo_data_precision + " " + p_jinfo_image_height + " " + p_jinfo_image_width + " " + p_jinfo_num_components)

          // if (length.value != out_length_get_sof || p_jinfo_data_precision != out_data_precision_get_sof ...) {error} 
          Pipe{length :-= 8} // Why the hell do we do this??

          Foreach(NUM_COMPONENT by 1) { i => 
            val p_comp_info_index = i.as[UInt8]
            val p_comp_info_id = read_byte()
            val c = read_byte()
            val p_comp_info_h_samp_factor = (c >> 4) & 15
            val p_comp_info_v_samp_factor = (c) & 15
            val p_comp_info_quant_tbl_no = read_byte()

            // Some more checks here

            println("writnig " + p_comp_info_index + " " + p_comp_info_id + " " + p_comp_info_h_samp_factor + " " + p_comp_info_v_samp_factor + " " + p_comp_info_quant_tbl_no)
            component(i) = comp_struct(p_comp_info_index, p_comp_info_id, p_comp_info_h_samp_factor, p_comp_info_v_samp_factor, p_comp_info_quant_tbl_no)
          }

          p_jinfo_smp_fact := mux(component(0).h_samp_factor == 2.to[UInt8], 2, 0)
        }}


        def get_sos(): Unit = {Sequential{
          val length = Reg[UInt16](0)
          length := read_word()
          val num_comp = read_byte()

          println(" length " + length.value)

          Sequential.Foreach(0 until num_comp.to[Index] by 1) { i => 
            val cc = read_byte ();
            val c = read_byte ();

            Foreach(0 until p_jinfo_num_components.value.to[Index] by 1) { ci => 
              val dc = (c >> 4) & 15
              val ac = c & 15
              component_acdc(ci) = comp_acdc(ci.to[UInt8], dc, ac)
            }

          }

          // pluck off 3 bytes for fun
          Foreach(3 by 1) { _ => read_byte()}
          p_jinfo_jpeg_data := scan_ptr.value
        }}

        def get_dht(): Unit = {Sequential{
          val length = Reg[UInt16](0)
          length := read_word() - 2
          println(" length " + length.value)
          // if (length != 65) quit()
          FSM[Int](whilst => whilst != -1) { whilst => 
            val index = Reg[UInt8](0)
            index := read_byte()
            val store_dc = Reg[Boolean](false)

            if ((index.value & 0x10) == 0.to[UInt8]) {
              store_dc := true
            } else {
              store_dc := false
              index :-= 0x10
            }

            val count = Reg[Int](0)
            count.reset
            Sequential.Foreach(1 until 17 by 1){ i => 
              val tmp = Reg[UInt8](0)
              tmp := read_byte()
              if (store_dc.value) {
                p_jinfo_dc_xhuff_tbl_bits(index.value.to[Int], i) = tmp
              } else {
                p_jinfo_ac_xhuff_tbl_bits(index.value.to[Int], i) = tmp
              }
              // println("p_jinfo_dc_xhuff_tbl_bits write " + index.value.to[Int] + "," + i + " = " + tmp.value)
              count := count + tmp.value.to[Int]
            }

            println(" dht count " + count.value)

            length :-= 17
            Sequential.Foreach(0 until count by 1) { i => 
              if (store_dc.value) {
                p_jinfo_dc_xhuff_tbl_huffval(index.value.to[Int], i) = read_byte()  
              } else {
                p_jinfo_ac_xhuff_tbl_huffval(index.value.to[Int], i) = read_byte()
              }
              
            }

            length := length - count.value.to[UInt16]

          }{whilst => mux(length > 16.to[UInt16], whilst, -1)}


        }}

        // Read markers
        FSM[Int,Int](0)(sow_SOI => sow_SOI != -1){sow_SOI => 
          if (sow_SOI == 0.to[Int]) {
            Pipe{unread_marker := read_byte()}
          } else {
            unread_marker := next_marker()
          }

          println("new marker is " + unread_marker)

          if (unread_marker.value == M_SOI.to[UInt8]) {
            println("M_SOI")
            // Continue normally
          } else if (unread_marker.value == M_SOF0.to[UInt8]) {
            println("M_SOF0")
            get_sof()
          } else if (unread_marker.value == M_DQT.to[UInt8]) {
            println("M_DQT")
            get_dqt()
          } else if (unread_marker.value == M_DHT.to[UInt8]) {
            println("M_DHT")
            get_dht()          
          } else if (unread_marker.value == M_SOS.to[UInt8]) {
            println("M_SOS")
            get_sos()
          } else if (unread_marker.value == M_EOI.to[UInt8]) {
            println("M_EOI")
            scan_ptr := -1
          } else {
            // Do nothing (probably 0xe0)
          }
        }{sow_SOI => mux(scan_ptr == -1.to[Int], -1, 1)}
      }

      def jpeg_init_decompress(): Unit = {
        def huff_make_dhuff_tb(idx: Int, p_xhtbl_bits: SRAM2[UInt8], p_dhtbl_ml_mem: SRAM1[UInt16], p_dhtbl_maxcode: SRAM2[UInt16], p_dhtbl_mincode: SRAM2[UInt16], p_dhtbl_valptr: SRAM2[UInt16]): Unit = {
          val huffsize = SRAM[UInt16](257)
          val huffcode = SRAM[UInt16](257)
          val p = Reg[Int](0)
          Foreach(1 until 17 by 1) { i => 
            val j_max = (p_xhtbl_bits(idx, i) + 1).as[Int]
            Foreach(1 until j_max by 1) { j => 
              huffsize(p) = i.as[UInt16]
              p :+= 1
            }
          }
          huffsize(p) = 0
          val lastp = p
          val code = Reg[UInt16](0)
          val size = Reg[UInt16](0)
          size := huffsize(0)
          val pp = Reg[Int](0)
          code.reset


          FSM[Int](whilst1 => whilst1 != -1) { whilst1 => 
            FSM[Int](whilst2 => whilst2 != -1) { whilst2 => 
              // println("huffocde " + pp + " = " + code )
              huffcode(pp) = code.value
              code :+= 1
              pp :+= 1
            } {whilst2 => mux((huffsize(pp) == size.value) && (pp < 257), whilst2, -1)}

            // println("at " + pp + " size is " + huffsize(pp))
            val break_cond = huffsize(pp) == 0.to[UInt16]

            // if (!break_cond) { // Issue #160
            FSM[Int](whilst2 => whilst2 != -1) { whilst2 => 
              if (!break_cond) {
                code := code << 1
                size :+= 1
              }
            } { whilst2 => mux((huffsize(pp) == size.value) || break_cond, -1, whilst2)}
            // }
          } {whilst1 => mux((huffsize(pp) == 0.to[UInt16]), -1, whilst1)}

          val p_dhtbl_ml = Reg[Int](1)
          val ppp = Reg[Int](0)
          p_dhtbl_ml.reset
          Foreach(1 until 17 by 1){l => 
            if (p_xhtbl_bits(idx, l) == 0.to[UInt8]) {
              p_dhtbl_maxcode(idx, l) = 65535.to[UInt16] // Signifies skip
            } else {
              p_dhtbl_valptr(idx, l) = ppp.value.as[UInt16]
              p_dhtbl_mincode(idx,l) = huffcode(ppp)
              Pipe{ppp :+= p_xhtbl_bits(idx,l).as[Int] - 1}
              p_dhtbl_maxcode(idx,l) = huffcode(ppp)
              p_dhtbl_ml := l
              println(" " + p_dhtbl_valptr(idx,l) + " " + p_dhtbl_mincode(idx,l) + " " + ppp + " " + p_dhtbl_maxcode(idx,l) + " " + p_dhtbl_ml)
              Pipe{ppp :+= 1}
            }
          }
          p_dhtbl_maxcode(idx, p_dhtbl_ml.value) = p_dhtbl_maxcode(idx, p_dhtbl_ml.value) + 1
          p_dhtbl_ml_mem(idx) = p_dhtbl_ml.value.as[UInt16]
        }

        p_jinfo_MCUHeight := (p_jinfo_image_height - 1) / 8 + 1
        p_jinfo_MCUWidth := (p_jinfo_image_width - 1) / 8 + 1
        p_jinfo_NumMCU := p_jinfo_MCUHeight * p_jinfo_MCUWidth

        huff_make_dhuff_tb(0, p_jinfo_dc_xhuff_tbl_bits, p_jinfo_dc_dhuff_tbl_ml, p_jinfo_dc_dhuff_tbl_maxcode,p_jinfo_dc_dhuff_tbl_mincode,p_jinfo_dc_dhuff_tbl_valptr)
        huff_make_dhuff_tb(1, p_jinfo_dc_xhuff_tbl_bits, p_jinfo_dc_dhuff_tbl_ml, p_jinfo_dc_dhuff_tbl_maxcode,p_jinfo_dc_dhuff_tbl_mincode,p_jinfo_dc_dhuff_tbl_valptr)
        huff_make_dhuff_tb(0, p_jinfo_ac_xhuff_tbl_bits, p_jinfo_ac_dhuff_tbl_ml, p_jinfo_ac_dhuff_tbl_maxcode,p_jinfo_ac_dhuff_tbl_mincode,p_jinfo_ac_dhuff_tbl_valptr)
        huff_make_dhuff_tb(1, p_jinfo_ac_xhuff_tbl_bits, p_jinfo_ac_dhuff_tbl_ml, p_jinfo_ac_dhuff_tbl_maxcode,p_jinfo_ac_dhuff_tbl_mincode,p_jinfo_ac_dhuff_tbl_valptr)

      }

      def decode_start(): Unit = {

        // Go to data start
        scan_ptr := p_jinfo_jpeg_data.value
        val read_position = Reg[UInt8](0)
        val read_position_idx = Reg[Int](-1)
        val current_read_byte = Reg[UInt8](0)
        val CurrentMCU = Reg[UInt16](0)
        val HuffBuff = SRAM[UInt16](NUM_COMPONENT,DCTSIZE2)
        val IDCTBuff = SRAM[UInt16](6,DCTSIZE2)

        def pgetc(): UInt8 = {
          val tmp = read_byte()
          if (tmp == 255.to[UInt8]){
            if (read_byte() != 0.to[UInt8]){
              println("Unanticipated marker detected.")
              0.to[UInt8]
            } else {
              255.to[UInt8]
            }
          } else {
            tmp
          }
          // 0.to[UInt8]
        }
        def buf_getb(): UInt8 = {
          if (read_position_idx.value < 0.to[Int]) {
            current_read_byte := pgetc()
            read_position := 0x80.to[UInt8]
            read_position_idx := 7.to[Int]
          }
          val ret = if ((current_read_byte.value & read_position.value.as[UInt8]) == 0.to[UInt8]){
                      0.to[UInt8]
                    } else {
                      1.to[UInt8]
                    }
          if (read_position_idx.value == 0.to[Int]){read_position_idx := -1} else {read_position := read_position.value >> 1; read_position_idx :-= 1}
          ret
        }
        def buf_getv(n: UInt8): UInt8 = {
          val ret = Reg[UInt8](0)
          val p = n - 1 - read_position_idx.value.as[UInt8]
          if (read_position_idx.value > 23.to[Int]) { // Not sure how this is ever possible
            val rv = Reg[UInt8](0)
            // val shifter = Reduce(Reg[UInt8](1))(p.as[Int] by 1){i => 2}{_*_}
            rv := current_read_byte.value
            // rv = (current_read_byte << p);  /* Manipulate buffer */
            // current_read_byte := pgetc().as[UInt8]
          //   val new_rv = Reg[UInt8](0)
          //   new_rv := current_read_byte.value
          //   Fold(new_rv)((8 - p).as[Int] by 1){_ => Reg[UInt8](0)}{(a,b) => a >> 1}
          //   val final_rv = new_rv | rv
          //   read_position_idx := (7 - p).as[Int]
          //   read_position := Reduce(Reg[UInt8](1))(read_position_idx - 1 by 1){_ => Reg[UInt8](0)}{(a,b) => a << 1}
          //   ret := (final_rv)// & lmask[n])
          //   // more specifically here
            rv.value
          }
          else {ret.value}
        }
        def DecodeHuffman(tbl_no: Index, use_dc: Boolean): UInt8 = {
          val code = Reg[UInt8](0)
          code := buf_getb()
          val l = Reg[Int](1)
          val max_decode = Reg[UInt16](0)
          FSM[Int](whilst => whilst != -1.to[Int]){whilst => 
            code := (code << 1) + buf_getb()
            max_decode := mux(use_dc, p_jinfo_dc_dhuff_tbl_maxcode(tbl_no, l), p_jinfo_ac_dhuff_tbl_maxcode(tbl_no, l))
            l :+= 1
          }{whilst => mux(code.value > max_decode.value.as[UInt8], -1, whilst)}
          val tbl_ml = mux(use_dc, p_jinfo_dc_dhuff_tbl_ml(tbl_no), p_jinfo_ac_dhuff_tbl_ml(tbl_no))
          if (code < tbl_ml.as[UInt8]) {
            val ptr = mux(use_dc, p_jinfo_dc_dhuff_tbl_valptr(tbl_no, l), p_jinfo_ac_dhuff_tbl_valptr(tbl_no, l))
            val mincode = mux(use_dc, p_jinfo_dc_dhuff_tbl_mincode(tbl_no, l), p_jinfo_ac_dhuff_tbl_mincode(tbl_no, l))
            val p = ptr + code.value.as[UInt16] - mincode
            p_jinfo_dc_xhuff_tbl_huffval(tbl_no, p.as[Int])
          } else {
            println("HUFFMAN READ ERROR")
            0.to[UInt8]
          }
        }
        def DecodeHuffMCU(num_cmp: Index): Unit = {
          val tbl_no = component_acdc(num_cmp).dc
          val s = DecodeHuffman(tbl_no.to[Index], true)
          if (s != 0.to[UInt8]){
            val diff = buf_getv(s)
            // Was working here

          }
        }

        def decode_block(comp_no: Index, id1: Index, id2:Index): Unit = {
          val QuantBuff = SRAM[UInt16](DCTSIZE2)
          DecodeHuffMCU(comp_no)

        }

        Foreach(NUM_COMPONENT by 1) { i => HuffBuff(i,0) = 0 }
        Foreach(RGB_NUM by 1) { i => out_data_comp_vpos(i) = 0; out_data_comp_hpos(i) = 0 }
        if (p_jinfo_smp_fact == SF4_1_1.to[UInt2]) {
          println("Decode 4:1:1")
          FSM[Int](whilst => whilst != 1){ whilst => 
            Sequential.Foreach(4 by 1) { i => 
              decode_block(0, i, 0)
            }

          }{ whilst => mux(CurrentMCU.value < p_jinfo_NumMCU.value, whilst, -1)}
        } else {
          // TODO: Implement this                
          println("Decode 1:1:1")
        }


      }


      read_markers()

      jpeg_init_decompress()

      decode_start()


    }

    val gold_bmp = loadCSV2D[UInt8]("/remote/regression/data/machsuite/jpeg_input.csv", ",", "\n")
    // printMatrix(gold_bmp, "gold")
  }
}

object MPEG2 extends SpatialApp { // DISABLED Regression (Dense) // Args: none
  override val target = AWS_F1
  type UInt8 = FixPt[FALSE, _8, _0]
  type UInt2 = FixPt[FALSE, _2, _0]
  type UInt16 = FixPt[FALSE, _16, _0]
  type UInt = FixPt[FALSE, _32, _0]

  @virtualize
  def main() = {


    Accel{
      val inPMV = LUT[UInt16](2,2,2)(45,  207,
                                     70,  41,

                                     4,   180,
                                     120, 216)
      val inmvfs = LUT[UInt16](2,2)(232, 200, 
                                    32,  240)
      val outPMV = LUT[UInt16](2,2,2)(1566, 206,
                                      70,   41,

                                      1566, 206,
                                      120, 216)
      val outmvfs = LUT[UInt16](2,2)(0, 200,
                                     0, 240)
    }

  }
}



