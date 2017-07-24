3. Matrix Multiplication
========================

Now that we've shown examples of using Foreach and Reduce, let's next look at everyone's favorite linear algebra kernel:
GEMM ("GEneral Matrix Multiplication"), i.e. `C += A * B`, where `A`, `B`, and `C` are all matrices.

As with dot product and outer product, GEMM will need to be tiled to fit on the accelerator. There are many different
loop orderings possible with matrix multiplication. For simplicity, we'll just look at one of them here.
We also assume here that our accelerator, like most FPGA boards, has two levels of memory hierarchy: on-chip SRAM and off-chip DRAM.


Accelerator Code
----------------
Let's look at the template for our hardware code::

    /**
     * Computes cOut = c + a * b, where a is a matrix with dimensions M x P,
     * b is a matrix with dimensions P x N, and c is a matrix with
     * dimensions M x N.
     */
    @virtualize def gemm(A: Matrix[Float], B: Matrix[Float], C: Matrix[Float]): Matrix[Float] = {
        assert(A.cols == B.rows)
        assert(C.rows == A.rows)
        assert(C.cols == B.cols)
        ...
    }

We'll first need DRAM for all three matrices. We can use the same DRAM for `C` and the result::

    @virtualize def gemm(a: Matrix[Float], b: Matrix[Float], c: Matrix[Float]): Matrix[Float] = {
        ...
        val M = ArgIn[Int]
        val P = ArgIn[Int]
        val N = ArgIn[Int]
        setArg(M, A.rows)
        setArg(P, A.cols)
        setArg(N, B.cols)

        val dramA = DRAM[Float](M, P)
        val dramB = DRAM[Float](P, N)
        val dramC = DRAM[Float](M, N)
        setMem(dramA, A)
        setMem(dramB, B)
        setMem(dramC, C)
        ...
    }

This will set the initial value for the output and store `A` and `B` to main accelerator memory.

In this example, we'd like to compute the entire increment to matrix `C` using smaller matrix multiplies, expressed as
sums of outer products. The size of each of the dimensions of the smaller matrix multiply will be `BM`, `BP`, and `BN`,
the tile sizes for `M`, `P`, and `N`, respectively.

In general, matrix multiplication is `O(M*N*P)` compute, but can be tiled such that one matrix (either `A`, `B`, or `C`)
is read in its entirety only once, while the others are read more. Let's look at an example where we tile to minimize
reads of the output, `C`::

    @virtualize def gemm(a: Matrix[Float], b: Matrix[Float], c: Matrix[Float]): Matrix[Float] = {
        ...
        val BM = 64
        val BP = 128
        val BN = 128
        Accel {
            Foreach(M by BM, N by BN){(i,j) =>
                val tileC = SRAM[Float](BM, BN)
                tileC load dramC(i::i+BM, j::j+BN)

                ... // Compute update to C

                dramC(i::i+BM, j::j+BN) store tileC
            }
        }
    }

Notice here that we load a part of `C`, update it completely, then store it back.

While we could use nested Foreach loops to express imperative updates to `tileC`, that would limit the amount of
implicit parallelism captured by the program. Let's instead write look at how to update `tileC` and ::

    @virtualize def gemm(a: Matrix[Float], b: Matrix[Float], c: Matrix[Float]): Matrix[Float] = {
        ...
                MemFold(tileC)(P by BP){k =>

                }{(x,y) => x + y }
        ...
    }
