
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

.. _CounterChain:

CounterChain
============

CounterChain describes a set of chained hardware counters, where a given counter increments only when the counter
below it wraps around. Order is specified as outermost counter first to innermost counter last.

CounterChains are generally created implicitly for small numbers of counters, and need only be created explicitly for
more than 3 chained counters.

---------------

**Static methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `object`         **CounterChain**                                                                                                     |
+=====================+======================================================================================================================+
| |               def   **apply**\(counters: :doc:`counter`\*): :doc:`counterchain`                                                          |
| |                       Creates a chain of counters. Order is specified as outermost on the left to innermost on the right                 |
+---------------------+----------------------------------------------------------------------------------------------------------------------+


