
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


**FIFOs** (first-in, first-out) are on-chip scratchpads with additional control logic for address-less enqueue/dequeue operations.
FIFOs preserve the ordering between elements as they are enqueued. A FIFO's **deq** operation always returns the oldest
**enqueued** element which has not yet been dequeued.

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
| |               def   **empty**\(): :doc:`../../common/boolean`                                                                            |
| |                       True when the FIFO contains no elements, false otherwise                                                           |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **full**\(): :doc:`../../common/boolean`                                                                             |
| |                       True when the FIFO cannot fit any more elements, false otherwise                                                   |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **almostFull**\(): :doc:`../../common/boolean`                                                                       |
| |                       True when the FIFO can fit exactly one more element before being full                                              |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **almostEmpty**\(): :doc:`../../common/boolean`                                                                      |
| |                       True when the FIFO contains exactly one element                                                                    |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **numel**\(): :doc:`Int <../../common/fixpt>`                                                                        |
| |                       Returns the number of elements currently in the FIFO                                                               |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **deq**\(): T                                                                                                        |
| |                       Creates a dequeue (destructive read) port to this FIFO                                                             |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **deq**\(en: :doc:`../../common/boolean`): T                                                                         |
| |                       Creates a dequeue (read) port to this FIFO with data-dependent enable **en**                                       |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **enq**\(data: T): Unit                                                                                              |
| |                       Creates an enqueue (write) port to this FIFO which is always enabled with its parent controller                    |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **enq**\(data: T, en: :doc:`../../common/boolean`): Unit                                                             |
| |                       Creates an enqueue (write) port to this FIFO with data-dependent enable **en**                                     |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **load**\(tile: :doc:`tile`\[T\]): Unit                                                                              |
| |                       Creates a burst load of a :doc:`tile` of a :doc:`dram` memory to this :doc:`fifo`.                                 |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
