import spatial._
import org.virtualized._

object AES extends SpatialApp { // Regression (Dense) // Args: none
	/*
		Properties:
			256-bit (32-byte) key ???
			128-bit (16-byte) plaintext
		
	*/
  import IR._

  type UInt8 = FixPt[FALSE,_8,_0]
  @virtualize
  def naive_aes(plaintext_sram: SRAM[UInt8], sbox_sram: SRAM[UInt8], 
  							key_sram: SRAM[UInt8], mix_lut: LUT[UInt8]): Unit = {

  }


  @virtualize
  def main() = {
  	// Setup off-chip data
	  val plaintext = Array[UInt8](0,17,34,51,68,85,102,119,136,153,170,187,204,221,238,255)
	  val key = Array.tabulate(32){i => i.to[UInt8]}
  	val sbox = Array[UInt8]( // 256 elements
	    0x63, 0x7c, 0x77, 0x7b, 0xf2, 0x6b, 0x6f, 0xc5,
	    0x30, 0x01, 0x67, 0x2b, 0xfe, 0xd7, 0xab, 0x76,
	    0xca, 0x82, 0xc9, 0x7d, 0xfa, 0x59, 0x47, 0xf0,
	    0xad, 0xd4, 0xa2, 0xaf, 0x9c, 0xa4, 0x72, 0xc0,
	    0xb7, 0xfd, 0x93, 0x26, 0x36, 0x3f, 0xf7, 0xcc,
	    0x34, 0xa5, 0xe5, 0xf1, 0x71, 0xd8, 0x31, 0x15,
	    0x04, 0xc7, 0x23, 0xc3, 0x18, 0x96, 0x05, 0x9a,
	    0x07, 0x12, 0x80, 0xe2, 0xeb, 0x27, 0xb2, 0x75,
	    0x09, 0x83, 0x2c, 0x1a, 0x1b, 0x6e, 0x5a, 0xa0,
	    0x52, 0x3b, 0xd6, 0xb3, 0x29, 0xe3, 0x2f, 0x84,
	    0x53, 0xd1, 0x00, 0xed, 0x20, 0xfc, 0xb1, 0x5b,
	    0x6a, 0xcb, 0xbe, 0x39, 0x4a, 0x4c, 0x58, 0xcf,
	    0xd0, 0xef, 0xaa, 0xfb, 0x43, 0x4d, 0x33, 0x85,
	    0x45, 0xf9, 0x02, 0x7f, 0x50, 0x3c, 0x9f, 0xa8,
	    0x51, 0xa3, 0x40, 0x8f, 0x92, 0x9d, 0x38, 0xf5,
	    0xbc, 0xb6, 0xda, 0x21, 0x10, 0xff, 0xf3, 0xd2,
	    0xcd, 0x0c, 0x13, 0xec, 0x5f, 0x97, 0x44, 0x17,
	    0xc4, 0xa7, 0x7e, 0x3d, 0x64, 0x5d, 0x19, 0x73,
	    0x60, 0x81, 0x4f, 0xdc, 0x22, 0x2a, 0x90, 0x88,
	    0x46, 0xee, 0xb8, 0x14, 0xde, 0x5e, 0x0b, 0xdb,
	    0xe0, 0x32, 0x3a, 0x0a, 0x49, 0x06, 0x24, 0x5c,
	    0xc2, 0xd3, 0xac, 0x62, 0x91, 0x95, 0xe4, 0x79,
	    0xe7, 0xc8, 0x37, 0x6d, 0x8d, 0xd5, 0x4e, 0xa9,
	    0x6c, 0x56, 0xf4, 0xea, 0x65, 0x7a, 0xae, 0x08,
	    0xba, 0x78, 0x25, 0x2e, 0x1c, 0xa6, 0xb4, 0xc6,
	    0xe8, 0xdd, 0x74, 0x1f, 0x4b, 0xbd, 0x8b, 0x8a,
	    0x70, 0x3e, 0xb5, 0x66, 0x48, 0x03, 0xf6, 0x0e,
	    0x61, 0x35, 0x57, 0xb9, 0x86, 0xc1, 0x1d, 0x9e,
	    0xe1, 0xf8, 0x98, 0x11, 0x69, 0xd9, 0x8e, 0x94,
	    0x9b, 0x1e, 0x87, 0xe9, 0xce, 0x55, 0x28, 0xdf,
	    0x8c, 0xa1, 0x89, 0x0d, 0xbf, 0xe6, 0x42, 0x68,
	    0x41, 0x99, 0x2d, 0x0f, 0xb0, 0x54, 0xbb, 0x16
	  )
  	
  	// Create DRAMs
  	val plaintext_dram = DRAM[UInt8](16)
  	val key_dram = DRAM[UInt8](32)
  	val sbox_dram = DRAM[UInt8](256)
  	val ciphertext_dram = DRAM[UInt8](16)

  	// Transfer data to DRAMs
  	setMem(plaintext_dram, plaintext)
  	setMem(key_dram, key)
  	setMem(sbox_dram, sbox)

  	// Debugging support
  	val niter = 15 //ArgIn[Int]
  	// setArg(niter, args(0).to[Int])
  	// val key_debug = DRAM[UInt8](32)

  	Accel{
  		// Setup data structures
  		val plaintext_flat = SRAM[UInt8](16)
  		val plaintext_sram = SRAM[UInt8](4,4)
  		val sbox_sram = SRAM[UInt8](256)
  		val key_sram = SRAM[UInt8](32)
  		val mix_lut = LUT[Int](4,4)(
  				2, 3, 1, 1,
  				1, 2, 3, 1,
  				1, 1, 2, 3,
  				3, 1, 1, 2
  			)
  		val rcon = Reg[UInt8](1)

  		// Specify methods
		  def expand_key(): Unit = {
		    Pipe{key_sram(0) = key_sram(0) ^ sbox_sram(key_sram(29).as[Int]) ^ rcon}
		    Pipe{key_sram(1) = key_sram(1) ^ sbox_sram(key_sram(30).as[Int])}
		    Pipe{key_sram(2) = key_sram(2) ^ sbox_sram(key_sram(31).as[Int])}
		    Pipe{key_sram(3) = key_sram(3) ^ sbox_sram(key_sram(28).as[Int])}
		    rcon := (((rcon)<<1) ^ ((((rcon)>>7) & 1) * 0x1b))

		    Sequential.Foreach(4 until 16 by 4) {i =>
		    	Pipe{key_sram(i) = key_sram(i) ^ key_sram(i-4)}
		    	Pipe{key_sram(i+1) = key_sram(i+1) ^ key_sram(i-3)}
		    	Pipe{key_sram(i+2) = key_sram(i+2) ^ key_sram(i-2)}
		    	Pipe{key_sram(i+3) = key_sram(i+3) ^ key_sram(i-1)}
		    }
			
				Pipe{key_sram(16) = key_sram(16) ^ sbox_sram(key_sram(12).as[Int])}
				Pipe{key_sram(17) = key_sram(17) ^ sbox_sram(key_sram(13).as[Int])}
				Pipe{key_sram(18) = key_sram(18) ^ sbox_sram(key_sram(14).as[Int])}
				Pipe{key_sram(19) = key_sram(19) ^ sbox_sram(key_sram(15).as[Int])}

				Sequential.Foreach(20 until 32 by 4) {i => 
					Pipe{key_sram(i) = key_sram(i) ^ key_sram(i-4)}
					Pipe{key_sram(i+1) = key_sram(i+1) ^ key_sram(i-3)}
					Pipe{key_sram(i+2) = key_sram(i+2) ^ key_sram(i-2)}
					Pipe{key_sram(i+3) = key_sram(i+3) ^ key_sram(i-1)}
				}
		  }

		  def shift_rows(): Unit = {
	  		Sequential.Foreach(4 by 1){ i => 
	  			val row = RegFile[UInt8](4)
	  			Foreach(4 by 1){ j => 
		  			val col_addr = (j - i) % 4
		  			row(col_addr) = plaintext_sram(i,j)
		  		}
		  		Foreach(4 by 1){ j => 
		  			plaintext_sram(i,j) = row(j)
		  		}
	  		}
		  }

		  def substitute_bytes(): Unit = {
	  		Sequential.Foreach(4 by 1, 4 by 1){(i,j) => 
	  			val addr = plaintext_sram(i,j).as[Int]
	  			val subst = sbox_sram(addr)
	  			plaintext_sram(i,j) = subst
	  		}
		  }

			def rj_xtime(x: UInt8): UInt8 = {
				mux(((x & 0x80.to[UInt8]) > 0.to[UInt8]), ((x << 1) ^ 0x1b.to[UInt8]), x << 1)
			}

		  def mix_columns(): Unit = {
	  		Sequential.Foreach(4 by 1){j => 
		  		val col = RegFile[UInt8](4)
		  		Sequential.Foreach(4 by 1) { i => col(i) = plaintext_sram(i,j) }
		  		val e = Reduce(Reg[UInt8](0))(4 by 1) { i => col(i) }{_^_}
		  		Pipe{plaintext_sram(0,j) = col(0) ^ e ^ rj_xtime(col(0) ^ col(1))}
		  		Pipe{plaintext_sram(1,j) = col(1) ^ e ^ rj_xtime(col(1) ^ col(2))}
		  		Pipe{plaintext_sram(2,j) = col(2) ^ e ^ rj_xtime(col(2) ^ col(3))}
		  		Pipe{plaintext_sram(3,j) = col(3) ^ e ^ rj_xtime(col(3) ^ col(0))}
	  		}
		  }

		  def add_round_key(round: Index): Unit = {
	  		Foreach(4 by 1, 4 by 1) { (i,j) => 
	  			val key = mux(round % 2 == 1, key_sram(i+j*4+16), key_sram(i+j*4))
	  			plaintext_sram(i,j) = plaintext_sram(i,j) ^ key
	  		}
		  }

  		// Load structures
  		Parallel {
	  		plaintext_flat load plaintext_dram // TODO: Allow dram loads to reshape (gh issue #83)
	  		sbox_sram load sbox_dram
	  		key_sram load key_dram
	  	}

	  	// gh issue #83
	  	Foreach(4 by 1, 4 by 1){(i,j) => 
	  		plaintext_sram(i,j) = plaintext_flat(i+j*4)
	  	} 

	  	// Do AES
	  	Sequential.Foreach(niter by 1) { round => 
	  		// SubBytes
	  		if (round > 0) {
	  			substitute_bytes()
	  		}

	  		// ShiftRows
	  		if (round > 0) {
	  			shift_rows()
	  		}

	  		// MixColumns
	  		if (round > 0 && round < 14 ) {
	  			mix_columns()
	  		}

	  		// Expand key
	  		if (round > 0 && ((round % 2) == 0)) {
	  			expand_key()
	  		}

	  		// AddRoundKey
	  		add_round_key(round)

	  	}

	  	// Reshape plaintext_sram (gh issue # 83)
	  	Foreach(4 by 1, 4 by 1) {(i,j) => 
	  		plaintext_flat(i+j*4) = plaintext_sram(i,j)
	  	}

	  	ciphertext_dram store plaintext_flat

	  	// // Debugging
	  	// key_debug store key_sram

  	}

  	val ciphertext = getMem(ciphertext_dram)
  	val ciphertext_gold = Array[UInt8](142,162,183,202,81,103,69,191,234,252,73,144,75,73,96,137)

  	printArray(ciphertext_gold, "Expected: ")
  	printArray(ciphertext, "Got: ")

  	// // Debugging
  	// val key_dbg = getMem(key_debug)
  	// printArray(key_dbg, "Key: ")

  	val cksum = ciphertext_gold.zip(ciphertext){_ == _}.reduce{_&&_}
  	println("PASS: " + cksum + " (AES)")

  }
}