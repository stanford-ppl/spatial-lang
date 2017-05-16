
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

.. _DRAM:

DRAM
====


DRAMs are pointers to locations in the accelerator's main memory comprising dense multi-dimensional arrays. They are the primary form of communication
of data between the host and the accelerator. Data may be loaded to and from the accelerator in contiguous chunks (Tiles),
or by bulk scatter and gather operations (SparseTiles).

A dense :doc:`tile` can be created from a DRAM either using address range selection or by implicit conversion.
When a Tile is created implicitly, it has the same address space as the entire original DRAM.

In Spatial, DRAMs are specified outside the Accel scope in the host code.

----------------

**Static Methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `object`         **DRAM**                                                                                                             |
+=====================+======================================================================================================================+
| |               def   **apply**\[T::doc:`../../typeclasses/bits`\](dimensions: :doc:`Int <../../common/fixpt>`\*): :doc:`dram`\[T\]        |
| |                       Creates a reference to an N-dimensional array in main memory with given **dimensions**                             |
| |                       The dimensions of a DRAM should be functions of constants, parameters, and :doc:`ArgIns <reg>`                     |
+---------------------+----------------------------------------------------------------------------------------------------------------------+

--------------

**Infix methods**

+---------------------+---------------------------------------------------------------------------------------------------------------------------------------+
|      `class`         **DRAM**\[T\]                                                                                                                          |
+=====================+=======================================================================================================================================+
| |               def   **apply**\(ranges\: :doc:`../../common/range`\*): :doc:`tile`\[T\]                                                                    |
| |                       Creates a reference to a :doc:`tile` of this DRAM for creating burst loads and stores.                                              |
| |                       The number of ranges should match the dimensionality of this DRAM.                                                                  |
| |                       Single :doc:`Int <../../common/fixpt>` values can be used as Ranges to create a Tile of smaller dimensionality.                     |
+---------------------+---------------------------------------------------------------------------------------------------------------------------------------+
| |               def   **apply**\(addrs: :doc:`sram`\[:doc:`Int <../../common/fixpt>`\]): :doc:`sparsetile`\[T\]                                             |
| |                       Creates a reference to a :doc:`sparsetile` of this DRAM for use in scatter and gather transfers                                     |
| |                       using all addresses from the **addrs** :doc:`sram` .                                                                                |
| |                       Note that creation of SparseTiles is currently only supported on 1-dimensional DRAMs.                                               |
| |                                                                                                                                                           |
| | 	                  * **addrs** \- SRAM with addresses to load                                                                                          |
+---------------------+---------------------------------------------------------------------------------------------------------------------------------------+
| |               def   **apply**\(addrs\: :doc:`sram`\[:doc:`Int <../../common/fixpt>`\], size\: :doc:`Int <../../common/fixpt>`): :doc:`sparsetile`\[T\]    |
| |                       Creates a reference to a :doc:`sparsetile` of this DRAM for use in scatter and gather transfers                                     |
| |                       using **size** addresses from the **addrs** :doc:`sram` .                                                                           |
| |                       Note that creation of SparseTiles is currently only supported on 1-dimensional DRAMs.                                               |
| |                                                                                                                                                           |
| | 	                  * **addrs** \- SRAM with addresses to load                                                                                          |
| |                       * **size** \- the number of addresses to use                                                                                        |
+---------------------+---------------------------------------------------------------------------------------------------------------------------------------+

--------------

**Implicit methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **createTile**\[T::doc:`../../typeclasses/bits`\](dram: :doc:`dram`\[T\]): :doc:`tile`\[T\]                          |
| |                       Implicitly converts a DRAM to a Tile with the same address space                                                   |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
