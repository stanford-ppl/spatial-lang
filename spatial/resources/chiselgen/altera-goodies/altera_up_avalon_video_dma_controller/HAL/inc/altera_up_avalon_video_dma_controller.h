#ifndef __ALTERA_UP_AVALON_VIDEO_DMA_CONTROLLER_H__
#define __ALTERA_UP_AVALON_VIDEO_DMA_CONTROLLER_H__

#include <stddef.h>
#include <alt_types.h>
#include <sys/alt_dev.h>

#ifdef __cplusplus
extern "C"
{
#endif /* __cplusplus */

#define ALT_UP_VIDEO_DMA_CONSECUTIVE_ADDRESS_MODE 1
#define ALT_UP_VIDEO_DMA_XY_ADDRESS_MODE 0

/*
 * Device structure definition. Each instance of the driver uses one
 * of these structures to hold its associated state.
 */
typedef struct alt_up_video_dma_dev {
	/// @brief character mode device structure 
	/// @sa Developing Device Drivers for the HAL in Nios II Software Developer's Handbook
	alt_dev dev;
	/// @brief the video dma's slave base address
	unsigned int base;
	/// @brief the memory buffer's start address
	unsigned int buffer_start_address;
	/// @brief the memory back buffer's start address
	unsigned int back_buffer_start_address;
	/// @brief the addressing mode 
	unsigned int addressing_mode;
	/// @brief the number of bits for color
	unsigned int color_bits;
	/// @brief the number of color planes
	unsigned int color_planes;
	/// @brief the data width in bytes 
	unsigned int data_width;
	/// @brief the resolution in x direction 
	unsigned int x_resolution;
	/// @brief the resolution in y direction 
	unsigned int y_resolution;
	/// @brief the x/y coordinate offset/masks
	unsigned int x_coord_offset;
	unsigned int x_coord_mask;
	unsigned int y_coord_offset;
	unsigned int y_coord_mask;
} alt_up_video_dma_dev;

///////////////////////////////////////////////////////////////////////////////
// HAL system functions

///////////////////////////////////////////////////////////////////////////////
// file-like operation functions

///////////////////////////////////////////////////////////////////////////////
// direct operation functions
/**
 * @brief Opens the video dma controller device specified by <em> name </em>
 *
 * @param name -- the video dma controller component name in Qsys 
 *
 * @return The corresponding device structure, or NULL if the device is not found
 **/
alt_up_video_dma_dev* alt_up_video_dma_open_dev(const char* name);

/**
 * @brief Changes the back buffer's start address
 *
 * @param video_dma		-- the pointer to the Video DMA device structure
 * @param new_address	-- the new start address of the back buffer
 *
 * @return 0 for success
 **/
int alt_up_video_dma_ctrl_set_bb_addr(alt_up_video_dma_dev *video_dma, unsigned int new_address);

/**
 * @brief Swaps which buffer is being read/written to by the DMA controller
 *
 * @param video_dma		-- the pointer to the Video DMA device structure
 *
 * @return 0 for success
 **/
int alt_up_video_dma_ctrl_swap_buffers(alt_up_video_dma_dev *video_dma);

/**
 * @brief Check if swapping buffers has completed
 *
 * @param video_dma		-- the pointer to the Video DMA device structure
 *
 * @return 0 if complete, 1 if still processing 
 **/
int alt_up_video_dma_ctrl_check_swap_status(alt_up_video_dma_dev *video_dma);

/**
 * @brief Draw a symbol at the location specified by <em>(x, y)</em> on the VGA monitor
 *
 * @param video_dma		-- the pointer to the Video DMA device structure
 * @param symbol		-- the symbol to be drawn
 * @param x				-- the \em x coordinate
 * @param y				-- the \em y coordinate
 * @param backbuffer	-- set to 1 to select the back buffer, otherwise set to 0 to select the current screen.
 *
 * @return 0 for success, -1 for error (such as out of bounds)
 **/
int alt_up_video_dma_draw(alt_up_video_dma_dev *video_dma, unsigned int symbol, 
		unsigned int x, unsigned int y, int backbuffer);

/**
 * @brief This function draws a box of a given symbol between points (x0,y0) and (x1,y1).
 *
 * @param video_dma		-- the pointer to the Video DMA device structure
 * @param symbol		-- the symbol to be drawn
 * @param x0,x1,y0,y1	-- coordinates of the top left (x0,y0) and bottom right (x1,y1) corner of the box
 * @param backbuffer	-- set to 1 to select the back buffer, otherwise set to 0 to select the current screen.
 * @param fill			-- set to 1 to fill the entire box, otherwise set to 0 to draw only the perimeter.
 *
 * @return 0 for success, -1 for error (such as out of bounds)
 **/
int alt_up_video_dma_draw_box(alt_up_video_dma_dev *video_dma, unsigned int symbol, 
		unsigned int x0, unsigned int y0, unsigned int x1, unsigned int y1, int backbuffer, int fill);

/**
 * @brief This function draws a line of a given symbol between points (x0,y0) and (x1,y1).
 *
 * @param video_dma		-- the pointer to the Video DMA device structure
 * @param symbol		-- the symbol to be drawn
 * @param x0,x1,y0,y1	-- coordinates (x0,y0) and (x1,y1) correspond to end points of the line
 * @param backbuffer	-- set to 1 to select the back buffer, otherwise set to 0 to select the current screen.
 *
 * @return 0 for success, -1 for error (such as out of bounds)
 **/
int alt_up_video_dma_draw_line(alt_up_video_dma_dev *video_dma, unsigned int symbol, 
		unsigned int x0, unsigned int y0, unsigned int x1, unsigned int y1, int backbuffer);

/**
 * @brief Draw a NULL-terminated text string starting at the location specified by <em>(x, y)</em>
 *
 * @param video_dma		-- the pointer to the Video DMA device structure
 * @param str			-- the character string to draw
 * @param x				-- the \em x coordinate
 * @param y				-- the \em y coordinate
 * @param backbuffer	-- set to 1 to select the back buffer, otherwise set to 0 to select the current screen.
 *
 * @return 0 for success, -1 for error (such as out of bounds)
 **/
int alt_up_video_dma_draw_string(alt_up_video_dma_dev *char_buffer, const char *str, 
		unsigned int x, unsigned int y, int backbuffer);

/**
 * @brief Clears the entire screen by filling it with the symbol 0.
 *
 * @param video_dma		-- the pointer to the Video DMA device structure
 * @param backbuffer	-- set to 1 to select the back buffer, otherwise set to 0 to select the current screen.
 **/
void alt_up_video_dma_screen_clear(alt_up_video_dma_dev *video_dma, int backbuffer);

/**
 * @brief Fill the entire screen with the given symbol
 *
 * @param video_dma		-- the pointer to the Video DMA device structure
 * @param symbol		-- the symbol to be drawn
 * @param backbuffer	-- set to 1 to select the back buffer, otherwise set to 0 to select the current screen.
 **/
void alt_up_video_dma_screen_fill(alt_up_video_dma_dev *video_dma, unsigned int symbol, int backbuffer);

///////////////////////////////////////////////////////////////////////////////
// Macros used by alt_sys_init 
#define ALTERA_UP_AVALON_VIDEO_DMA_CONTROLLER_INSTANCE(name, device)	\
static alt_up_video_dma_dev device =									\
{																		\
	{																	\
		ALT_LLIST_ENTRY,												\
		name##_NAME,													\
		NULL, /* open  */												\
		NULL, /* close */												\
		NULL, /* read  */												\
		NULL, /* write */												\
		NULL, /* lseek */												\
		NULL, /* fstat */												\
		NULL, /* ioctl */												\
	},																	\
	name##_BASE,														\
	0,		/* Default Buffer Starting Address		*/					\
	0,		/* Default Back Buf Starting Address	*/					\
	0,		/* Default to XY Addressing Mode		*/					\
	8,		/* Default Color Bits  		 			*/					\
	3,		/* Default Color Planes		 			*/					\
	4,		/* Default Data Width  		 			*/					\
	320,	/* Default X Resolution					*/					\
	240,	/* Default Y Resolution					*/					\
	1,		/* Default X Offset   		 			*/					\
	0x01FF,	/* Default X Mask      					*/					\
	10,		/* Default Y Offset    					*/					\
	0x00FF,	/* Default Y Mask      					*/					\
}

#define ALTERA_UP_AVALON_VIDEO_DMA_CONTROLLER_INIT(name, device)		\
{																		\
	device.buffer_start_address =										\
		(*((int *)(device.base)) & 0xFFFFFFFF);							\
	device.back_buffer_start_address =									\
		(*((int *)(device.base) + 1) & 0xFFFFFFFF);						\
	device.x_resolution =												\
		(*((int *)(device.base) + 2) & 0xFFFF);							\
	device.y_resolution =												\
		((*((int *)(device.base) + 2) >> 16) & 0xFFFF);					\
	device.addressing_mode =											\
		((*((int *)(device.base) + 3) >> 1) & 0x1);						\
	device.color_bits =													\
		((*((int *)(device.base) + 3) >> 8) & 0xF);						\
	device.color_planes =												\
		((*((int *)(device.base) + 3) >> 6) & 0x3);						\
																		\
	alt_u8 alt_up_dw = device.color_bits * device.color_planes;			\
	if (alt_up_dw <= 8) {												\
		device.data_width = 1;											\
	} else if (alt_up_dw <= 16) {										\
		device.data_width = 2;											\
	} else {															\
		device.data_width = 4;											\
	}																	\
																		\
	alt_u8 alt_up_wiw =													\
		((*((int *)(device.base) + 3) >> 16) & 0xFF);					\
	alt_u8 alt_up_hiw =													\
		((*((int *)(device.base) + 3) >> 24) & 0xFF);					\
																		\
																		\
	if (device.data_width == 1) {										\
		device.x_coord_offset = 0;										\
	} else if (device.data_width == 2) {								\
		device.x_coord_offset = 1;										\
	} else {															\
		device.x_coord_offset = 2;										\
	}																	\
	device.x_coord_mask = 0xFFFFFFFF >> (32 - alt_up_wiw);				\
	device.y_coord_offset = alt_up_wiw + device.x_coord_offset;			\
	device.y_coord_mask = 0xFFFFFFFF >> (32 - alt_up_hiw);				\
																		\
	/* make the device available to the system */						\
	alt_dev_reg(&device.dev);											\
}

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* __ALTERA_UP_AVALON_VIDEO_DMA_CONTROLLER_H__ */


