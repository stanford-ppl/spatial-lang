import spatial._
import org.virtualized._

object AES extends SpatialApp { // Regression (Dense) // Args: none
  import IR._

  /*
  TODO: Optimize/parallelize many of the memory accesses here and pipeline as much as possible
  
  MachSuite Concerns: 
  	- Mix rows math seemed wrong in their implementation
  	- Not exactly sure what was going on with their expand_key step
	*/
  type UInt8 = FixPt[FALSE,_8,_0]
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
  	val niter = ArgIn[Int] // 15
  	setArg(niter, args(0).to[Int])
  	// val key_debug = DRAM[UInt8](32)

  	Accel{
  		// Setup data structures
  		val plaintext_flat = SRAM[UInt8](16)
  		val plaintext_sram = SRAM[UInt8](4,4)
  		val sbox_sram = SRAM[UInt8](256)
  		val key_sram = SRAM[UInt8](32)
  		// val mix_lut = LUT[Int](4,4)(
  		// 		2, 3, 1, 1,
  		// 		1, 2, 3, 1,
  		// 		1, 1, 2, 3,
  		// 		3, 1, 1, 2
  		// 	)
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
		  		// val e = Reduce(Reg[UInt8](0))(4 by 1 par 4) { i => col(i) }{_^_}
		  		val e = col(0) ^ col(1) ^ col(2) ^ col(3)
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
	  		plaintext_sram(i,j) = plaintext_flat(j*4+i) // MachSuite flattens columnwise... Why????
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
	  		plaintext_flat(j*4+i) = plaintext_sram(i,j)
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
  	println("PASS: " + cksum + " (AES) * For retiming, need to fix ^ reduction if not parallelized")

  }
}


object Viterbi extends SpatialApp { // Regression (Dense) // Args: none
  import IR._

  /*

                    ←       N_OBS            →

          State 63 ----- State 63 ----- State 63                
        /  .        \ /            \ /                     P(obs | state) = emission_probs   
       /   .         X              X                                         (N_STATES x N_TOKENS)
      /    .        / \            / \                        
     O----State k  ----- State k  ----- State k  ...            
      \    .        \ /            \ /                          
       \   .         X              X                          shortest path to (state, obs) = llike
        \  .        / \            / \                                                          (N_OBS x N_STATES)
          State 0  ----- State 0  ----- State 0                  
      ↑               ↑                                                           
    init_states     transition_probs                        
     (N_STATES)       (N_STATES x N_STATES)

						obs_vec
						 (N_OBS, max value = N_TOKENS)


	TODO: Eliminate backprop step and do everything feed-forward
	MachSuite Concerns:
		- Constructing path step by step seems to give the wrong result because they do extra math in the backprop step. 
		       Why do you need to do math when going backwards? I thought you just read off the result

  */

  type T = FixPt[TRUE,_16,_16]

  @virtualize
  def main() = {
  	// Setup dimensions of problem
  	val N_STATES = 64
  	val N_TOKENS = 64
  	val N_OBS = 140

  	// debugging
  	// val steps_to_take = ArgIn[Int]
  	// setArg(steps_to_take, args(0).to[Int])

  	// Setup data
  	val init_states = Array[T](4.6977033615112305.to[T],3.6915655136108398.to[T],4.8652229309082031.to[T],4.7658410072326660.to[T],
  		4.0006790161132812.to[T],3.9517300128936768.to[T],3.4640796184539795.to[T],3.4600069522857666.to[T],4.2856273651123047.to[T],
  		3.6522088050842285.to[T],4.8189344406127930.to[T],3.8075556755065918.to[T],3.8743767738342285.to[T],5.4135279655456543.to[T],
  		4.9173111915588379.to[T],3.6458325386047363.to[T],5.8528852462768555.to[T],11.3210048675537109.to[T],4.9971127510070801.to[T],
  		5.1006979942321777.to[T],3.5980830192565918.to[T],5.3161897659301758.to[T],3.4544019699096680.to[T],3.7314746379852295.to[T],
  		4.9998908042907715.to[T],3.4898567199707031.to[T],4.2091164588928223.to[T],3.5122559070587158.to[T],3.9326364994049072.to[T],
  		7.2767667770385742.to[T],3.6539671421051025.to[T],4.0916681289672852.to[T],3.5044839382171631.to[T],4.5234117507934570.to[T],
  		3.7673256397247314.to[T],4.0265331268310547.to[T],3.7147023677825928.to[T],6.7589721679687500.to[T],3.5749390125274658.to[T],
  		3.7701597213745117.to[T],3.5728175640106201.to[T],5.0258340835571289.to[T],4.9390106201171875.to[T],5.7208223342895508.to[T],
  		6.3652114868164062.to[T],3.5838112831115723.to[T],5.0102572441101074.to[T],4.0017414093017578.to[T],4.2373661994934082.to[T],
  		3.8841004371643066.to[T],5.3679313659667969.to[T],3.9980680942535400.to[T],3.5181968212127686.to[T],4.7306714057922363.to[T],
  		5.5075111389160156.to[T],5.1880970001220703.to[T],4.8259010314941406.to[T],4.2589011192321777.to[T],5.6381106376647949.to[T],
  		3.4522385597229004.to[T],3.5920252799987793.to[T],4.2071061134338379.to[T],5.0856294631958008.to[T],6.0637059211730957.to[T])

  	val obs_vec = Array[Int](0,27,49,52,20,31,63,63,29,0,47,4,38,38,38,38,4,43,7,28,31,
  											 7,7,7,57,2,2,43,52,52,43,3,43,13,54,44,51,32,9,9,15,45,21,
  											 33,61,45,62,0,55,15,55,30,13,13,53,13,13,50,57,57,34,26,21,
  											 43,7,12,41,41,41,17,17,30,41,8,58,58,58,31,52,54,54,54,54,
  											 54,54,15,54,54,54,54,52,56,52,21,21,21,28,18,18,15,40,1,62,
  											 40,6,46,24,47,2,2,53,41,0,55,38,5,57,57,57,57,14,57,34,37,
  											 57,30,30,5,1,5,62,25,59,5,2,43,30,26,38,38)

  	val raw_transitions = loadCSV1D[T]("/remote/regression/data/machsuite/viterbi_transition.csv", "\n")
  	val raw_emissions = loadCSV1D[T]("/remote/regression/data/machsuite/viterbi_emission.csv", "\n")
  	val transitions = raw_transitions.reshape(N_STATES, N_STATES)
  	val emissions = raw_emissions.reshape(N_STATES, N_TOKENS)

  	val correct_path = Array[Int](27,27,27,27,27,31,63,63,63,63,47,4,38,38,38,38,7,7,7,
  																7,7,7,7,7,2,2,2,43,52,52,43,43,43,43,43,44,44,32,9,9,
  																15,45,45,45,45,45,45,0,55,55,55,30,13,13,13,13,13,13,
  																57,57,21,21,21,21,7,41,41,41,41,17,17,30,41,41,58,58,
  																58,31,54,54,54,54,54,54,54,54,54,54,54,54,52,52,52,21,
  																21,21,28,18,18,40,40,40,40,40,40,46,46,2,2,2,53,53,53,
  																55,38,57,57,57,57,57,57,57,57,57,57,30,30,5,5,5,5,5,5,
  																5,5,30,30,26,38,38)
  	// Handle DRAMs
  	val init_dram = DRAM[T](N_STATES)
  	val obs_dram = DRAM[Int](N_OBS)
  	val transitions_dram = DRAM[T](N_STATES,N_STATES)
  	val emissions_dram = DRAM[T](N_STATES,N_TOKENS)
  	// val llike_dram = DRAM[T](N_OBS,N_STATES)
  	val path_dram = DRAM[Int](N_OBS)
  	setMem(init_dram,init_states)
  	setMem(obs_dram, obs_vec)
  	setMem(transitions_dram,transitions)
  	setMem(emissions_dram,emissions)


  	Accel{
  		// Load data structures
  		val obs_sram = SRAM[Int](N_OBS)
  		val init_sram = SRAM[T](N_STATES)
  		val transitions_sram = SRAM[T](N_STATES,N_STATES)
  		val emissions_sram = SRAM[T](N_STATES,N_TOKENS)
  		val llike_sram = SRAM[T](N_OBS, N_STATES)
  		val path_sram = SRAM[Int](N_OBS)

  		Parallel{
  			obs_sram load obs_dram
  			init_sram load init_dram
  			transitions_sram load transitions_dram
  			emissions_sram load emissions_dram
  		}

  		// from --> to
  		Sequential.Foreach(0 until N_OBS) { step => 
  			val obs = obs_sram(step)
  			Sequential.Foreach(0 until N_STATES) { to => 
	  			val emission = emissions_sram(to, obs)
  				val best_hop = Reg[T](15)
  				best_hop.reset
  				Reduce(best_hop)(0 until N_STATES) { from => 
  					val base = llike_sram(step-1, from) + transitions_sram(from,to)
  					base + emission
  				} { (a,b) => mux(a < b, a, b)}
  				llike_sram(step,to) = mux(step == 0, emission + init_sram(to), best_hop)
  			}
  		}

  		// to <-- from
  		Sequential.Foreach(N_OBS-1 until -1 by -1) { step => 
  			val from = path_sram(step+1)
  			val min_pack = Reg[Tup2[Int, T]](pack(-1.to[Int], 15.to[T]))
  			min_pack.reset
  			Reduce(min_pack)(0 until N_STATES){ to => 
  				val jump_cost = mux(step == N_OBS-1, 0.to[T], transitions_sram(to, from))
  				val p = llike_sram(step,to) + jump_cost
  				pack(to,p)
  			}{(a,b) => mux(a._2 < b._2, a, b)}
  			path_sram(step) = min_pack._1
  		}

  		// Store results
  		// llike_dram store llike_sram
  		path_dram store path_sram
  	}

  	// Get data structures
  	// val llike = getMatrix(llike_dram)
  	val path = getMem(path_dram)

  	// Print data structures
  	// printMatrix(llike, "log-likelihood")
  	printArray(path, "path taken")
  	printArray(correct_path, "correct path")

  	// Check results
  	val cksum = correct_path.zip(path){_ == _}.reduce{_&&_}
  	println("PASS: " + cksum + " (Viterbi)")

  }
}


object Stencil2D extends SpatialApp { // DISABLED Regression (Dense) // Args: none
  import IR._

  /*
           ←    COLS     →   
         ___________________             ___________________                         
        |                   |           |X  X  X  X  X  X 00|          
    ↑   |    ←3→            |           |                 00|          
        |    ___            |           |    VALID DATA   00|          
        |  ↑|   |           |           |X  X  X  X  X  X 00|          
        |  3|   | ----->    |    ->     |                 00|            
 ROWS   |  ↓|___|           |           |X  X  X  X  X  X 00|          
        |                   |           |                 00|          
        |                   |           |X  X  X  X  X  X 00|          
        |                   |           |                 00|          
    ↓   |                   |           |0000000000000000000|          
        |                   |           |0000000000000000000|          
         ```````````````````             ```````````````````      
                                               
                                               
  */


  @virtualize
  def main() = {

  	// Problem properties
  	val ROWS = 128
  	val COLS = 64
  	val filter_size = 9

  	// Setup data
  	val raw_data = loadCSV1D[Int]("/remote/regression/data/machsuite/stencil2d_data.csv", "\n")
  	val data = raw_data.reshape(ROWS, COLS)

  	// Setup DRAMs
  	val data_dram = DRAM[Int](ROWS,COLS)
  	val result_dram = DRAM[Int](ROWS,COLS)

  	setMem(data_dram, data)

  	Accel {

	  	val filter = LUT[Int](3,3)(468,909,379,
	  														 165,886,771,
	  														 159,963,553)

	  	val lb = LineBuffer[Int](3,COLS)
	  	val sr = RegFile[Int](3,3)
	  	val result_sram = SRAM[Int](ROWS,COLS)
	  	Foreach(ROWS by 1){ i => 
	  		lb load data_dram(i, 0::COLS)
				Foreach(COLS by 1) {j => 
					Foreach(3 by 1 par 3) {k => sr(k,*) <<= lb(k,j)}
					val temp = Reduce(Reg[Int](0))(3 by 1, 3 by 1){(r,c) => sr(r,c) * filter(r,c)}{_+_}
					if (i > 2 && j < COLS-2) {result_sram(i-2,j) = temp}
					else {result_sram(i-2,j) = 0}
				}	  		
	  	}
	  	// Pad with 0's to make regression check happy
	  	Foreach(2 by 1, COLS by 1){ (i,j) => result_sram(ROWS-1-i, j) = 0}

	  	result_dram store result_sram
  	}

  	// Get results
  	val result_data = getMatrix(result_dram)
  	val raw_gold = loadCSV1D[Int]("/remote/regression/data/machsuite/stencil2d_gold.csv", "\n")
  	val gold = raw_gold.reshape(ROWS,COLS)

  	// Printers
  	printMatrix(gold, "gold")
  	printMatrix(result_data, "gold")

  	val cksum = gold.zip(result_data){_==_}.reduce{_&&_}
  	println("PASS: " + cksum + " (Stencil2D)")

  }
}


object Stencil3D extends SpatialApp { // DISABLED Regression (Dense) // Args: none
  import IR._

  /*
                                                                      
                                                                          
      ↗      ___________________                  ___________________                                                                  
   HEIGHT  /                   /|               /000000000000000000 /|                                                                
          / ←    COLS     →   / |              / x  x  x  x  x  00 /0|                        
  ↙      /__________________ /  |             /__________________ / 0|                                                                 
        |                   |   |            |X  X  X  X  X  X 00| x0|      
    ↑   |    ←___           |   |            |                 00|  0|      
        |    /__/           |   |            |    VALID DATA   00|  0|      
        |  ↑|   |           |   |            |X  X  X  X  X  X 00| x0|      
        |  3|   | ----->    |   |   --->     |                 00|  0|        
 ROWS   |  ↓|___|           |   |            |X  X  X  X  X  X 00| x0|      
        |                   |   |            |                 00|  0|      
        |                   |   |            |X  X  X  X  X  X 00| x0|      
        |                   |  /             |                 00| 0/      
    ↓   |                   | /              |0000000000000000000|0/       
        |                   |/               |0000000000000000000|/        
         ```````````````````                  ```````````````````      
                                               
                                               
  */


  @virtualize
  def main() = {

  	// // Problem properties
  	// val ROWS = 128
  	// val COLS = 64
  	// val filter_size = 9

  	// // Setup data
  	// val raw_data = loadCSV1D[Int]("/remote/regression/data/machsuite/stencil2d_data.csv", "\n")
  	// val data = raw_data.reshape(ROWS, COLS)

  	// // Setup DRAMs
  	// val data_dram = DRAM[Int](ROWS,COLS)
  	// val result_dram = DRAM[Int](ROWS,COLS)

  	// setMem(data_dram, data)

  	Accel {
  	}

  	// // Get results
  	// val result_data = getMatrix(result_dram)
  	// val raw_gold = loadCSV1D[Int]("/remote/regression/data/machsuite/stencil2d_gold.csv", "\n")
  	// val gold = raw_gold.reshape(ROWS,COLS)

  	// // Printers
  	// printMatrix(gold, "gold")
  	// printMatrix(result_data, "gold")

  	// val cksum = gold.zip(result_data){_==_}.reduce{_&&_}
  	// println("PASS: " + cksum + " (Stencil2D)")

  }
}
