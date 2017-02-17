
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

.. _Tup2:

Tup2
====

Tup2[A,B] is a simple data structure used to hold a pair of staged values.

-----------

**Infix methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `class`          **Tup2**\[A, B\]                                                                                                     |
+=====================+======================================================================================================================+
| |               def   **_1**\: A                                                                                                           |
| |                       Returns the first field in this tuple                                                                              |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **_2**\: B                                                                                                           |
| |                       Returns the second field in this tuple                                                                             |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **toString**\: :doc:`../cpu/string`                                                                                  |
| |                       Creates a printable String from this value                                                                         |
| |                                                                                                                                          |
| |                       \[**NOTE**\] This method is unsynthesizable, and can be used only on the CPU or in simulation.                     |
+---------------------+----------------------------------------------------------------------------------------------------------------------+

----------

**Related methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **pack**\[A,B\](x: Tuple2[A,B]): :doc:`tup2`\[A,B\]                                                                  |
| |                       Creates a staged tuple from the given unstaged tuple                                                               |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **pack**\[A,B\](a: A, b: B): :doc:`tup2`\[A,B\]                                                                      |
| |                       Creates a staged tuple from the given pair of values. Shorthand for ``pack((a,b))``                                |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **unpack**\[A,B\](x: :doc:`tup2`\[A,B\]): Tuple2\[A,B\]                                                              |
| |                       Returns an unstaged tuple from this staged tuple. Shorthand for ``(x._1, x._2)``                                   |
+---------------------+----------------------------------------------------------------------------------------------------------------------+


