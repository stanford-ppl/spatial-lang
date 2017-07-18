
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

.. _BufferedOut:

BufferedOut
===========


**BufferedOut** defines an addressable output stream from the accelerator, implemented in hardware as an SRAM buffer
which is asynchronously (and implicitly) used to drive a set of output pins.

BufferedOut is similar in functionality to :doc:`streamout`, but allows address and frame-based writing.
This is useful, for example, in video processing, when video needs to be processed on a frame-by-frame basis rather
than a pixel-by-pixel basis.

In Spatial, BufferedOuts are specified outside the Accel block, in host code.


-----------------

**Static methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `object`         **BufferedOut**                                                                                                      |
+=====================+======================================================================================================================+
| |               def   **apply**\[T::doc:`../../typeclasses/bits`\](bus: :doc:`bus`): :doc:`bufferedout`\[T\]                               |
| |                       Creates a BufferedOut of type T connected to the specified target bus pins                                         |
| |                       The size of the created buffer is currently fixed at 320 rows by 240 columns                                       |
+---------------------+----------------------------------------------------------------------------------------------------------------------+

-------------

**Infix methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `class`          **BufferedOut**\[T\]                                                                                                 |
+=====================+======================================================================================================================+
| |               def   **update**\(row: :doc:`Int <../../common/fixpt>`, col: :doc:`Int <../../common/fixpt>`, data: T): Unit               |
| |                       Creates a write port to this output buffer with the 2D address (row, col) and corresponding data                   |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
