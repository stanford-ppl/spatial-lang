
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

.. _Parallel:

Parallel
========

**Parallel** is a control node which informs the compiler to schedule any inner control nodes in a fork-join manner.

Note: Parallel will be soon be deprecated for general use as the scheduling algorithms in the Spatial compiler improve.


--------------

**Static methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `object`         **Parallel**                                                                                                         |
+=====================+======================================================================================================================+
| |               def   **apply**\(body: => Unit): Unit                                                                                      |
| |                       Creates a parallel fork-join controller. Waits on completion of all controllers in the body.                       |
+---------------------+----------------------------------------------------------------------------------------------------------------------+



