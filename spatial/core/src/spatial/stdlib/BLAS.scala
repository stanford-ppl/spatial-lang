package spatial.stdlib

import spatial.dsl._
import org.virtualized._
import spatial.metadata._
import argon.core.State

object BLAS {

	@virtualize
	def Dot[T<:MetaAny[T]:Type:Num](N: Reg[Int], 
	                    X: DRAM1[T], incX: Int,
	                    Y: DRAM1[T], incY: Int,
	                    res: Reg[T])(implicit state: State): Unit = {
	  // Loop over whole vectors
	  val outer_res = Reduce(Reg[T])(N.value by 64/*tileSize*/ par 1/*outer_par*/){i => 
	    // Compute elements left in this tile
	    val elements = min(lift(64)/*tileSize*/, N.value - i)
	    // Create onchip structures
	    val x_tile = SRAM[T](64/*tileSize*/)
	    val y_tile = SRAM[T](64/*tileSize*/)
	    // Load local tiles
	    x_tile load X(i::i+elements par 1/*load_par*/)
	    y_tile load Y(i::i+elements par 1/*load_par*/)
	    // Loop over elements in local tiles
	    val inner_res = Reduce(Reg[T])(elements by 1 par 1/*inner_par*/){j => 
	      x_tile(j) * y_tile(j)
	    }{_+_}
	    inner_res
	  }{_+_}
	  res := outer_res

	}

	@virtualize
	def Axpy[T<:MetaAny[T]:Type:Num](N: Reg[Int], alpha: T, 
	                     X: DRAM1[T], incX: Int,
	                     Y: DRAM1[T], incY: Int,
	                     res: DRAM1[T])(implicit state: State): Unit = {
	  // Loop over whole vectors
	  Foreach(N.value by 64/*tileSize*/ par 1/*outer_par*/){i => 
	    // Compute elements left in this tile
	    val elements = min(lift(64)/*tileSize*/, N.value - i)
	    // Create onchip structures
	    val x_tile = SRAM[T](64/*tileSize*/)
	    val y_tile = SRAM[T](64/*tileSize*/)
	    val z_tile = SRAM[T](64/*tileSize*/)
	    // Load local tiles
	    x_tile load X(i::i+elements par 1/*load_par*/)
	    y_tile load Y(i::i+elements par 1/*load_par*/)
	    // Loop over elements in local tiles
	    Foreach(elements by 1 par 1/*inner_par*/){j => 
	      z_tile(j) = alpha * x_tile(j) + y_tile(j)
	    }
	    // Store tile to DRAM
	    res(i::i+elements par 1/*store_par*/) store z_tile
	  }
	}

	@virtualize
	def Gemm[T<:MetaAny[T]:Type:Num](M: Reg[Int], N: Reg[Int], K: Reg[Int],
	                     alpha: T, 
	                     A: DRAM2[T], lda: Int,
	                     B: DRAM2[T], ldb: Int,
	                     beta: T,
	                     C: DRAM2[T], ldc: Int)(implicit state: State): Unit = {
	  Foreach(M.value by 64/*tileSizeM*/ par 1/*m_outer_par*/){i =>
	    // Compute leftover dim
	    val elements_m = min(lift(64)/*tileSizeM*/, M.value - i)
	    Foreach(N.value by 64/*tileSizeN*/ par 1/*n_outer_par*/){j =>
	      // Compute leftover dim
	      val elements_n = min(lift(64)/*tileSizeN*/, N.value - j)
	      // Create C tile for accumulating
	      val c_tile = SRAM[T](64/*tileSizeM*/, 64/*tileSizeN*/)
	      MemReduce(c_tile par 1/*c_reduce_par*/)(K.value by 64/*tileSizeK*/ par 1/*k_outer_par*/){l =>
	        // Create local C tile
	        val c_tile_local = SRAM[T](64/*tileSizeM*/, 64/*tileSizeN*/)
	        // Compute leftover dim
	        val elements_k = min(lift(64)/*tileSizeK*/, K.value - l)
	        // Generate A and B tiles
	        val a_tile = SRAM[T](64/*tileSizeM*/, 64/*tileSizeK*/)
	        val b_tile = SRAM[T](64/*tileSizeK*/, 64/*tileSizeN*/)
	        // Transfer tiles to sram
	        Parallel{
	          a_tile load A(i::i+elements_m, l::l+elements_k par 1/*load_par*/) 
	          b_tile load B(l::l+elements_k, j::j+elements_n par 1/*load_par*/) 
	        }
	        Foreach(elements_m by 1 par 1/*m_inner_par*/){ii => 
	          Foreach(elements_n by 1 par 1/*n_inner_par*/){jj => 
	            c_tile_local(ii,jj) = Reduce(Reg[T])(elements_k by 1 par 1/*k_inner_par*/){ll => 
	              a_tile(ii,ll) * b_tile(ll,jj)
	            }{_+_}
	          }
	        }
	        c_tile_local
	      }{_+_}
	      C(i::i+elements_m, j::j+elements_n par 1/*store_par*/) store c_tile
	    }
	  }
	}

	@virtualize
	def Gemv[T<:MetaAny[T]:Type:Num](M: Reg[Int], N: Reg[Int],
	                     alpha: T, 
	                     A: DRAM2[T], lda: Int,
	                     X: DRAM1[T], incX: Int,
	                     beta: T,
	                     Y: DRAM1[T], incY: Int)(implicit state: State): Unit = {
	  Foreach(M.value by 64/*tileSizeM*/ par 1/*m_outer_par*/){i =>
	    // Compute leftover dim
	    val elements_m = min(lift(64)/*tileSizeM*/, M.value - i)
	    // Create Y tile
	    val y_tile = SRAM[T](64/*tileSizeM*/)
	    MemReduce(y_tile par 1/*y_reduce_par*/)(N.value by 64/*tileSizeN*/ par 1/*n_outer_par*/){j =>
	      // Compute leftover dim
	      val elements_n = min(lift(64)/*tileSizeN*/, N.value - j)
	      // Create local Y tile for accumulating
	      val y_tile_local = SRAM[T](64/*tileSizeM*/)
	      // Create X tile
	      val x_tile = SRAM[T](64/*tileSizeN*/)
	      // Load vector tile
	      x_tile load X(j::j+elements_n par 1/*load_par*/)
	      // Create A tile
	      val a_tile = SRAM[T](64/*tileSizeM*/, 64/*tileSizeN*/)
	      // Load matrix tile
	      a_tile load A(i::i+elements_m, j::j+elements_n par 1/*load_par*/)
	      Foreach(elements_m by 1 par 1/*m_inner_par*/){ii => 
	        y_tile_local(ii) = Reduce(Reg[T])(elements_n by 1 par 1/*n_inner_par*/){jj => 
	          a_tile(ii,jj) * x_tile(jj)
	        }{_+_}
	      }
	      y_tile_local
	    }{_+_}
	    Y(i::i+elements_m par 1/*store_par*/) store y_tile
	  }
	}

	@virtualize
	def Ger[T<:MetaAny[T]:Type:Num](M: Reg[Int], N: Reg[Int],
	                     alpha: T, 
	                     X: DRAM1[T], incX: Int,
	                     Y: DRAM1[T], incY: Int,
	                     A: DRAM2[T], lda: Int)(implicit state: State): Unit = {
	  Foreach(M.value by 16/*tileSizeM*/ par 1/*m_outer_par*/){i =>
	    // Compute leftover dim
	    val elements_m = min(lift(16)/*tileSizeM*/, M.value - i)
	    // Create X tile
	    val x_tile = SRAM[T](16/*tileSizeM*/)
	    // Load x data into tile
	    x_tile load X(i::i+elements_m)
	    Foreach(N.value by 16/*tileSizeN*/ par 1/*n_outer_par*/){j => 
	      // Compute leftover dim
	      val elements_n = min(lift(16)/*tileSizeN*/, N.value - j)
	      // Create Y and A tiles
	      val y_tile = SRAM[T](16/*tileSizeN*/)
	      val a_tile = SRAM[T](16/*tileSizeM*/, 16/*tileSizeN*/)
	      // Load x data into tile
	      y_tile load Y(j::j+elements_n)
	      Foreach(elements_m by 1 par 1/*m_inner_par*/){ii =>
	        Foreach(elements_n by 1 par 1/*n_inner_par*/){jj =>
	          a_tile(ii,jj) = x_tile(ii) * y_tile(jj)
	        }
	      }
	      A(i::i+elements_m, j::j+elements_n par 1/*store_par*/) store a_tile
	    }
	  }
    }

	@virtualize
	def Scal[T<:MetaAny[T]:Type:Num](N: Reg[Int], alpha: T, 
	                     X: DRAM1[T], incX: Int,
	                     Y: DRAM1[T])(implicit state: State): Unit = {
	  // Loop over whole vectors
	  Foreach(N.value by 64/*tileSize*/ par 1 /*outer_par*/){i => 
	    // Compute elements left in this tile
	    val elements = min(lift(64)/*tileSize*/, N.value - i)
	    // Create onchip structures
	    val x_tile = SRAM[T](64/*tileSize*/)
	    val y_tile = SRAM[T](64/*tileSize*/)
	    // Load local tiles
	    x_tile load X(i::i+elements par 1 /*load_par*/)
	    // Loop over elements in local tiles
	    Foreach(elements by 1 par 1 /*inner_par*/){j => 
	      y_tile(j) = alpha * x_tile(j)
	    }
	    // Store tile to DRAM
	    Y(i::i+elements par 1 /*store_par*/) store y_tile
	  }
	}

  @virtualize
  def Axpby[T<:MetaAny[T]:Type:Num](N: Reg[Int],
                       alpha: T, 
                       X: DRAM1[T], incX: Int,
                       beta: T,
                       Y: DRAM1[T], incY: Int,
                       Z: DRAM1[T])(implicit state: State): Unit = {
    Scal[T](N, beta, Y, incY, Z)
    Axpy[T](N, alpha, X, incX, Z, incY, Z)
  }

}