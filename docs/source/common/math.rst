
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

.. _Math:

Math
====

Commonly used mathematical operators

Methods
-------

.. parsed-literal::

  :maroon:`def` pow\[T::doc:`num`\](x: T, n: Int): T

Integer power implemented as a simple reduction tree 

	* **n** \- exponent, currently must be an integer greater than zero

*********

.. parsed-literal::

  :maroon:`def` productTree\[T::doc:`num`\](x: Seq\[T\]): T

Creates a reduction tree which calculates the product of the given symbols


*********

.. parsed-literal::

  :maroon:`def` reduceTree(syms: Seq\[T\])(rFunc: (T, T) => T): T

Creates a reduction tree structure of the given list of symbols 

	* **syms** \- List of symbols to reduce
	* **rFunc** \- Associative reduction function

*********

.. parsed-literal::

  :maroon:`def` sumTree\[T::doc:`num`\](x: Seq\[T\]): T

Creates a reduction tree which calculates the sum of the given symbols

