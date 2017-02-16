
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


SRAMs are on-chip scratchpads with fixed size. SRAMs can be specified as multi-dimensional, but the underlying addressing
in hardware is always flat. The contents of SRAMs are currently persistent across loop iterations, even when they are declared in an inner scope.
SRAMs can have an arbitrary number of readers but only one writer. This writer may be an element-based store or a load from a DRAM memory.


Static methods
--------------

.. parsed-literal::

  :maroon:`def` apply(dims: :doc:`Index <fixpt>`\*)(:maroon:`implicit` ev0: Num[T]): :doc:`sram`\[T\]

Creates a SRAM with given dimensions. Dimensions must be statically known signed integers (constants or parameters). 


Infix methods
-------------

.. parsed-literal::

  :maroon:`def` :=(tile: :doc:`tile`\[T\])(:maroon:`implicit` ev0: Num[T]): Unit

Stores a Tile of a DRAM to this SRAM. 


*********

.. parsed-literal::

  :maroon:`def` :=(tile: :doc:`sparsetile`\[T\]): Unit

Gathers the values from the supplied SparseTile into this SRAM 


*********

.. parsed-literal::

  :maroon:`def` apply(ii: :doc:`Index <fixpt>`\*): T

Creates a read from this SRAM at the given multi-dimensional address. Number of indices given can either be 1 or the same as the number of dimensions that the SRAM was declared with. 

	* **ii** \- multi-dimensional address

*********

.. parsed-literal::

  :maroon:`def` par(y: Int): :doc:`sram`\[T\]




*********

.. parsed-literal::

  :maroon:`def` update(i: :doc:`Index <fixpt>`, x: T): Unit

Creates a write to this SRAM at the given 1D address. 

	* **i** \- 1D address
	* **x** \- element to be stored to SRAM

*********

.. parsed-literal::

  :maroon:`def` update(i: :doc:`Index <fixpt>`, j: :doc:`Index <fixpt>`, x: T): Unit

Creates a write to this SRAM at the given 2D address. The SRAM must have initially been declared as 2D. 

	* **i** \- row index
	* **j** \- column index
	* **x** \- element to be stored to SRAM

*********

.. parsed-literal::

  :maroon:`def` update(i: :doc:`Index <fixpt>`, j: :doc:`Index <fixpt>`, k: :doc:`Index <fixpt>`, x: T): Unit

Creates a write to this SRAM at the given 3D address. The SRAM must have initially been declared as 3D. 

	* **i** \- row index
	* **j** \- column index
	* **k** \- page index
	* **x** \- element to be stored to SRAM

*********

.. parsed-literal::

  :maroon:`def` update(ii: List\[:doc:`Index <fixpt>`\], x: T): Unit

Creates a write to this SRAM at the given multi-dimensional address. The number of indices given can either be 1 or the same as the number of dimensions that the SRAM was declared with. 

	* **ii** \- multi-dimensional index
	* **x** \- element to be stored to SRAM

