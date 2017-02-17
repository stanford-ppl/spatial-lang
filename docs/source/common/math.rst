
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
| |               def   **pow**\[T::doc:`../typeclasses/num`\](x: T, n: scala.Int): T                                                        |
| |                     Integer power implemented in hardware as a reduction tree                                                            |
| |                                                                                                                                          |
| | 	                * **n** \- exponent, currently must be an integer greater than zero                                                  |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **productTree**\[T::doc:`../typeclasses/num`\](x: Seq\[T\]): T                                                       |
| |                     Creates a reduction tree which calculates the product of the given symbols                                           |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **reduceTree**\[T\](ins\: Seq\[T\])(func: (T, T) => T): T                                                            |
| |                     Creates a reduction tree structure of the given list of symbols                                                      |
| |                                                                                                                                          |
| |                     * **ins** \- List of symbols to reduce	                                                                             |
| |                     * **func** \- Associative reduction function                                                                         |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **sumTree**\[T::doc:`../typeclasses/num`\](x: Seq\[T\]): T                                                           |
| |                     Creates a reduction tree which calculates the sum of the given symbols                                               |
+---------------------+----------------------------------------------------------------------------------------------------------------------+


