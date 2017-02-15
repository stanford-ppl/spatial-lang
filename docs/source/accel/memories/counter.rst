
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

.. _Counter:

Counter
=======

Counter is a single hardware counter with an associated start (inclusive), end (exclusive), step size, and parallelization factor.
By default, the parallelization factor is assumed to be a design parameter. Counters can be chained together using
CounterChain, but this is typically done implicitly when creating controllers.


Static methods
--------------

.. parsed-literal::

  :maroon:`def` apply(end: :doc:`Index <fixpt>`): :doc:`counter`

Creates a Counter with start of 0, given end, and step size of 1


*********

.. parsed-literal::

  :maroon:`def` apply(start: :doc:`Index <fixpt>`, end: :doc:`Index <fixpt>`): :doc:`counter`

Creates a Counter with given start and end, and step size of 1


*********

.. parsed-literal::

  :maroon:`def` apply(start: :doc:`Index <fixpt>`, end: :doc:`Index <fixpt>`, step: :doc:`Index <fixpt>`): :doc:`counter`

Creates a Counter with given start, end and step size


*********

.. parsed-literal::

  :maroon:`def` apply(start: :doc:`Index <fixpt>`, end: :doc:`Index <fixpt>`, step: :doc:`Index <fixpt>`, par: Int): :doc:`counter`

Creates a Counter with given start, end, step size, and parallelization factor


