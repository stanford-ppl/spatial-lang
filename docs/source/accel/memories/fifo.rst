
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

.. _FIFO:

FIFO
====


**FIFOs** are on-chip scratchpads with additional control logic for addressless enqueue/dequeue operations.

---------------

**Static methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `object`         **FIFO**                                                                                                             |
+=====================+======================================================================================================================+
| |               def   **apply**\[T::doc:`../../typeclasses/bits`\](depth: :doc:`Int <../../common/fixpt>`): :doc:`fifo`\[T\]               |
| |                       Creates a FIFO with given **depth**. **Depth** must be a statically determinable signed integer.                   |
+---------------------+----------------------------------------------------------------------------------------------------------------------+


--------------

**Infix methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `object`         **FIFO**\[T\]                                                                                                        |
+=====================+======================================================================================================================+
| |               def   **deq**\(): T                                                                                                        |
| |                       Creates a dequeue (read) port to this FIFO which is always enabled with its parent controller                      |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **deq**\(en: :doc:`../../common/boolean`): T                                                                         |
| |                       Creates a dequeue (read) port to this FIFO with data-dependent enable **en**                                       |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **enq**\(data: T): Unit                                                                                              |
| |                       Creates a enqueue (write) port to this FIFO which is always enabled with its parent controller                     |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **enq**\(data: T, en: :doc:`../../common/boolean`): Unit                                                             |
| |                       Creates a enqueue (write) port to this FIFO with data-dependent enable **en**                                      |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **load**\(tile: :doc:`tile`\[T\]): Unit                                                                              |
| |                       Creates a burst load of a :doc:`tile` of a :doc:`dram` memory to this :doc:`fifo`.                                 |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
