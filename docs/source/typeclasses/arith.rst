
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

.. _Arith:

Arith
=====

Type class used to supply evidence that type T has basic arithmetic operations defined on it.


Abstract Methods
----------------

.. parsed-literal::

  :maroon:`def` negate(x: T): T

Negate x

*********

.. parsed-literal::

  :maroon:`def` plus(x: T, y: T): T

Add x and y

*********

.. parsed-literal::

  :maroon:`def` minus(x: T, y: T): T

Subtract y from x

*********

.. parsed-literal::

  :maroon:`def` times(x: T, y: T): T

Multiply x and y

*********

.. parsed-literal::

  :maroon:`def` divide(x: T, y: T): T

Divide x by y



