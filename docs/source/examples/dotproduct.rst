
1. Vector Inner Product
=======================

Inner product (also called dot product) is an extremely simple linear algebra kernel, defined as the
sum of the element-wise products between two vectors of data. For this example, we'll assume that the
data in this case are scalar Floats. You could, however, also do the same operations with custom struct types.

Let's look at how to write it in Spatial. Let's start with the application's template. We'll first create a method which
takes two Arrays, `a` and `b`::

    import spatial._
    import org.virtualized._

    object DotProduct extends SpatialApp {

        /**
         * Computes the dot product of two arrays on a hardware accelerator.
         * Arrays a and b should have the same length.
         */
        @virtualize def dotproduct(a: Array[Float], b: Array[Float]): Float = {
            assert(a.length == b.length)
            ???
        }

        /**
         * Entry point for our program. Host CPU starts here.
         */
        @virtualize def main(): Unit = {
            ???
        }
    }

Accelerator Code
----------------
Let's look at the hardware side first. We'll define this in the `dotproduct` method.

We first need some global memory to hold our vectors. This memory needs to be big enough to hold the arrays. Since
the size of the arrays is only known at runtime, we'll need an ArgIn to pass this to the accelerator::

    @virtualize def dotproduct(a: Array[Float], b: Array[Float]): Float = {
        ...
        val len = a.length
        val N = ArgIn[Int]
        setArg(N, len)

        val dramA = DRAM[Int](N)
        val dramB = DRAM[Int](N)
        setMem(dramA, a)
        setMem(dramB, a)
        ...
    }

We'll also need an ArgOut to pass the result of the dot product back to our CPU host::

    @virtualize def dotproduct(a: Array[Float], b: Array[Float]): Float = {
        ...
        val output = ArgOut[Float]
        ...
    }

So far so good. Now we need to compute the dot product of those vectors on the accelerator. To do this,
we need to tile the operation such that we're operating on fixed size chunks of data at a time. Dot product is
really simple to tile - we can split the vectors into smaller B-sized vectors, and compute the dot product of each
B chunk. Then we can add up all the dot products across all of those vectors.

In order to do a B-sized dot product, we first need to load some data onto the accelerator::

    @virtualize def dotproduct(a: Array[Float], b: Array[Float]): Float = {
        ...
        val B = 256
        Accel {
            val result = Reg[Float](0.0f)
            Reduce(result)(N by B){i =>
                val sramA = SRAM[Float](B)
                val sramB = SRAM[Float](B)
                sramA load dramA(i::i+B)
                sramB load dramB(i::i+B)

                ... // Inner dot product goes here!

            }{(x,y) => x + y }
            ...
        }
    }

We're using Reduce here because we know the outer loop is a parallelizable reduction. In this Reduce, `i` will have the values
`0, 256, 512, ...`. Using this iterator, we will load our B-sized chunks from dramA into sramA and dramB into sramB
using the range `i::i+B`, which will correspond to address ranges `[0, 255]`, `[256, 511]`, `[512, 767]` and so on.

Now that we have tiles of data, let's compute a small dot product::

    @virtualize def dotproduct(a: Array[Float], b: Array[Float]): Float = {
        ...
                // Inner dot product
                Reduce(0.0f)(B by 1){j =>
                    sramA(j) * sramB(j)
                }{(x,y) => x + y }
        ...
    }

Here, we're saying we're doing an element-wise multiplication between sramA and sramB, and adding up all of the elements.

Now let's see the entire function, adding the code to return the result back to the host::

    @virtualize def dotproduct(a: Array[Float], b: Array[Float]): Float = {
        assert(a.length == b.length)
        val len = a.length
        val N = ArgIn[Int]
        setArg(N, len)

        val dramA = DRAM[Int](N)
        val dramB = DRAM[Int](N)
        setMem(dramA, a)
        setMem(dramB, a)

        Accel {
            val result = Reg[Float](0.0f)
            Reduce(result)(N by B){i =>
                val sramA = SRAM[Float](B)
                val sramB = SRAM[Float](B)
                sramA load dramA(i::i+B)
                sramB load dramB(i::i+B)

                Reduce(0.0f)(B by 1){j =>
                    sramA(j) * sramB(j)
                }{(x,y) => x + y }
            }{(x,y) => x + y }
            output := result   // Write to a register the host can read
        }
        getArg(output)  // Read the output register on the host side
    }

It might seem a bit odd at first that we have the line `{(x,y) => x + y }` twice. Isn't this redundant?
The duplication of this line comes from the fact that we've tiled our computation. The first `x + y` tells us
how to combine any two elements produced by the inner dot product, while the second one tells us how to combine
results from multiple inner dot products. It just so happens that, since this is a tiled Reduce, the combine
function for the two Reduce loops is the same.


So far in this example, we assumed that B (our chunk size) evenly divides the vector size (N). What if this isn't the case?
If B doesn't divide N, we have an edge case where the remaining number of elements to be operated on is less than B.
The size of the current tile we actually want to compute on then is actually `T = min(B, N - B)`. Let's factor that in::

    @virtualize def dotproduct(a: Array[Float], b: Array[Float]): Float = {
        assert(a.length == b.length)
        val len = a.length
        val N = ArgIn[Int]
        setArg(N, len)

        val dramA = DRAM[Int](N)
        val dramB = DRAM[Int](N)
        setMem(dramA, a)
        setMem(dramB, a)

        Accel {
            val result = Reg[Float](0.0f)
            Reduce(result)(N by B){i =>
                val sramA = SRAM[Float](B)
                val sramB = SRAM[Float](B)
                val T = min(B, N - B)     // Edge case handling
                sramA load dramA(i::i+T)  // Now loads T elements
                sramB load dramB(i::i+T)  // Now loads T elements

                Reduce(0.0f)(T by 1){j =>   // Now iterates over T
                    sramA(j) * sramB(j)
                }{(x,y) => x + y }
            }{(x,y) => x + y }
            output := result   // Write to a register the host can read
        }
        getArg(output)  // Read the output register on the host side
    }

Host Code
---------
To call our accelerator, all we need now are some arrays to operate on. Let's just load these from some files called
"vectorA.csv" and "vectorB.csv"::

    @virtualize def main(): Unit = {
        val a = loadCSV1D[Float]("vectorA.csv")
        val b = loadCSV1D[Float]("vectorB.csv")
        val prod = dotproduct(a, b)

        println("Product of A and B: " + prod)
    }

That's all for this example!


Final Code
----------
::

    import spatial._
    import org.virtualized._

    object DotProduct extends SpatialApp {

        /**
         * Computes the dot product of two arrays on a hardware accelerator.
         * Arrays a and b should have the same length.
         */
        @virtualize def dotproduct(a: Array[Float], b: Array[Float]): Float = {
            assert(a.length == b.length)
            val len = a.length
            val N = ArgIn[Int]
            setArg(N, len)

            val dramA = DRAM[Int](N)
            val dramB = DRAM[Int](N)
            setMem(dramA, a)
            setMem(dramB, a)

            Accel {
                val result = Reg[Float](0.0f)
                Reduce(result)(N by B){i =>
                    val sramA = SRAM[Float](B)
                    val sramB = SRAM[Float](B)
                    val T = min(B, N - B)
                    sramA load dramA(i::i+T)
                    sramB load dramB(i::i+T)

                    Reduce(0.0f)(T by 1){j =>
                        sramA(j) * sramB(j)
                    }{(x,y) => x + y }
                }{(x,y) => x + y }
                output := result   // Write to a register the host can read
            }
            getArg(output)  // Read the output register on the host side
        }

        /**
         * Entry point for our program. Host CPU starts here.
         */
        @virtualize def main(): Unit = {
            val a = loadCSV[Float]("vectorA.csv")
            val b = loadCSV[Float]("vectorB.csv")
            val prod = dotproduct(a, b)

            println("Product of A and B: " + prod)
        }
    }

Next example: :doc:`outerproduct`