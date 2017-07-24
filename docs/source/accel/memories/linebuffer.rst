
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

.. _LineBuffer:

LineBuffer
==========


**LineBuffers** are two dimensional, on-chip scratchpads with a fixed size.
LineBuffers act as a FIFO on input, supporting only queued writes, but support addressed reading like SRAMs.
For writes, the current row buffer and column is maintained using an internal counter.
This counter resets every time the controller containing the enqueue completes execution.

The contents of LineBuffers are persistent across loop iterations, even when they are declared in an inner scope.
Up to 5-dimensional LineBuffers are currently supported.


---------------

**Static methods**

+---------------------+---------------------------------------------------------------------------------------------------------------------------------------------------------+
|      `object`         **LineBuffer**                                                                                                                                          |
+=====================+=========================================================================================================================================================+
| |               def   **apply**\[T::doc:`../../typeclasses/bits`\](rows: :doc:`Int <../../common/fixpt>`, columns: :doc:`Int <../../common/fixpt>`\): :doc:`linebuffer`\[T\]  |
| |                       Creates a LineBuffer with given **rows** and **columns**.                                                                                             |
| |                       **rows** and **columns** must be statically determinable signed integers.                                                                             |
+---------------------+---------------------------------------------------------------------------------------------------------------------------------------------------------+


--------------

**Infix methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `class`         **LineBuffer**\[T\]                                                                                                   |
+=====================+======================================================================================================================+
| |               def   **apply**\(row: :doc:`Int <../../common/fixpt>`, column: :doc:`Int <../../common/fixpt>`): T                         |
| |                       Creates a load port to this LineBuffer at the given 2-dimensional address.                                         |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **enq**\(data: T)                                                                                                    |
| |                       Creates an enqueue (write) port to this LineBuffer, writing **data** to the current column                         |
| |                       in the current receiving buffer                                                                                    |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **enq**\(data: T, en: :doc:`../../common/boolean`)                                                                   |
| |                       Creates an enqueue (write) port to this LineBuffer with data-dependent enable **en**                               |
| |                       writing **data** to the current column in the current receiving buffer                                             |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **load**\(tile: :doc:`tile`\[T\]): Unit                                                                              |
| |                       Creates a burst load of a :doc:`tile` of a :doc:`dram` memory to this local memory.                                |
| |                       Only 1-dimensional DRAM tiles are currently supported when loading to a LineBuffer                                 |
+---------------------+----------------------------------------------------------------------------------------------------------------------+

