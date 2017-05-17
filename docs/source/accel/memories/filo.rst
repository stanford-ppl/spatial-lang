
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

.. _FILO:

FILO
====


**FILOs** (first-in, last-out) are on-chip scratchpads with additional control logic for address-less enqueue/dequeue operations.
FILOs acts as a Stack, reversing the order of elements it receives. A FILO's **pop** operation always returns the most
recently **pushed** element.

---------------

**Static methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `object`         **FILO**                                                                                                             |
+=====================+======================================================================================================================+
| |               def   **apply**\[T::doc:`../../typeclasses/bits`\](depth: :doc:`Int <../../common/fixpt>`): :doc:`fifo`\[T\]               |
| |                       Creates a FILO with given **depth**. **Depth** must be a statically determinable signed integer.                   |
+---------------------+----------------------------------------------------------------------------------------------------------------------+


--------------

**Infix methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `object`         **FILO**\[T\]                                                                                                        |
+=====================+======================================================================================================================+
| |               def   **empty**\(): :doc:`../../common/boolean`                                                                            |
| |                       True when the FILO contains no elements, false otherwise                                                           |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **full**\(): :doc:`../../common/boolean`                                                                             |
| |                       True when the FILO cannot fit any more elements, false otherwise                                                   |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **almostFull**\(): :doc:`../../common/boolean`                                                                       |
| |                       True when the FILO can fit exactly one more element before being full                                              |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **almostEmpty**\(): :doc:`../../common/boolean`                                                                      |
| |                       True when the FILO contains exactly one element                                                                    |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **numel**\(): :doc:`Int <../../common/fixpt>`                                                                        |
| |                       Returns the number of elements currently in the FILO                                                               |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **pop**\(): T                                                                                                        |
| |                       Creates a pop (destructive read) port to this FILO                                                                 |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **pop**\(en: :doc:`../../common/boolean`): T                                                                         |
| |                       Creates a pop (destructive read) port to this FILO with data-dependent enable **en**                               |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **push**\(data: T): Unit                                                                                             |
| |                       Creates a push (write) port to this FILO which is always enabled with its parent controller                        |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **push**\(data: T, en: :doc:`../../common/boolean`): Unit                                                            |
| |                       Creates a push (write) port to this FILO with data-dependent enable **en**                                         |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **load**\(tile: :doc:`tile`\[T\]): Unit                                                                              |
| |                       Creates a burst load of a :doc:`tile` of a :doc:`dram` memory to this :doc:`fifo`.                                 |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
