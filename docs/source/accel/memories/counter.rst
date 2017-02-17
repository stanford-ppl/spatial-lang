
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

It is generally recommended to create a :doc:`../../common/range` and allow the compiler to implicitly convert this to a Counter,
as Range provides slightly better syntax sugar.

----------------

**Static Methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
|      `object`         **Counter**                                                                                                                                                                            |
+=====================+========================================================================================================================================================================================+
| |               def   **apply**\(end: :doc:`Int <../../common/fixpt>`): :doc:`counter`                                                                                                                       |
| |                       Creates a Counter with start of 0, given end, and step size of 1                                                                                                                     |
+---------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| |               def   **apply**\(start: :doc:`Int <../../common/fixpt>`, end: :doc:`Int <../../common/fixpt>`): :doc:`counter`                                                                               |
| |                       Creates a Counter with given start and end, and step size of 1                                                                                                                       |
+---------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| |               def   **apply**\(start: :doc:`Int <../../common/fixpt>`, end: :doc:`Int <../../common/fixpt>`, step: :doc:`Int <../../common/fixpt>`): :doc:`counter`                                        |
| |                       Creates a Counter with given start, end and step size                                                                                                                                |
+---------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| |               def   **apply**\(start: :doc:`Int <../../common/fixpt>`, end: :doc:`Int <../../common/fixpt>`, step: :doc:`Int <../../common/fixpt>`, par: :doc:`Int <../../common/fixpt>`): :doc:`counter`  |
| |                       Creates a Counter with given start, end, step size, and parallelization factor                                                                                                       |
| |                       **par** must be statically determinable, i.e. a function of constants and parameters                                                                                                 |
+---------------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+


