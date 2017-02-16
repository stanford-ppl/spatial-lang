
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

.. _FltPt:

FltPt
=====

FltPt[G,E] represents an arbitrary precision, IEEE-754-like representation.
FltPt values are always assumed to be signed.

The type parameters for FltPt are:

+---+------------------------------------------------+---------------+
| G | Number of significand bits, including sign bit | (_2 - _64)    |
+---+------------------------------------------------+---------------+
| E | Number of exponent bits                        | (_1 - _64)    |
+---+------------------------------------------------+---------------+

Note that numbers of bits use the underscore prefix as integers cannot be used as type parameters in Scala.


**Type Aliases**

Specific types of FltPt values can be managed using type aliases.
New type aliases can be created using:::

    type MyType = FltPt[_##,_##]


Spatial defines the following type aliases by default:

+----------+---------+-------------------------+---------------------------+
| **type** | Half    | :doc:`fltpt`\[_11,_5\]  | IEEE-754 half precision   |
+----------+---------+-------------------------+---------------------------+
| **type** | Float   | :doc:`fltpt`\[_24,_8\]  | IEEE-754 single precision |
+----------+---------+-------------------------+---------------------------+
| **type** | Double  | :doc:`fltpt`\[_53,_11\] | IEEE-754 double precision |
+----------+---------+-------------------------+---------------------------+

Note that the Float and Double types shadow their respective unstaged Scala types.
In the case where an unstaged type is required, use the full `scala.*` name.


**Infix methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `class`          **FltPt**\[G, E\]                                                                                                    |
+=====================+======================================================================================================================+
| |               def   **unary_-**\: :doc:`fltpt`\[G,E\]                                                                                    |
| |                       Negates this fixed point value                                                                                     |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **+**\(rhs: :doc:`fltpt`\[G,E\]): :doc:`fltpt`\[G,E\]                                                                |
| |                       Floating point addition                                                                                            |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **-**\(rhs: :doc:`fltpt`\[G,E\]): :doc:`fltpt`\[G,E\]                                                                |
| |                       Floating point subtraction                                                                                         |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   *****\(rhs: :doc:`fltpt`\[G,E\]): :doc:`fltpt`\[G,E\]                                                                |
| |                       Floating point multiplication                                                                                      |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **\*\***\(exp: scala.Int): :doc:`fltpt`\[G,E\]                                                                       |
| |                       Integer power, implemented in hardware as a reduction tree with **exp** inputs                                     |
| |                                                                                                                                          |
| |                       * **exp** \- exponent, currently must be an integer greater than zero                                              |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **\/**\(rhs: :doc:`fltpt`\[G,E\]): :doc:`fltpt`\[G,E\]                                                               |
| |                       Floating point division                                                                                            |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **<**\(rhs: :doc:`fltpt`\[G,E\]): :doc:`fltpt`\[G,E\]                                                                |
| |                       Less than comparison.                                                                                              |
| |                       Returns `true` if this value is less than the right hand side. Otherwise returns `false`.                          |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **<=**\(rhs: :doc:`fltpt`\[G,E\]): :doc:`fltpt`\[G,E\]                                                               |
| |                       Less than or equal comparison                                                                                      |
| |                       Returns `true` if this value is less than or equal to the right hand side. Otherwise returns `false`.              |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **>**\(rhs: :doc:`fltpt`\[G,E\]): :doc:`fltpt`\[G,E\]                                                                |
| |                       Greater than comparison                                                                                            |
| |                       Returns `true` if this value is greater than the right hand side. Otherwise returns `false`.                       |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **>=**\(rhs: :doc:`fltpt`\[G,E\]): :doc:`fltpt`\[G,E\]                                                               |
| |                       Greater than or equal comparison                                                                                   |
| |                       Returns `true` if this value is greater than or equal to the right hand side. Otherwise returns `false`.           |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **!=**\(rhs: :doc:`fltpt`\[G,E\]): :doc:`fltpt`\[G,E\]                                                               |
| |                       Value inequality comparison                                                                                        |
| |                       Returns `true` if this value is not equal to the right hand side. Otherwise returns `false`.                       |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **==**\(rhs: :doc:`fltpt`\[G,E\]): :doc:`fltpt`\[G,E\]                                                               |
| |                       Value equality comparison                                                                                          |
| |                       Returns `true` if this value is equal to the right hand side. Otherwise returns `false`.                           |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **toString**\: :doc:`../cpu/string`                                                                                  |
| |                       Creates a printable String from this value                                                                         |
| |                                                                                                                                          |
| |                       \[**NOTE**\] This method is unsynthesizable, and can be used only on the CPU or in simulation.                     |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
