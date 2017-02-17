3. Memory Templates
===================

On-Chip Memories
----------------

**Reg**
A *Reg* is an array of flip-flops. These are generally simple scalars, but can be anything representable by a static number
of bits (e.g. vectors, structs of scalars).

.. code-block:: scala

    val reg = Reg[Int](0)        // Declare a register which holds a 32b integer and has reset value 0

    reg := 5                     // Store 5 to the register

    reg.value                    // Get the value of the register


**SRAM**
On FPGAs, *SRAM*\s refer to the block RAMs on the device. You can think of them as scratchpad memories that take one cycle to read and write.

.. code-block:: scala

    val sram = SRAM[Int](32, 48) // Declare an SRAM which contains space for 32 x 48 integer values

    sram(0, 0) = 32              // Create a write port where the value 32 is stored to address (0,0)

    sram(0, 0)                   // Create a read port which loads the constant address (0,0)


**FIFO**
*FIFO*\s are SRAMs behind the scenes, but rather than being word-addressable, present a simple *enqueue*/*dequeue* interface.

.. code-block:: scala

    val fifo = FIFO[Int](32)     // Declare a FIFO of maximum depth 32 integers

    fifo.enq(32)                 // Put the value 32 into the FIFO

    fifo.deq()                   // Remove and return the top value from the FIFO


**Counter**
*Counters* provide state for iterating over sets of data. They are generally created with Scala Range - like syntax sugar::

    val ctr = 0 until 32         // Creates a counter from [0, 32)


**Forever**
*Forever* is a special kind of counter which runs, as the name suggests, forever. It's particularly useful in streaming applications
where incoming data has no real start or end. *Forever* counters are created simply with the ``*`` token.


**CounterChain**
*CounterChains* couple multiple counters together to create nested loops. They generally don't need to be created
explicitly, as Spatial has syntax sugar for doing this for you. When necessary, they can be created using::

    val cchain = CounterChain(ctr1, ctr2, ...)


Off-Chip Memories
-----------------

**ArgIn** and **ArgOut**
*ArgIn* and *ArgOut* are special types of registers used for communicating scalars to and from the accelerator, respectively

.. code-block:: scala

    val input = ArgIn[Int]       // Declare an integer argument input to the accelerator
    val output = ArgOut[Float]   // Declare a floating point output from the accelerator

ArgIn and ArgOut require special methods on the host-side (e.g. outside the ``Accel`` scope)::

    setArg(input, 32)            // Passes the integer 32 as an argument to the accelerator prior to running

    val out = getArg(output)     // Reads the scalar returned by the accelerator after running



**DRAM**
DRAM refers to memory that is not on-board the accelerator.  Data is generally put into these memories by the CPU and then fetched by the accelerator.
The accelerator can fetch memory from DRAM at a target-specific burst-granularity (e.g. 64 bytes = 16 4-byte words)

.. code-block:: scala

    val dram = DRAM[Int](1024, 1024) // Declares a region of DRAM with room for (at least) 1024 x 1024 integer values




Next, let's :doc:`look at the control templates Spatial offers <controllers>`