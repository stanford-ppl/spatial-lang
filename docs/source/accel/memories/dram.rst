
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


DRAMs are pointers to locations in the accelerator's main memory to dense multi-dimensional arrays. They are the primary form of communication
of data between the host and the accelerator. Data may be loaded to and from the accelerator in contiguous chunks (Tiles),
or by bulk scatter and gather operations (SparseTiles).


Direct methods
--------------

.. parsed-literal::

  :maroon:`def` DRAM\[T:Staged::doc:`bits`\](dims: :doc:`Index <fixpt>`\*): :doc:`dram`\[T\]

Creates a reference to a multi-dimensional array in main memory with given dimensions 


Infix methods
-------------

.. parsed-literal::

  :maroon:`def` apply(ranges: :doc:`range`\*): :doc:`tile`\[T\]

Creates a reference to a :doc:`tile` of this DRAM for creating burst loads and stores.
The number of ranges should match the dimensionality of this DRAM.
Single :doc:`Index <fixpt>` values can be used as :doc:`range`s to create a Tile of a smaller dimensionality.


*********

.. parsed-literal::

  :maroon:`def` apply(addrs: :doc:`sram`\[:doc:`Index <fixpt>`\], size: :doc:`Index <fixpt>`): :doc:`sparsetile`\[T\]

Sets up a :doc:`sparsetile` for use in bulk gather from this DRAM using *size* addresses from the supplied SRAM

	* **addrs** \- SRAM with addresses to load
	* **size** \- the number of addresses

*********

.. parsed-literal::

  :maroon:`def` apply(addrs: :doc:`sram`\[:doc:`Index <fixpt>`\]): :doc:`sparsetile`\[T\]

Sets up a :doc:`sparsetile` for use in bulk gather from this DRAM using all addresses from the supplied SRAM

	* **addrs** \- SRAM with addresses to load

