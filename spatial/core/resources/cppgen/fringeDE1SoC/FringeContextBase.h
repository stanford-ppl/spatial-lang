#ifndef __FRINGE_CONTEXT_BASE_H__
#define __FRINGE_CONTEXT_BASE_H__

template <class T>
class FringeContextBase {
public:
  T *dut = NULL;
  std::string path = "";

  FringeContextBase(std::string p) {
    path = p;
  }
  virtual void load() = 0;
  virtual uint64_t malloc(size_t bytes) = 0;
  virtual void free(uint64_t buf) = 0;
  virtual void memcpy(uint64_t devmem, void* hostmem, size_t size) = 0;
  virtual void memcpy(void* hostmem, uint64_t devmem, size_t size) = 0;
  virtual void run() = 0;
  virtual void writeReg(uint32_t reg, uint64_t data) = 0;
  virtual uint64_t readReg(uint32_t reg) = 0;
  virtual uint64_t getArg(uint32_t arg, bool isIO) = 0;
  virtual void setArg(uint32_t reg, uint64_t data, bool isIO) = 0;
  // user-defined APIs
  virtual void start() = 0;
  virtual bool isDone() = 0;
  // video-related APIs
  // enable camera to write to its image buffer
  virtual void enableCamera() = 0;
  // disable camera from writing to its image buffer
  virtual void disableCamera() = 0;
  // enable the pixel buffers for VGA
  virtual void enablePixelBuffer() = 0;
  // disable the pixel buffers for VGA
  virtual void disablePixelBuffer() = 0;
  // write a pixel to the front pixel buffer of VGA
  virtual void writePixel2FrontBuffer(uint32_t row, uint32_t col, short pixel, bool swap) = 0;
  // write a pixel to the back pixel buffer of VGA
  virtual void writePixel2BackBuffer(uint32_t row, uint32_t col, short pixel, bool swap) = 0;
  // write one row of pixels to the front pixel buffer of VGA 
  // the row of pixels is stored in buf
  virtual void writeRow2FrontBuffer(uint32_t row, short* buf, bool swap) = 0;
  // write one row of pixels to the back pixel buffer of VGA
  // the row of pixels is stored in buf
  virtual void writeRow2BackBuffer(uint32_t row, short* buf, bool swap) = 0;
  // read a pixel from the image buffer that's connected to the camera
  virtual short readPixelFromCameraBuffer(uint32_t row, uint32_t col) = 0;
  // read a line from the image buffer that's connected to othe camera
  // user needs to pass a pointer to a buffer where pixel information will be stored
  virtual void readRowFromCameraBuffer(uint32_t row, short* buf) = 0;
  virtual void setNumArgIns(uint32_t number) = 0;
  virtual void setNumArgIOs(uint32_t number) = 0;

  ~FringeContextBase() {
//    delete dut;
  }
};

// Fringe error codes


// Fringe APIs - implemented only for simulation
void fringeInit(int argc, char **argv);

#endif
