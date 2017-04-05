/* Pixel Buffer to ARM, video server side */

// TODO: How to define stream? FPGA_CHIP_BASE is
// the start of the pixel buffer, which starts at
// FPGA_CHIP_BASE and ends at FPGA_ONCHIP_END.
// Each short in this part of memory corresponds to 
// a pixel color value. The start of the memory corresponds
// to the color of pixel at upper-left corner.
//
// The frame rate is fixed at 60 fps. Can go lower
// than 60 fps. Need to allow user to choose if they want
// to read in a pixel? (Short) a line? (Line) a frame? (Frame)
val pixel_buffer_to_arm_in = StreamIn[Short](FPGA_CHIP_BASE)
val pixel_buffer_to_arm_in = StreamIn[Line](FPGA_CHIP_BASE)
val pixel_buffer_to_arm_in = StreamIn[Frame](FPGA_CHIP_BASE)

// TODO: How to define the UDP_SOCKET? 
// UDP_SOCKET needs to be an open socket at:
// IP address: 192.168.1.9, port: 21234
//
// TODO: what should be every streaming element?
// A pixel? (Too slow)
// A line? (What interesting operation can we do on a line?)
// A frame? (Would the UDP payload be too small?)
val pixel_buffer_to_arm_out = StreamOut[Short](UDP_SOCKET) 
// TODO:
// If we stream a line of frame, we need to specify how many columns 
// are in the line and what the row number is. This information
// is also needed by the UDP controller to know how many 
// bytes to send.
val pixel_buffer_to_arm_out = StreamOut[Line](UDP_SOCKET) ?
// TODO: 
// IF we stream a frame, we need to specify the resolution
// of the frame so that the UDP socket knows how many bytes
// to send.
val pixel_buffer_to_arm_out = StreamOut[Frame](UDP_SOCKET) ?

// Stream 100 elements to UDP
Foreach(*, 0 until 100) { i => 
  val x = pixel_buffer_to_arm_in.deq()
  pixel_buffer_to_arm_in.put(x)
}

==================================================

/* Client side: UDP to pixel buffer */
val UDP_to_pixel_buffer = StreamIn[...](UDP_SOCKET)
val pixel_buffer = StreamOut[...](FPGA_CHIP_BASE)

// Option 1: Stream 100 elements to pixel_buffer
Foreach(*, 0 until 100) {i =>
  val x = UDP_to_pixel_buffer.deq()
  pixel_buffer.put(x)
}

// Option 2: Stream 100 elements to a pixel_back_buffer,
// and sync it every 1/60s (fps = 60)
Foreach(*, 0 until 100) {
  val x = UDP_to_pixel_buffer.deq()
  pixel_back_buffer.put(x)
}

Pipe(*)(60 Hz) {
  // Load can be done by activating the swapping
  // register. 
  //TODO: How to constantly sample a memory-mapped register
  // and activate it when it goes to zero?
}
