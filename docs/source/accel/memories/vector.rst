
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

.. _Vector:

Vector
======


Vector defines a fixed size collection of scalar values.


Static methods
--------------

.. parsed-literal::

  :maroon:`def` apply(elems: T\*): :doc:`vector`\[T\]

Creates a new Vector containing the given elements 


Infix methods
-------------

.. parsed-literal::

  :maroon:`def` apply(i: Int): T

Extracts the element of this vector at the given index 

	* **i** \- the index of the element to extract

*********

.. parsed-literal::

  :maroon:`def` slice(start: Int, end: Int): :doc:`vector`\[T\]

Creates a subvector of this vector with elements [start, end) 

	* **start** \- index of the first element to include in the subvector
	* **end** \- end index of the slice, non-inclusive

