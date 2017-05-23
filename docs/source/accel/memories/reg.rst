
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

.. _Reg:

Reg
===


**Reg** defines a hardware register used to hold a scalar value.
The default reset value for a Reg is the numeric zero value for it's specified type.

**ArgIn**, **ArgOut**, and **HostIO** are specialized forms of Reg which are used to transfer scalar values
to and from the accelerator. ArgIns and ArgOuts are used for setup values at the initialization of the FPGA.
ArgIns may not be written to, while ArgOuts generally should not be read from.
HostIOs are for values which may be continuously changed or read by the host during FPGA execution.

In Spatial, ArgIns, ArgOuts, and HostIO registers are specified outside the Accel block, in host code.


-----------------

**Static methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `object`         **Reg**                                                                                                              |
+=====================+======================================================================================================================+
| |               def   **apply**\[T::doc:`../../typeclasses/bits`\]: :doc:`reg`\[T\]                                                        |
| |                       Creates a register of type T with reset value :doc:`zero\[T\] <../../typeclasses/bits>`                            |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **apply**\[T::doc:`../../typeclasses/bits`\](init: T): :doc:`reg`\[T\]                                               |
| |                       Creates a register of type T with reset value **init**                                                             |
+---------------------+----------------------------------------------------------------------------------------------------------------------+

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `object`         **ArgIn**                                                                                                            |
+=====================+======================================================================================================================+
| |               def   **apply**\[T::doc:`../../typeclasses/bits`\]: :doc:`reg`\[T\]                                                        |
| |                       Creates an input argument register of type T with reset value zero                                                 |
+---------------------+----------------------------------------------------------------------------------------------------------------------+

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `object`         **ArgOut**                                                                                                           |
+=====================+======================================================================================================================+
| |               def   **apply**\[T::doc:`../../typeclasses/bits`\]: :doc:`reg`\[T\]                                                        |
| |                       Creates an output argument register of type T with reset value zero                                                |
+---------------------+----------------------------------------------------------------------------------------------------------------------+

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `object`         **HostIO**                                                                                                           |
+=====================+======================================================================================================================+
| |               def   **apply**\[T::doc:`../../typeclasses/bits`\]: :doc:`reg`\[T\]                                                        |
| |                       Creates an host input/output register of type T with reset value zero                                              |
+---------------------+----------------------------------------------------------------------------------------------------------------------+


-------------

**Infix methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `class`          **Reg**\[T\]                                                                                                         |
+=====================+======================================================================================================================+
| |               def   **:=**\(data: T): Void                                                                                               |
| |                       Creates a write of **data** to this Reg.                                                                           |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **value**\: T                                                                                                        |
| |                       Returns the current value of this register                                                                         |
| |                       This method is often unnecessary, as the compiler will attempt to add register reads implicitly                    |
| |                       In hardware, equivalent to a wire connected to the output of the register                                          |
+---------------------+----------------------------------------------------------------------------------------------------------------------+


--------------

**Implicit methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **readReg**\[T::doc:`../../typeclasses/bits`\](reg: :doc:`reg`\[T\]): T                                              |
| |                       Implicitly adds a register read                                                                                    |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
