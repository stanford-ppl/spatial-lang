
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

.. _LUT:

LUT
====

**LUTs** are on-chip, read-only memories of fixed size. LUTs can be specified as 1 to 5 dimensional.

---------------

**Static methods**

+---------------------+-----------------------------------------------------------------------------------------------------------------------------+
|      `object`         **LUT**                                                                                                                     |
+=====================+=============================================================================================================================+
| |               def   **apply**\[T::doc:`../../typeclasses/bits`\](dimensions: :doc:`Int <../../common/fixpt>`\*)(elements: T*): :doc:`lut`\[T\]  |
| |                       Creates a N-dimensional LUT with given **dimensions** and **elements**.                                                   |
| |                       The number of elements supplied must match the product of the dimensions                                                  |
| |                       **Dimensions** must be statically determinable signed integers.                                                           |
+---------------------+-----------------------------------------------------------------------------------------------------------------------------+


--------------

**Infix methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `class`         **LUT**\[T\]                                                                                                          |
+=====================+======================================================================================================================+
| |               def   **apply**\(address: :doc:`Int <../../common/fixpt>`\*): T                                                            |
| |                       Creates a load port to this LUT at the given multi-dimensional address.                                            |
| |                       The number of indices should match the number of dimensions the LUT was declared with.                             |
+---------------------+----------------------------------------------------------------------------------------------------------------------+

