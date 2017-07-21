
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

.. _SRAM:

SRAM
====


**SRAMs** are on-chip scratchpads with fixed size. SRAMs can be specified as multi-dimensional, but the underlying addressing
in hardware is always flat. The contents of SRAMs are persistent across loop iterations, even when they are declared in an inner scope.
Up to 5-dimensional SRAMs are currently supported.


---------------

**Static methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `object`         **SRAM**                                                                                                             |
+=====================+======================================================================================================================+
| |               def   **apply**\[T::doc:`../../typeclasses/bits`\](dimensions: :doc:`Int <../../common/fixpt>`\*): :doc:`sram`\[T\]        |
| |                       Creates a N-dimensional with given **dimensions**.                                                                 |
| |                       **Dimensions** must be statically determinable signed integers.                                                    |
+---------------------+----------------------------------------------------------------------------------------------------------------------+


--------------

**Infix methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `class`         **SRAM**\[T\]                                                                                                         |
+=====================+======================================================================================================================+
| |               def   **apply**\(address: :doc:`Int <../../common/fixpt>`\*): T                                                            |
| |                       Creates a load port to this SRAM at the given multi-dimensional address.                                           |
| |                       The number of indices should match the number of dimensions the SRAM was declared with.                            |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **update**\(address: :doc:`Int <../../common/fixpt>`\*, data: T): Unit                                               |
| |                       Creates a store port to this SRAM, writing **data** to the given **address**                                       |
| |                       The number of indices should match the number of dimensions the SRAM was declared with.                            |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **load**\(tile: :doc:`tile`\[T\]): Unit                                                                              |
| |                       Creates a burst load of a :doc:`tile` of a :doc:`dram` memory to this local memory.                                |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **gather**\(tile: :doc:`sparsetile`\[T\]): Unit                                                                      |
| |                       Creates a gather load from the given :doc:`sparsetile` of :doc:`dram` to this local memory.                        |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **par**\(p: :doc:`Int <../../common/fixpt>`): :doc:`sram`\[T\]                                                       |
| |                       Sets this SRAM to have an associated parallelization factor of **p**.                                              |
| |                       This parallelization factor is used in scatters and gathers to determine number of elements                        |
| |                       to be processed in parallel.                                                                                       |
+---------------------+----------------------------------------------------------------------------------------------------------------------+

