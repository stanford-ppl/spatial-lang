
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

.. _RegFile:

RegFile
=======


**RegFiles** are on-chip arrays of registers with fixed size. RegFiles currently can be specified as one or two dimensional.
Like other memories in Spatial, the contents of RegFiles are persistent across loop iterations, even when they are declared
in an inner scope.

Using the **<<=** operator, RegFiles can be used as shift registers. 2-dimensional RegFiles must select a specific
row or column before shifting using `regfile(row, *)` or regfile(*, col)`, respectively.

---------------

**Static methods**

+---------------------+-------------------------------------------------------------------------------------------------------------------------------------------------------+
| |               def   **RegFile**\[T::doc:`../../typeclasses/bits`\](columns: :doc:`Int <../../common/fixpt>`\): :doc:`regfile`\[T\]                                        |
| |                       Creates a 1-dimensional RegFile with given number of **columns**.                                                                                   |
| |                       **columns** must be a statically determinable signed integer.                                                                                       |
+---------------------+-------------------------------------------------------------------------------------------------------------------------------------------------------+
| |               def   **RegFile**\[T::doc:`../../typeclasses/bits`\](rows: :doc:`Int <../../common/fixpt>`, columns: :doc:`Int <../../common/fixpt>`\): :doc:`regfile`\[T\] |
| |                       Creates a 2-dimensional RegFile with given number of **rows** and **columns**.                                                                      |
| |                       **rows** and **columns** must be statically determinable signed integers.                                                                           |
+---------------------+-------------------------------------------------------------------------------------------------------------------------------------------------------+


--------------

**Infix methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `class`         **RegFile**\[T\]                                                                                                      |
+=====================+======================================================================================================================+
| |               def   **apply**\(address: :doc:`Int <../../common/fixpt>`\*): T                                                            |
| |                       Creates a load port to this RegFile at the given multi-dimensional address.                                        |
| |                       The number of indices should match the number of dimensions the SRAM was declared with.                            |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **update**\(address: :doc:`Int <../../common/fixpt>`\*, data: T)                                                     |
| |                       Creates a store port to this RegFile, writing **data** to the given **address**                                    |
| |                       The number of indices should match the number of dimensions the SRAM was declared with.                            |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **load**\(tile: :doc:`tile`\[T\]): Unit                                                                              |
| |                       Creates a burst load of a :doc:`tile` of a :doc:`dram` memory to this local memory.                                |
+---------------------+----------------------------------------------------------------------------------------------------------------------+

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `class`         **RegFile (1D)**\[T\]                                                                                                 |
+=====================+======================================================================================================================+
| |               def   **<<=**\(data: T): Unit                                                                                              |
| |                       Turns this RegFile into a shift register.                                                                          |
| |                       Shifts the given data element into index 0 and all other positions over by 1.                                      |
+---------------------+----------------------------------------------------------------------------------------------------------------------+

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `class`         **RegFile (2D)**\[T\]                                                                                                 |
+=====================+======================================================================================================================+
| |               def   **apply**\(row: :doc:`Int <../../common/fixpt>`, columns: Wildcard): Unit                                            |
| |                       Gives a 1D RegFile view of the specified row of this RegFile.                                                      |
| |                       Used to specify shifting a value into a specific row of a 2D RegFile.                                              |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **apply**\(rows: Wildcard, column: :doc:`Int <../../common/fixpt>`): Unit                                            |
| |                       Gives a 1D RegFile view of the specified column of this RegFile.                                                   |
| |                       Used to specify shifting a value into a specific column of a 2D RegFile.                                           |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
