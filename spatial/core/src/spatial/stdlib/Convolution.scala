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
	      val filter_elements = List.tabulate(3){ii => List.tabulate(3){jj => 
	      	filter(ii,jj)
	      }}.flatten
	      val sr_elements = List.tabulate(3){ii => List.tabulate(3){jj => 
	      	if ((row.to[Int]+rowstride-1) - (filter.rows - 1 - ii.to[Int]) < 0 || (j.to[Int]+colstride-1) - (filter.cols - 1 - jj.to[Int]) < 0) 0.to[T] else sr(ii,filter.cols - 1 - jj)
	      }}.flatten
	      lineout(j/colstride) = sr_elements.zip(filter_elements).map{case (s, f) => s * f}.reduce{_+_}
	      // lineout(j/colstride) = mux(row + (rowstride-1) < filter.rows.to[Int]-1 || j + (colstride-1) < filter.cols.to[Int]-1, 0.to[T], Reduce(Reg[T](0.to[T]))(filter.rows by 1, filter.cols by 1){(ii,jj) => sr(ii,jj) * filter(ii,jj)}{_+_}.value)
	    }
	    output(row/rowstride, 0::output.cols par store_par) store lineout
	  }
	}

	// Multifilter
	@virtualize
	def MFConvolutionSlide[T:Type:Num](output: DRAM3[T], 
	                    input: DRAM2[T],
	                    filter: List[LUT2[T]],
	                    colstride: scala.Int, rowstride: scala.Int,
	                	load_par: Index, store_par: Index )(implicit state: State): Unit = {

	  val lb = LineBuffer.strided[T](filter.head.rows, coltile, rowstride)
	  val sr = RegFile[T](filter.head.rows, filter.head.cols)
	  val lineout = List.tabulate(filter.length){_ => SRAM[T](coltile/colstride)}
	  Foreach(input.rows by rowstride){row =>
	    lb load input(row::row+rowstride, 0::input.cols par load_par)
	    Foreach(input.cols by colstride){j => 
	      Foreach(filter.head.rows by 1 par filter.head.rows){i => sr(i,*) <<= lb(i,j::j+colstride)}
	      val sr_elements = List.tabulate(3){ii => List.tabulate(3){jj => 
	      	if ((row.to[Int]+rowstride-1) - (filter.head.rows - 1 - ii.to[Int]) < 0 || (j.to[Int]+colstride-1) - (filter.head.cols - 1 - jj.to[Int]) < 0) 0.to[T] else sr(ii,filter.head.cols - 1 - jj)
	      }}.flatten
	      lineout.zipWithIndex.foreach{case (lo, page) => 
	        val filter_elements = List.tabulate(3){ii => List.tabulate(3){jj => 
	        	filter(page).apply(ii,jj)
	        }}.flatten
	        lo(j/colstride) = sr_elements.zip(filter_elements).map{case (s, f) => s * f}.reduce{_+_}
	  	  }
	      // lineout(j/colstride) = mux(row + (rowstride-1) < filter.head.rows.to[Int]-1 || j + (colstride-1) < filter.head.cols.to[Int]-1, 0.to[T], Reduce(Reg[T](0.to[T]))(filter.head.rows by 1, filter.head.cols by 1){(ii,jj) => sr(ii,jj) * filter(ii,jj)}{_+_}.value)
	    }
	    Parallel{
		  lineout.zipWithIndex.foreach{case (lo, page) => 
		    output(page, row/rowstride, 0::output.dim2 par store_par) store lo
 		  }
		}
	  }
	}


	// Multichannel
	@virtualize
	def MCConvolutionSlide[T:Type:Num](output: DRAM2[T], 
	                    input: DRAM3[T],
	                    filter: LUT3[T],
	                    colstride: scala.Int, rowstride: scala.Int,
	                	load_par: Index, store_par: Index, channels: scala.Int)(implicit state: State): Unit = {

		  Foreach(input.dim1 by rowstride){row =>
		  	val lineout = SRAM[T](coltile/colstride)
			val lineout_temps = List.tabulate(channels){_ => SRAM[T](coltile/colstride)}
			val lbs = List.tabulate(channels){_ => LineBuffer.strided[T](filter.dim1, coltile, rowstride)}
			val srs = List.tabulate(channels){_ => RegFile[T](filter.dim1, filter.dim2)}
		    lbs.zip(srs.zip(lineout_temps)).zipWithIndex.foreach{case ((lb, (sr,lo)), i) =>
		      lb load input(i, row::row+rowstride, 0::input.dim2 par load_par)
			  Parallel {  // why is this here?
			    Foreach(input.dim2 by colstride){j =>
			      Foreach(filter.dim1 by 1 par filter.dim1){i => sr(i,*) <<= lb(i,j::j+colstride)} 
			      lo(j/colstride) = Reduce(Reg[T](0.to[T]))(filter.dim1 by 1, filter.dim2 by 1){(ii,jj) => 
			        val img = if ((row.to[Int]+rowstride-1) - (filter.dim1 - 1 - ii.to[Int]) < 0 || (j.to[Int]+colstride-1) - (filter.dim2 - 1 - jj.to[Int]) < 0) 0.to[T] else sr(ii,filter.dim2 - 1 - jj)
			        img * filter(i,ii,jj)
			      }{_+_}
			    }
			  }
			}
			Foreach(input.dim2 by 1){ j => lineout(j) = lineout_temps.map{t => t(j)}.reduce{_+_} }
			output(row/rowstride, 0::output.cols par store_par) store lineout
		  }
	}


	// Multichannel, multifilter, assume coltile fits all columns
	@virtualize
	def MCMFConvolutionSlide[T:Type:Num](output: DRAM3[T], 
	                    input: DRAM3[T],
	                    filter: List[LUT3[T]],
	                    colstride: scala.Int, rowstride: scala.Int,
	                	load_par: Index, store_par: Index, channels: scala.Int)(implicit state: State): Unit = {

		  Foreach(input.dim1 by rowstride){row =>
		  	val lineout = List.tabulate(filter.length) {_ => SRAM[T](coltile/colstride)}
			val lineout_temps = List.tabulate(filter.length){_ => List.tabulate(channels) {_ => SRAM[T](coltile/colstride)}} // TODO: Fix hardcoded 3
			val lbs = List.tabulate(channels){_ => LineBuffer.strided[T](filter.head.dim1, coltile, rowstride)} // TODO: Fix hardcoded 3
			val srs = List.tabulate(channels){_ => RegFile[T](filter.head.dim1, filter.head.dim2)} // TODO: Fix hardcoded 3
			lbs.zip(srs).zipWithIndex.foreach{case ((lb, sr), i) =>
			  lb load input(i, row::row+rowstride, 0::input.dim2 par load_par)
				Foreach(input.dim2 by colstride){j =>
				  Foreach(filter.head.dim1 by 1 par filter.head.dim1){i => sr(i,*) <<= lb(i,j::j+colstride)} 
				  lineout_temps.zipWithIndex.foreach{case (lot, p) => 
				    lot(i)(j/colstride) = Reduce(Reg[T](0.to[T]))(filter.head.dim1 by 1, filter.head.dim2 by 1){(ii,jj) => 
			          val img = if ((row.to[Int]+rowstride-1) - (filter.head.dim1 - 1 - ii.to[Int]) < 0 || (j.to[Int]+colstride-1) - (filter.head.dim2 - 1 - jj.to[Int]) < 0) 0.to[T] else sr(ii,filter.head.dim2 - 1 - jj)
			          val f = filter(p).apply(i,ii,jj)
			          img * f
				    }{_+_}
				  }
				}
			}
			Foreach(output.dim2 by 1){ j => 
			  lineout.zip(lineout_temps).zipWithIndex.foreach{case ((lo, lot), i) => 
			    lo(j) = lot.map{t => t(j)}.reduce{_+_}
			  }
			}
			Parallel{
			  lineout.zipWithIndex.foreach{case (lo, p) => 
			  	output(p, row/rowstride, 0::output.dim2 par store_par) store lo
			  }
			}
		  }
	}

	// Multichannel, multifilter, assume coltile fits all columns
	// Regfile version for layer-by-layer assembly of output
	@virtualize
	def MCConvolutionSlide[T:Type:Num](output: DRAM3[T], slice: Index,
	                    input: DRAM3[T],
	                    filter: RegFile3[T],
	                    colstride: scala.Int, rowstride: scala.Int,
	                	load_par: Index, store_par: Index, channels: scala.Int, colsize: scala.Int)(implicit state: State): Unit = {

		  Foreach(input.dim1 by rowstride){row =>
		  	val lineout = SRAM[T](colsize/colstride)
			val lineout_temps = List.tabulate(channels){_ => SRAM[T](colsize/colstride)}
			val lbs = List.tabulate(channels){_ => LineBuffer.strided[T](filter.dim1, colsize, rowstride)}
			val srs = List.tabulate(channels){_ => RegFile[T](filter.dim1, filter.dim2)}
		    lbs.zip(srs.zip(lineout_temps)).zipWithIndex.foreach{case ((lb, (sr,lo)), i) =>
		      lb load input(i, row::row+rowstride, 0::input.dim2 par load_par)
			  Parallel {  // why is this here?
			    Foreach(input.dim2 by colstride){j =>
			      Foreach(filter.dim1 by 1 par filter.dim1){i => sr(i,*) <<= lb(i,j::j+colstride)} 
			      lo(j/colstride) = Reduce(Reg[T](0.to[T]))(filter.dim1 by 1, filter.dim2 by 1){(ii,jj) => 
			        val img = if ((row.to[Int]+rowstride-1) - (filter.dim1 - 1 - ii.to[Int]) < 0 || (j.to[Int]+colstride-1) - (filter.dim2 - 1 - jj.to[Int]) < 0) 0.to[T] else sr(ii,filter.dim2 - 1 - jj)
			        img * filter(i,ii,jj)
			      }{_+_}
			    }
			  }
			}
			Foreach(input.dim2 by 1){ j => lineout(j) = lineout_temps.map{t => t(j)}.reduce{_+_} }
			output(slice, row/rowstride, 0::output.dim2 par store_par) store lineout
		  }
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