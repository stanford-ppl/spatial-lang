
.. role:: black
.. role:: gray
.. role:: silver
.. role:: white
.. role:: maroon
.. role:: red
.. role:: fuchsia
.. role:: pink
.. role:: orange
.. role:: yellow
.. role:: lime
.. role:: green
.. role:: olive
.. role:: teal
.. role:: cyan
.. role:: aqua
.. role:: blue
.. role:: navy
.. role:: purple

.. _Transfer:

Transfer Operations
===================

These operations are used to transfer scalar values and arrays between the CPU host and the hardware accelerator.
They must be specified explicitly in the host code (not in Accel scopes).


**Methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **getArg**\[T::doc:`../typeclasses/bits`\](reg: :doc:`../accel/memories/reg`\[T\]): T                                |
| |                       Transfer a scalar ArgOut value from the accelerator to the host                                                    |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **getMem**\[T::doc:`../typeclasses/bits`\](dram: :doc:`../accel/memories/dram`\[T\]): :doc:`array`\[T\]              |
| |                       Copy a block of data from the accelerator's main memory into an Array on the host                                  |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **setArg**\[T::doc:`../typeclasses/bits`\](reg: :doc:`../accel/memories/reg`\[T\], value: T): Unit                   |
| |                       Transfer a scalar value from the host to the accelerator                                                           |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **setMem**\[T::doc:`../typeclasses/bits`\](dram: :doc:`../accel/memories/dram`\[T\], data: :doc:`array`\[T\]): Unit  |
| |                       Copy a block of data from a host Array to the accelerator's main memory                                            |
+---------------------+----------------------------------------------------------------------------------------------------------------------+

