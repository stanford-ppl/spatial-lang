
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

.. _Bits:

Bits
====

Type class used to supply evidence that type T is representable by a statically known number of bits.

**Abstract Methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `trait`         **Bits**\[T\]                                                                                                         |
+=====================+======================================================================================================================+
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


**Related methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **zero**\[T: :doc:`bits`\]: T                                                                                        |
| |                       Returns the zero value for type T                                                                                  |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **one**\[T: :doc:`bits`\]: T                                                                                         |
| |                       Returns the one value for type T.                                                                                  |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **random**\[T: :doc:`bits`\]: T                                                                                      |
| |                       Generates a pseudorandom value uniformly distributed between 0 and the default max for type T.                     |
| |                                                                                                                                          |
| |                       \[**NOTE**\] This method is unsynthesizable, and can be used only on the CPU or in simulation.                     |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **random**\[T: :doc:`bits`\](max: T): T                                                                              |
| |                       Generates a pseudorandom value uniformly distributed between 0 and **max**.                                        |
| |                                                                                                                                          |
| |                       \[**NOTE**\] This method is unsynthesizable, and can be used only on the CPU or in simulation.                     |
+---------------------+----------------------------------------------------------------------------------------------------------------------+


