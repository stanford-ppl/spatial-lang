
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

.. _StreamIn:

StreamIn
========


**StreamIn** defines a hardware bus used to receive streaming data from outside of the FPGA.
StreamIns may not be written to. For streaming outputs, use :doc:`streamout`.
StreamIns are specified using a :doc:`../../typeclasses/bits`-based type and a target :doc:`bus`.

In Spatial, StreamIns are specified outside the Accel block, in host code.


-----------------

**Static methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `object`         **StreamIn**                                                                                                         |
+=====================+======================================================================================================================+
| |               def   **apply**\[T::doc:`../../typeclasses/bits`\](bus: :doc:`bus`): :doc:`streamin`\[T\]                                  |
| |                       Creates a StreamIn of type T connected to the specified target bus pins                                            |
+---------------------+----------------------------------------------------------------------------------------------------------------------+

-------------

**Infix methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `class`          **StreamIn**\[T\]                                                                                                    |
+=====================+======================================================================================================================+
| |               def   **value**\: T                                                                                                        |
| |                       Returns the current value of this StreamIn                                                                         |
| |                       This method is often unnecessary, as the compiler will attempt to add stream reads implicitly                      |
| |                       In hardware, equivalent to a wire connected to the streaming bus                                                   |
+---------------------+----------------------------------------------------------------------------------------------------------------------+


--------------

**Implicit methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **readStream**\[T::doc:`../../typeclasses/bits`\](stream: :doc:`streamin`\[T\]): T                                   |
| |                       Implicitly adds a stream read                                                                                      |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
