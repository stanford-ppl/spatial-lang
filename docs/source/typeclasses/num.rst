
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

.. _Num:

Num
====

Combination of :doc:`arith`, :doc:`bits`, and :doc:`order` type classes

**Abstract Methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `trait`         **Num**\[T\] `extends` :doc:`arith`\[T\] `with` :doc:`bits`\[T\] `with` :doc:`order`\[T\]                             |
+=====================+======================================================================================================================+
| |      abstract def   **negate**\(x: T): T                                                                                                 |
| |                       Returns a negated version of the given value                                                                       |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |      abstract def   **plus**\(x: T, y: T): T                                                                                             |
| |                       Returns the result of adding x and y                                                                               |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |      abstract def   **minus**\(x: T, y: T): T                                                                                            |
| |                       Returns the result of subtracting y from x                                                                         |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |      abstract def   **times**\(x: T, y: T): T                                                                                            |
| |                       Returns the result of multiplying x and y                                                                          |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |      abstract def   **divide**\(x: T, y: T): T                                                                                           |
| |                       Returns the result of dividing x by y                                                                              |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |      abstract def   **zero**\: T                                                                                                         |
| |                       Creates the zero value for type T                                                                                  |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |      abstract def   **one**\: T                                                                                                          |
| |                       Creates the one value for type T                                                                                   |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |      abstract def   **random**\(max: Option[T]): T                                                                                       |
| |                       Generates a pseudorandom value uniformly distributed between 0 and max.                                            |
| |                       If max is unspecified, type T's default maximum is used instead.                                                   |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |      abstract def   **length**\(x: T, y: T): T                                                                                           |
| |                       Returns the number of bits required to represent this type.                                                        |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |      abstract def   **lessThan**\(x: T, y: T): :doc:`../common/boolean`                                                                  |
| |                       Returns `true` if x is less than y, `false` otherwise                                                              |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |      abstract def   **lessThanOrEqual**\(x: T, y: T): :doc:`../common/boolean`                                                           |
| |                       Returns `true` if x is less than or equal to y, `false` otherwise                                                  |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |      abstract def   **equal**\(x: T, y: T): :doc:`../common/boolean`                                                                     |
| |                       Returns `true` if x and y are equal, `false` otherwise                                                             |
+---------------------+----------------------------------------------------------------------------------------------------------------------+




