
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

**Methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   pow\[T::doc:`../typeclasses/num`\](x: T, n: Int): T                                                                  |
| |                     Integer power implemented in hardware as a reduction tree                                                            |
| |                                                                                                                                          |
| | 	                * **n** \- exponent, currently must be an integer greater than zero                                                  |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **productTree**\[T::doc:`../typeclasses/num`\](x: Seq\[T\]): T                                                       |
| |                     Creates a reduction tree which calculates the product of the given symbols                                           |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **reduceTree**\(syms: Seq\[T\])(func: (T, T) => T): T                                                                |
| |                     Creates a reduction tree structure of the given list of symbols                                                      |
| |                                                                                                                                          |
| |                     * **syms** \- List of symbols to reduce	                                                                             |
| |                     * **rFunc** \- Associative reduction function                                                                        |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   sumTree\[T::doc:`num`\](x: Seq\[T\]): T                                                                              |
| |                     Creates a reduction tree which calculates the sum of the given symbols                                               |
+---------------------+----------------------------------------------------------------------------------------------------------------------+


