package spatial.stdlib

import spatial.dsl._
import org.virtualized._
import spatial.metadata._
import argon.core.State

object Convolution {

	val coltile = 480
	val tileSizeM = 16
	val tileSizeN = 16
	val tileSizeK = 16

	@virtualize
	def ConvolutionSlide[T:Type:Num](output: DRAM2[T], 
	                    input: DRAM2[T],
	                    filter: LUT2[T],
	                    colstride: scala.Int, rowstride: scala.Int,
	                	load_par: Index, store_par: Index )(implicit state: State): Unit = {

	  val lb = LineBuffer.strided[T](filter.rows, coltile, rowstride)
	  val sr = RegFile[T](filter.rows, filter.cols)
	  val lineout = SRAM[T](coltile/colstride)
	  Foreach(input.rows by rowstride){row =>
	    lb load input(row::row+rowstride, 0::input.cols par load_par)
	    Foreach(input.cols by colstride){j => 
	      Foreach(filter.rows by 1 par filter.rows){i => sr(i,*) <<= lb(i,j::j+colstride)}
	      lineout(j/colstride) = Reduce(Reg[T](0.to[T]))(filter.rows by 1, filter.cols by 1 par filter.cols){(ii,jj) => 
	        val img = if ((row.to[Int]+rowstride-1) - (filter.rows - 1 - ii.to[Int]) < 0 || (j.to[Int]+colstride-1) - (filter.cols - 1 - jj.to[Int]) < 0) 0.to[T] else sr(ii,filter.cols - 1 - jj)
	        img * filter(ii,jj)
	      }{_+_}
	      // lineout(j/colstride) = mux(row + (rowstride-1) < filter.rows.to[Int]-1 || j + (colstride-1) < filter.cols.to[Int]-1, 0.to[T], Reduce(Reg[T](0.to[T]))(filter.rows by 1, filter.cols by 1){(ii,jj) => sr(ii,jj) * filter(ii,jj)}{_+_}.value)
	    }
	    output(row/rowstride, 0::output.cols par store_par) store lineout
	  }
	}

	@virtualize
	def ConvolutionSlideFast[T:Type:Num](output: DRAM2[T], 
	                    input: DRAM2[T],
	                    filter: LUT2[T],
	                    colstride: scala.Int, rowstride: scala.Int,
	                	load_par: Index, store_par: Index )(implicit state: State): Unit = {

	  val lb = LineBuffer.strided[T](filter.rows, coltile, rowstride)
	  val sr = RegFile[T](filter.rows, filter.cols)
	  val lineout = SRAM[T](coltile/colstride)
	  Foreach(input.rows by rowstride){row =>
	    lb load input(row::row+rowstride, 0::input.cols par load_par)
	    Foreach(input.cols by colstride){j => 
	      Foreach(filter.rows by 1 par filter.rows){i => sr(i,*) <<= lb(i,j::j+colstride)}
	      lineout(j/colstride) = Reduce(Reg[T](0.to[T]))(filter.rows by 1, filter.cols by 1 par filter.cols){(ii,jj) => 
	        val img = if ((row.to[Int]+rowstride-1) - (filter.rows - 1 - ii.to[Int]) < 0 || (j.to[Int]+colstride-1) - (filter.cols - 1 - jj.to[Int]) < 0) 0.to[T] else sr(ii,filter.cols - 1 - jj)
	        img * filter(ii,jj)
	      }{_+_}
	      // lineout(j/colstride) = mux(row + (rowstride-1) < filter.rows.to[Int]-1 || j + (colstride-1) < filter.cols.to[Int]-1, 0.to[T], Reduce(Reg[T](0.to[T]))(filter.rows by 1, filter.cols by 1){(ii,jj) => sr(ii,jj) * filter(ii,jj)}{_+_}.value)
	    }
	    output(row/rowstride, 0::output.cols par store_par) store lineout
	  }
	}


	@virtualize
	def MultichannelConvolutionSlide[T:Type:Num](output: DRAM2[T], 
	                    input: DRAM3[T],
	                    filter: LUT3[T],
	                    colstride: scala.Int, rowstride: scala.Int,
	                	load_par: Index, store_par: Index, layer_par: Index)(implicit state: State): Unit = {

		  Foreach(input.dim1 by rowstride){row =>
		  	val lineout = SRAM[T](coltile/colstride)
			val lineout_temps = List.tabulate(3){_ => SRAM[T](coltile/colstride)}
			val lbs = List.tabulate(3){_ => LineBuffer.strided[T](filter.dim1, coltile, rowstride)}
			val srs = List.tabulate(3){_ => RegFile[T](filter.dim1, filter.dim2)}
			lbs.zip(srs.zip(lineout_temps)).zipWithIndex.foreach{case ((lb, (sr,lo)), i) =>
			  lb load input(i, row::row+rowstride, 0::input.dim2 par load_par)
			  Parallel {
				Foreach(input.dim2 by colstride){j =>
				  Foreach(filter.dim1 by 1 par filter.dim1){i => sr(i,*) <<= lb(i,j::j+colstride)} 
				  lo(j) = Reduce(Reg[T](0.to[T]))(filter.dim1 by 1, filter.dim2 by 1){(ii,jj) => 
			        val img = if ((row.to[Int]+rowstride-1) - (filter.dim1 - 1 - ii.to[Int]) < 0 || (j.to[Int]+colstride-1) - (filter.dim2 - 1 - jj.to[Int]) < 0) 0.to[T] else sr(ii,filter.dim2 - 1 - jj)
			        img * filter(i,ii,jj)
				  }{_+_}
				}
			  }
			}
			Foreach(input.dim2 by 1){ j => lineout(j) = lineout_temps.map{t => t(j)}.reduce{_+_} }
			output(row/rowstride, 0::output.cols par store_par) store lineout
		  }
			// // MemReduce(lineout par 1/*red_par*/)(input.dim0 by 1 par layer_par) { plane =>  // Can't do this because it messes up lb control signals
			//   val lbs = List.tabulate(3){_ => LineBuffer.strided[T](filter.dim1, coltile, rowstride)}
			//   val srs = List.tabulate(3){_ => RegFile[T](filter.dim1, filter.dim2)}
			//   // val lineout_local = List.tabulate(3){_ => SRAM[T](coltile/colstride)}
			//   for (plane <- 0 until 3) {
			//   	lbs(plane) load input(plane, row, 0::input.dim2 par load_par)
			//     Foreach(filter.dim1 by 1 par filter.dim1){i => srs(plane)(i,*) <<= lbs(plane)(i,j::j+colstride)}
			//   }
		 //      lineout(j/colstride) = (0 until 3).map{plane => 
		 //      	Reduce(Reg[T](0.to[T]))(filter.dim1 by 1, filter.dim2 by 1){(ii,jj) => 
		 //          val img = if ((row.to[Int]+rowstride-1) - (filter.dim1 - 1 - ii.to[Int]) < 0 || (j.to[Int]+colstride-1) - (filter.dim2 - 1 - jj.to[Int]) < 0) 0.to[T] else srs(plane)(ii,filter.dim2 - 1 - jj)
		 //          img * filter(plane,ii,jj)
		 //        }{_+_}
		 //      }.reduce{_+_}

		 //      output(row/rowstride, 0::output.cols par store_par) store lineout
		 //    }
		 //  Foreach(input.dim1 by rowstride){row =>
		 //    val lineout = SRAM[T](coltile/colstride)
			// MemReduce(lineout par 1/*red_par*/)(input.dim0 by 1 par layer_par) { plane =>  // Can't do this because it messes up lb control signals
			//   lb load input(plane, row, 0::input.dim2 par load_par) // TODO: load with correct rowstride
			//   Foreach(input.dim2 by colstride){j => 
			//     Foreach(filter.dim1 by 1 par filter.dim1){i => sr(i,*) <<= lb(i,j::j+colstride)}
			//     lineout_local(j/colstride) = Reduce(Reg[T](0.to[T]))(filter.dim1 by 1, filter.dim2 by 1){(ii,jj) => 
			//       val img = if ((row.to[Int]+rowstride-1) - (filter.dim1 - 1 - ii.to[Int]) < 0 || (j.to[Int]+colstride-1) - (filter.dim2 - 1 - jj.to[Int]) < 0) 0.to[T] else sr(ii,filter.dim2 - 1 - jj)
			//       img * filter(plane,ii,jj)
			//     }{_+_}
			//     // lineout(j/colstride) = mux(row + (rowstride-1) < filter.rows.to[Int]-1 || j + (colstride-1) < filter.cols.to[Int]-1, 0.to[T], Reduce(Reg[T](0.to[T]))(filter.rows by 1, filter.cols by 1){(ii,jj) => sr(ii,jj) * filter(ii,jj)}{_+_}.value)
			//   }
			//   lineout_local
			// }{_+_}
		 //    output(row/rowstride, 0::output.cols par store_par) store lineout
		 //  }

	}


	@virtualize
	def ConvolutionGEMM[T:Type:Num](output: DRAM1[T], 
	                    input: DRAM1[T],
	                    filter: DRAM2[T])(implicit state: State): Unit = {    
	  Foreach(filter.rows by tileSizeM par 1/*m_outer_par*/){i =>
	    // Compute leftover dim
	    val elements_m = min(lift(tileSizeM), filter.rows - i)
	    // Create Y tile
	    val y_tile = SRAM[T](tileSizeM)
	    MemReduce(y_tile par 1/*y_reduce_par*/)(filter.cols by tileSizeN par 1/*n_outer_par*/){j =>
	      // Compute leftover dim
	      val elements_n = min(lift(tileSizeN), filter.cols - j)
	      // Create local Y tile for accumulating
	      val y_tile_local = SRAM[T](tileSizeM)
	      // Create X tile
	      val x_tile = SRAM[T](tileSizeN)
	      // Load vector tile
	      x_tile load input(j::j+elements_n par 1/*load_par*/)
	      // Create A tile
	      val a_tile = SRAM[T](tileSizeM, tileSizeN)
	      // Load matrix tile
	      a_tile load filter(i::i+elements_m, j::j+elements_n par 1/*load_par*/)
	      Foreach(elements_m by 1 par 1/*m_inner_par*/){ii => 
	        y_tile_local(ii) = Reduce(Reg[T])(elements_n by 1 par 1/*n_inner_par*/){jj => 
	          a_tile(ii,jj) * x_tile(jj)
	        }{_+_}
	      }
	      y_tile_local
	    }{_+_}
	    output(i::i+elements_m par 1/*store_par*/) store y_tile
	  }
	}

}