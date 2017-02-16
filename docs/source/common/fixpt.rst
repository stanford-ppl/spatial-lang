
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

.. _FixPt:

FixPt
=====

FixPt[S,I,F] represents an arbitrary precision fixed point representation.
FixPt values may be signed or unsigned. Negative values, if applicable, are represented
in twos complement.

The type parameters for FixPt are:

+---+-----------------------------------+-----------------+
| S | Signed representation             | TRUE | FALSE    |
+---+-----------------------------------+-----------------+
| I | Number of integer bits            | (_1 - _64)      |
+---+-----------------------------------+-----------------+
| F | Number of fractional bits         | (_0 - _64)      |
+---+-----------------------------------+-----------------+

Note that numbers of bits use the underscore prefix as integers cannot be used as type parameters in Scala.


**Type Aliases**

Specific types of FixPt values can be managed using type aliases.
New type aliases can be created using syntax like the following::

  type Q16_16 = FixPt[TRUE,_16,_16]



Spatial defines the following type aliases by default:

+----------+-------+-----------------------------+-----------------------------------+
| **type** | Char  | :doc:`fixpt`\[TRUE,_8,_0\]  | Signed, 8 bit integer             |
+----------+-------+-----------------------------+-----------------------------------+
| **type** | Short | :doc:`fixpt`\[TRUE,_16,_0\] | Signed, 16 bit integer            |
+----------+-------+-----------------------------+-----------------------------------+
| **type** | Int   | :doc:`fixpt`\[TRUE,_32,_0\] | Signed, 32 bit integer            |
+----------+-------+-----------------------------+-----------------------------------+
| **type** | Index | :doc:`fixpt`\[TRUE,_32,_0\] | Signed, 32 bit integer (indexing) |
+----------+-------+-----------------------------+-----------------------------------+
| **type** | Long  | :doc:`fixpt`\[TRUE,_64,_0\] | Signed, 64 bit integer            |
+----------+-------+-----------------------------+-----------------------------------+

Note that the Char, Short, Int, and Long types shadow their respective unstaged Scala types.
In the case where an unstaged type is required, use the full `scala.*` name.

-------------

**Infix methods**

The following infix methods are defined on all FixPt classes. When the method takes a right hand argument,
only values of the same FixPt class can be used for this argument.

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `class`          **FixPt**\[S, I, F\]                                                                                                 |
+=====================+======================================================================================================================+
| |               def   **unary_-**\: :doc:`fixpt`\[S,I,F\]                                                                                  |
| |                       Negates this fixed point value                                                                                     |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **+**\(rhs: :doc:`fixpt`\[S,I,F\]): :doc:`fixpt`\[S,I,F\]                                                            |
| |                       Fixed point addition                                                                                               |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **-**\(rhs: :doc:`fixpt`\[S,I,F\]): :doc:`fixpt`\[S,I,F\]                                                            |
| |                       Fixed point subtraction                                                                                            |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   *****\(rhs: :doc:`fixpt`\[S,I,F\]): :doc:`fixpt`\[S,I,F\]                                                            |
| |                       Fixed point multiplication                                                                                         |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **\*\***\(exp: scala.Int): :doc:`fixpt`\[S,I,F\]                                                                     |
| |                       Integer power, implemented in hardware as a reduction tree with **exp** inputs                                     |
| |                                                                                                                                          |
| |                       * **exp** \- exponent, currently must be an integer greater than zero                                              |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **\/**\(rhs: :doc:`fixpt`\[S,I,F\]): :doc:`fixpt`\[S,I,F\]                                                           |
| |                       Fixed point division                                                                                               |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **&**\(rhs: :doc:`fixpt`\[S,I,F\]): :doc:`fixpt`\[S,I,F\]                                                            |
| |                       Bit-wise AND                                                                                                       |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **|**\(rhs: :doc:`fixpt`\[S,I,F\]): :doc:`fixpt`\[S,I,F\]                                                            |
| |                       Bit-wise OR                                                                                                        |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **<<**\(rhs: :doc:`fixpt`\[S,I,F\]): :doc:`fixpt`\[S,I,F\]                                                           |
| |                       Logical shift left                                                                                                 |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **>>**\(rhs: :doc:`fixpt`\[S,I,F\]): :doc:`fixpt`\[S,I,F\]                                                           |
| |                       Arithmetic (sign preserving) shift right                                                                           |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **<**\(rhs: :doc:`fixpt`\[S,I,F\]): :doc:`fixpt`\[S,I,F\]                                                            |
| |                       Less than comparison.                                                                                              |
| |                       Returns `true` if this value is less than the right hand side. Otherwise returns `false`.                          |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **<=**\(rhs: :doc:`fixpt`\[S,I,F\]): :doc:`fixpt`\[S,I,F\]                                                           |
| |                       Less than or equal comparison                                                                                      |
| |                       Returns `true` if this value is less than or equal to the right hand side. Otherwise returns `false`.              |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **>**\(rhs: :doc:`fixpt`\[S,I,F\]): :doc:`fixpt`\[S,I,F\]                                                            |
| |                       Greater than comparison                                                                                            |
| |                       Returns `true` if this value is greater than the right hand side. Otherwise returns `false`.                       |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **>=**\(rhs: :doc:`fixpt`\[S,I,F\]): :doc:`fixpt`\[S,I,F\]                                                           |
| |                       Greater than or equal comparison                                                                                   |
| |                       Returns `true` if this value is greater than or equal to the right hand side. Otherwise returns `false`.           |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **!=**\(rhs: :doc:`fixpt`\[S,I,F\]): :doc:`fixpt`\[S,I,F\]                                                           |
| |                       Value inequality comparison                                                                                        |
| |                       Returns `true` if this value is not equal to the right hand side. Otherwise returns `false`.                       |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **==**\(rhs: :doc:`fixpt`\[S,I,F\]): :doc:`fixpt`\[S,I,F\]                                                           |
| |                       Value equality comparison                                                                                          |
| |                       Returns `true` if this value is equal to the right hand side. Otherwise returns `false`.                           |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **toString**\: :doc:`../cpu/string`                                                                                  |
| |                       Creates a printable String from this value                                                                         |
| |                                                                                                                                          |
| |                       \[**NOTE**\] This method is unsynthesizable, and can be used only on the CPU or in simulation.                     |
+---------------------+----------------------------------------------------------------------------------------------------------------------+

--------------

**Specialized infix methods**

These methods are defined on only specific classes of FixPt values.

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `subclass`       **FixPt**\[S, I, _0\]                                                                                                |
+=====================+======================================================================================================================+
| |               def   **%**\(rhs: :doc:`fixpt`\[S,I,_0\]): :doc:`fixpt`\[S,I,_0\]                                                          |
| |                       Fixed point modulus                                                                                                |
| |                       Note that modulus is currently only defined for fixed point values with no fractional bits.                        |
+---------------------+----------------------------------------------------------------------------------------------------------------------+

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `subclass`       **Int** (aliases: **Index**, **FixPt**\[TRUE, _32, _0\])                                                             |
+=====================+======================================================================================================================+
| |               def   **::**\(end: :doc:`Int <fixpt>`): :doc:`range`                                                                       |
| |                       Creates a Range with this as the start (inclusive), the given end (noninclusive), and step of 1.                   |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **by**\(step: :doc:`Int <fixpt>`): :doc:`range`                                                                      |
| |                       Creates a Range with start of 0 (inclusive), this value as the end (noninclusive), and the given step.             |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **until**\(end: :doc:`Int <fixpt>`): :doc:`range`                                                                    |
| |                       Creates a Range with this as the start (inclusive), the given end (noninclusive), and step of 1.                   |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
