4. Controller Templates
=======================


Accel Block
-----------
``Accel`` blocks scope out any code that will run on the hardware accelerator.
This is where you should specify the dataflow-heavy part of the algorithm using a hierarchy of control nodes and primitive operations.

Control Nodes
-------------
A control node is essentially some variety of a loop, with extra semantic information for the compiler.
As with imperative *for* loops, control nodes scope out a section of code that runs for some given number of iterations.

It is best to think of the `Accel` block as a hierarchy of control nodes, where each control node contains either a
list of other control nodes, or a list of primitive operations.

The control nodes currently available are:

**Foreach**

The *Foreach* controller is similar to a *for* loop. Significantly, however, unless explicitly told otherwise, the compiler
will assume each iteration of *Foreach* is independent, and will attempt to parallelize and pipeline the body.
*Foreach* has no usable return value.

A *Foreach* can be created using::

    Foreach(0 until N){i =>
        // Some operations or other controllers
    }

Here, the variable ``i`` represents the iterator, which will take on values 0 through N as the counter progresses.

We can also make a 2 dimensional loop::

    Foreach(0 until M, 0 until N){(i,j) =>
        // Some other operations
    }

This code says that we are iterating over a two dimensional, *M* x *N* space, where *M* is the size of the outer dimension,
and *N* is the size of the inner dimension. In the loop, ``i`` will take values 0 through M and ``j`` will take values 0 through N (M times).


**Reduce**

The *Reduce* node takes many scalar values and combines them into one value using some associative operator.
Like *Foreach*, unless otherwise disabled, the compiler will attempt to automatically pipeline and parallelize *Reduce* nodes.
A *Reduce* node consists of a *map* function, which is responsible for producing the values that will be used in the reduction, and
a *reduction* function to describe how the values should be combined.

*Reduce* returns the accumulator which, at the end of the loop, will contain the final result of the reduction.

For example, suppose we want to add up all of the elements in a local :doc:`../accel/memories/sram` scratchpad.
In this case, the *map* function should tell the hardware to load an element from the memory and the *reduction* function
tells the hardware to perform an addition to combine elements::

    val data = SRAM[Int](N)
    val result = Reduce(0 until N){i =>
        data(i)
    }{ (x,y) =>
        x + y
    }

In this example, ``i`` is again the loop iterator, which will take values 0 through N.  The ``x`` and ``y`` here don't represent
any specific values. Instead, they represent any two values which are produced from the *map*. Spatial supports this syntax because,
unlike imperative updates, this functional representation allows the compiler to easily identify, analyze, and duplicate the reduction function.

*Reduce* can also take an explicit accumulator::

    val data = SRAM[Int](N)
    val result = Reg[Int]
    Reduce(result)(0 until N){i => ...

Or an explicit identity value (the value which does not change the output when combined using the reduction function)::

    val data = SRAM[Int](N)
    val result = Reduce(0)(0 until N){i => ...

Both of these have the same behavior as the previous example. In some cases, supplying an explicit identity value
can decrease the size of the generated logic slightly.


**Fold**

*Fold* is similar to *Reduce*, but it takes an initial value rather than an identity value. This initial value is
combined with the rest of the produced values. Alternatively, if *Fold* is given an explicit accumulator, the
current value of the accumulator will be combined when calculating the final result.

The same example as above, now using *Fold* with an initial value::

    val data = SRAM[Int](N)
    val result = Fold(10)(0 until N){i =>
        data(i)
    }{ (x,y) =>
        x + y
    }

This will produce 10 + (the sum of the contents of ``data``).

With an explicit accumulator::

    val data = SRAM[Int](N)
    val accum = Reg[Int]
    accum := 10

    Fold(accum)(0 until N){i =>
        data(i)
    }{ (x,y) =>
        x + y
    }

Again, this will produce 10 + (the sum of the contents of ``data``), since ``accum`` holds 10 when the *Fold* begins.


**MemReduce**

Also occasionally referred to as "Block Reduce", *MemReduce* describes the reduction *across* multiple local memories.
Like *Reduce*, *MemReduce* requires both a *map* and a *reduction* function. However, in *MemReduce*, the *map*
describes the creation and population of a local memory (typically an :doc:`../accel/memories/sram`).
The *reduction* function still operates on scalars, and is used to combine local memories together element-wise.
Unlike *Reduce*, *MemReduce* always requires an explicit accumulator.
Unless otherwise disabled, the compiler will then try to parallelize both the creation of this memory and the reduction
of each of these memories into a single accumulator.


Let's look at an example where we use *MemReduce* to combine *M* sequences of the numbers 0 ... *N*-1::

    val accum = SRAM[Int](N)
    MemReduce(accum)(0 until M){i =>
        val sequence = SRAM[Int](N)
        Foreach(0 until M){j => sequence(j) = j }
        sequence
    }{(x,y) =>
        x + y
    }

Clearly this is a silly example, since we could have computed the final result of ``accum`` without all this effort.
However, there are plenty of algorithms (e.g. gradient descent) which have accumulation of identical N-dimensional arrays.

**Parallel**

Unlike the other control nodes, *Parallel* does not specify a loop, but simply tells the compiler to schedule
any inner control nodes in a fork-join manner.

For instance::

    Parallel {
        Foreach(0 until N){ i => ... } // Loop #1
        Foreach(0 until M){ j => ... } // Loop #2
    }

In this example, Loop #1 and Loop #2 will be run at the same time, and the *Parallel* controller will complete
only when both are done.

Parallel will be soon be deprecated for general use as the scheduling algorithms in the Spatial compiler improve.


Control Tags
------------

While the oracle compiler is a nice dream, it can often be difficult for a compiler to discover ALL relevant information
about a program. Spatial offers a few annotation tags when creating controllers to allow users to specify how
a controller's inner body should be scheduled.

Controller tags are specified using prefix syntax, e.g. ``<Tag>.<Controller>``.


**Pipe**

*Pipe* is the default tag for controllers, and doesn't usually need to be specified. This tag tells the compiler that
the stages of the controller can be overlapped in a pipelined fashion. If the controller contains other controllers within it,
this means that these inner controllers will be executed using coarse-grained pipeline scheduling.
Additionally, *Pipe* tells the compiler it may attempt to parallelize the loop by unrolling it in space.

When multiple stages communicate through an :doc:`../accel/memories/sram` in a *Pipe* controller, the compiler will
automatically buffer and bank memories as necessary to maximize throughput.

For example, suppose a *Foreach* contains two stages::

    Pipe.Foreach(0 until N){i =>
        Foreach(0 until N){ ... } // Stage 0
        Foreach(0 until M){ ... } // Stage 1
    }


When executing, Stage 0 will execute with the first value of the counter.
When Stage 0 finishes, the counter for the control node will increment and Stage 0 will then begin executing again with this new counter value.
At the same time, it will pass its old counter value to Stage 1, which will begin to execute its first iteration.


**Sequential**

The *Sequential* tag tells the compiler not to attempt to parallelize or to pipeline inner computation. In this
scheduling mode, the controller's counter will only increment when it's last stage is complete.
This tag is needed primarily when your algorithm contains long loop-carry dependencies that cannot be optimized away.

.. code-block:: scala

    Sequential.Foreach(0 until N){i =>
        Foreach(0 until N){ ... } // Stage 0
        Foreach(0 until M){ ... } // Stage 1
    }




**Stream**

The *Stream* tag tells the compiler to overlap inner computation in a fine-grained, streaming fashion. In controllers
which contain multiple control stages, this implies that communication is being done through :doc:`FIFOs <../accel/memories/fifo>`
at an element-wise level.

Communication across stages within *Stream* controllers through any memory except FIFOs is currently disallowed.
Note that this may change as the language evolves.

.. code-block:: scala

    Stream.Foreach(0 until N){i =>
        val fifo = FIFO[Int](32)
        Foreach(0 until N){ i => fifo.enq(i) }    // Stage 0
        Foreach(0 until M){ j => fifo.deq() ... } // Stage 1
    }



Now let's :doc:`look at the specialized memory transfer templates in Spatial <transfers>`