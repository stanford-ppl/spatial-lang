/******************************************************************************
*                                                                             *
* License Agreement                                                           *
*                                                                             *
* Copyright (c) Intel Corporation. All rights reserved.                       *
*                                                                             *
* Permission is hereby granted, free of charge, to any person obtaining a     *
* copy of this software and associated documentation files (the "Software"),  *
* to deal in the Software without restriction, including without limitation   *
* the rights to use, copy, modify, merge, publish, distribute, sublicense,    *
* and/or sell copies of the Software, and to permit persons to whom the       *
* Software is furnished to do so, subject to the following conditions:        *
*                                                                             *
* The above copyright notice and this permission notice shall be included in  *
* all copies or substantial portions of the Software.                         *
*                                                                             *
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR  *
* IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,    *
* FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE *
* AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER      *
* LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING     *
* FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER         *
* DEALINGS IN THE SOFTWARE.                                                   *
*                                                                             *
* This agreement shall be governed in all respects by the laws of the State   *
* of California and by the laws of the United States of America.              *
*                                                                             *
******************************************************************************/

#include <errno.h>
#include <io.h>

#include <priv/alt_file.h>

#include "altera_up_avalon_video_dma_controller.h"

#define ABS(x)	((x >= 0) ? (x) : (-(x)))

/* helper functions */
void alt_up_video_dma_draw_hline(alt_up_video_dma_dev *video_dma, register unsigned int symbol, register unsigned int x0, register unsigned int x1, register unsigned int y, int backbuffer);
void alt_up_video_dma_draw_vline(alt_up_video_dma_dev *video_dma, register unsigned int symbol, register unsigned int x, register unsigned int y0, register unsigned int y1, int backbuffer);
void alt_up_video_dma_draw_rectangle(alt_up_video_dma_dev *video_dma, register unsigned int symbol, register unsigned int x0, register unsigned int y0, register unsigned int x1, register unsigned int y1, int backbuffer);
void alt_up_video_dma_draw_helper(register unsigned int buffer_start, register unsigned int offset, register unsigned int symbol, register unsigned int data_width);

alt_up_video_dma_dev* alt_up_video_dma_open_dev(const char* name) {
  // find the device from the device list 
  // (see altera_hal/HAL/inc/priv/alt_file.h 
  // and altera_hal/HAL/src/alt_find_dev.c 
  // for details)
  alt_up_video_dma_dev *dev = (alt_up_video_dma_dev*)alt_find_dev(name, &alt_dev_list);

  return dev;
}

int alt_up_video_dma_ctrl_set_bb_addr(alt_up_video_dma_dev *video_dma, unsigned int new_address)
/* This function changes the memory address for the back buffer. */
{
	IOWR_32DIRECT(video_dma->base, 4, new_address);
	video_dma->back_buffer_start_address = IORD_32DIRECT(video_dma->base, 4);
	return 0;
}

int alt_up_video_dma_ctrl_swap_buffers(alt_up_video_dma_dev *video_dma)
/* This function swaps the front and back buffers. At the next refresh cycle the back buffer will be drawn on the screen
 * and will become the front buffer. */
{
	register unsigned int temp = video_dma->back_buffer_start_address;
	IOWR_32DIRECT(video_dma->base, 0, 1);
	video_dma->back_buffer_start_address = video_dma->buffer_start_address;
	video_dma->buffer_start_address = temp;
	return 0;
}

int alt_up_video_dma_ctrl_check_swap_status(alt_up_video_dma_dev *video_dma)
/* This function checks if the buffer swap has occured. Since the buffer swap only happens after an entire screen is drawn,
 * it is important to wait for this function to return 0 before proceeding to draw on either buffer. When both front and the back buffers
 * have the same address calling the alt_up_video_dma_swap_buffers(...) function and then waiting for this function to return 0, causes your program to
 * wait for the screen to refresh. */
{
	return (IORD_32DIRECT(video_dma->base, 12) & 0x1);
}

int alt_up_video_dma_draw(alt_up_video_dma_dev *video_dma, unsigned int symbol, unsigned int x, unsigned int y, int backbuffer)
/* This function draws a pixel to either the front or back buffer. */
{
	register unsigned int addr;
	register unsigned int offset = 0;
	register unsigned int limit_x = video_dma->x_resolution;
	register unsigned int limit_y = video_dma->y_resolution;
	register unsigned int local_x = x;
	register unsigned int local_y = y;
	register unsigned int local_symbol = symbol;

	// boundary check
	if (local_x >= limit_x || local_y >= limit_y )
		return -1;

	/* Set up the start address of the chosen frame buffer. */
	if (backbuffer == 1)
		addr = video_dma->back_buffer_start_address;
	else
		addr = video_dma->buffer_start_address;

	/* Check the mode VGA Pixel Buffer is using. */
	if (video_dma->addressing_mode == ALT_UP_VIDEO_DMA_XY_ADDRESS_MODE) {
		/* For X-Y addressing mode, the address format is | unused | Y | X |. So shift bits for coordinates X and Y into their respective locations. */
		offset += ((local_x & video_dma->x_coord_mask) << video_dma->x_coord_offset);
		offset += ((local_y & video_dma->y_coord_mask) << video_dma->y_coord_offset);

	} else {
		/* In a consecutive addressing mode, the pixels are stored in consecutive memory locations. So the address of a pixel at (x,y) can be computed as
		 * (y*x_resolution + x).*/
		offset += ((x & video_dma->x_coord_mask) << video_dma->x_coord_offset);
		offset += (((y & video_dma->y_coord_mask) * video_dma->x_resolution) << video_dma->x_coord_offset);
	}

	/* Now, depending on the color depth, write the pixel color to the specified memory location. */
	alt_up_video_dma_draw_helper(addr, offset, local_symbol, video_dma->data_width);

	return 0;
}

int alt_up_video_dma_draw_box(alt_up_video_dma_dev *video_dma, unsigned int symbol, unsigned int x0, unsigned int y0, unsigned int x1, unsigned int y1, int backbuffer, int fill)
/* This function draws a box. */
{
	register unsigned int addr;
	register unsigned int limit_x = video_dma->x_resolution;
	register unsigned int limit_y = video_dma->y_resolution;
	register unsigned int temp;
	register unsigned int l_x = x0;
	register unsigned int r_x = x1;
	register unsigned int t_y = y0;
	register unsigned int b_y = y1;
	register unsigned int local_symbol = symbol;
	
	/* Check coordinates */
	if (l_x > r_x)
	{
		temp = l_x;
		l_x = r_x;
		r_x = temp;
	}
	if (t_y > b_y)
	{
		temp = t_y;
		t_y = b_y;
		b_y = temp;
	}
	if ((l_x >= limit_x) || (t_y >= limit_y))
	{
		/* Drawing outside of the window, so don't bother. */
		return -1;
	}
	/* Clip the box and draw only within the confines of the screen. */
	if (r_x >= limit_x)
	{
		r_x = limit_x - 1;
	}
	if (b_y >= limit_y)
	{
		b_y = limit_y - 1;
	}

	if (fill == 0) {
		alt_up_video_dma_draw_rectangle(video_dma, local_symbol, l_x, t_y, r_x, b_y, backbuffer);
		return 0;
	}

	/* Set up the start address of the chosen frame buffer. */
	if (backbuffer == 1)
		addr = video_dma->back_buffer_start_address;
	else
		addr = video_dma->buffer_start_address;

	/* Draw the box using one of the addressing modes. */
	if (video_dma->addressing_mode == ALT_UP_VIDEO_DMA_XY_ADDRESS_MODE) {
		/* Draw a box of a given color on the screen using the XY addressing mode. */
		register unsigned int x,y;
		register unsigned int offset_y;
		offset_y = video_dma->y_coord_offset;
		addr = addr + (t_y << offset_y);
		
		/* This portion of the code is purposefully replicated. This is because having a test for
		 * the mode would unnecessarily slow down the drawing of a box. */
		if (video_dma->data_width == 1) {
			for (y = t_y; y <= b_y; y++)
			{
				for (x = l_x; x <= r_x; x++)
				{
					IOWR_8DIRECT(addr, x, local_symbol);
				}
				addr = addr + (1 << offset_y);
			}
		} else if (video_dma->data_width == 2) {
			for (y = t_y; y <= b_y; y++)
			{
				for (x = l_x; x <= r_x; x++)
				{
					IOWR_16DIRECT(addr, x << 1, local_symbol);
				}
				addr = addr + (1 << offset_y);
			}
		}
		else
		{
			for (y = t_y; y <= b_y; y++)
			{
				for (x = l_x; x <= r_x; x++)
				{
					IOWR_32DIRECT(addr, x << 2, local_symbol);
				}
				addr = addr + (1 << offset_y);
			}
		}
	} else {
		/* Draw a box of a given color on the screen using the linear addressing mode. */
		register unsigned int x,y;

		/* This portion of the code is purposefully replicated. This is because having a test for
		 * the mode would unnecessarily slow down the drawing of a box. */
		if (video_dma->data_width == 1) {
			addr = addr + t_y * limit_x;
			for (y = t_y; y <= b_y; y++)
			{
				for (x = l_x; x <= r_x; x++)
				{
					IOWR_8DIRECT(addr, x, local_symbol);
				}
				addr = addr + limit_x;
			}

		} else if (video_dma->data_width == 2) {
			limit_x = limit_x << 1;
			addr = addr + t_y * limit_x;
			for (y = t_y; y <= b_y; y++)
			{
				for (x = l_x; x <= r_x; x++)
				{
					IOWR_16DIRECT(addr, x << 1, local_symbol);
				}
				addr = addr + limit_x;
			}

		} else {
			limit_x = limit_x << 2;
			addr = addr + t_y * limit_x;
			for (y = t_y; y <= b_y; y++)
			{
				for (x = l_x; x <= r_x; x++)
				{
					IOWR_32DIRECT(addr, x << 2, local_symbol);
				}
				addr = addr + limit_x;
			}
		}
	}
	return 0;
}

int alt_up_video_dma_draw_line(alt_up_video_dma_dev *video_dma, unsigned int symbol, unsigned int x0, unsigned int y0, unsigned int x1, unsigned int y1, int backbuffer)
/* This function draws a line between points (x0, y0) and (x1, y1). */
{
	register int x_0 = x0;
	register int y_0 = y0;
	register int x_1 = x1;
	register int y_1 = y1;
	register char steep = (ABS(y_1 - y_0) > ABS(x_1 - x_0)) ? 1 : 0;
	register int deltax, deltay, error, ystep, x, y;
	register unsigned int data_width = video_dma->data_width;
	register unsigned int line_symbol = symbol;
	register unsigned int buffer_start;
	register unsigned int offset;
	register unsigned int x_coord_offset = video_dma->x_coord_offset;
	register int line_size = (video_dma->addressing_mode == ALT_UP_VIDEO_DMA_XY_ADDRESS_MODE) ? (1 << (video_dma->y_coord_offset-video_dma->x_coord_offset)) : video_dma->x_resolution;

	if (backbuffer == 1)
		buffer_start = video_dma->back_buffer_start_address;
	else
		buffer_start = video_dma->buffer_start_address;

	/* Preprocessing inputs */
	if (steep > 0) {
		// Swap x_0 and y_0
		error = x_0;
		x_0 = y_0;
		y_0 = error;
		// Swap x_1 and y_1
		error = x_1;
		x_1 = y_1;
		y_1 = error;
	}
	if (x_0 > x_1) {
		// Swap x_0 and x_1
		error = x_0;
		x_0 = x_1;
		x_1 = error;
		// Swap y_0 and y_1
		error = y_0;
		y_0 = y_1;
		y_1 = error;
	}

	if ((x_0 < 0) || (x_1 >= video_dma->x_resolution))
		return -1;
	if ((y_0 < 0) || (y_0 >= video_dma->y_resolution))
		return -1;
	if ((y_1 < 0) || (y_1 >= video_dma->y_resolution))
		return -1;

	/* Setup local variables */
	deltax = x_1 - x_0;
	deltay = ABS(y_1 - y_0);
	error = -(deltax / 2); 
	y = y_0;
	if (y_0 < y_1)
		ystep = 1;
	else
		ystep = -1;

	/* Draw a line - either go along the x axis (steep = 0) or along the y axis (steep = 1). The code is replicated to
	 * compile well on low optimization levels. */
	if (steep == 1)
	{
		for (x=x_0; x <= x_1; x++) {
			offset = ((line_size * y + x) << x_coord_offset);
			alt_up_video_dma_draw_helper(buffer_start, offset, line_symbol, data_width);
			error = error + deltay;
			if (error > 0) {
				y = y + ystep;
				error = error - deltax;
			}
		}
	}
	else
	{
		for (x=x_0; x <= x_1; x++) {
			offset = ((line_size * x + y) << x_coord_offset);
			alt_up_video_dma_draw_helper(buffer_start, offset, line_symbol, data_width);
			error = error + deltay;
			if (error > 0) {
				y = y + ystep;
				error = error - deltax;
			}
		}
	}
	return 0;
}

int alt_up_video_dma_draw_string(alt_up_video_dma_dev *video_dma, const char *ptr, unsigned int x, unsigned int y, int backbuffer) {
	register unsigned int addr;

	/* Set up the start address of the chosen frame buffer. */
	if (backbuffer == 1)
		addr = video_dma->back_buffer_start_address;
	else
		addr = video_dma->buffer_start_address;

	// boundary check
	if (x >= video_dma->x_resolution || y >= video_dma->y_resolution )
		return -1;
	
	unsigned int offset = 0;
	offset = (y << video_dma->y_coord_offset) + (x << video_dma->x_coord_offset);

	if (video_dma->data_width == 1) {
		while ( *ptr )
		{
			if (x >= video_dma->x_resolution)
				return -1;
			IOWR_8DIRECT(addr, offset, *ptr);
			++ptr;
			++x;
			++offset;
		}
	} else if (video_dma->data_width == 2) {
		while ( *ptr )
		{
			if (x >= video_dma->x_resolution)
				return -1;
			IOWR_16DIRECT(addr, offset, *ptr);
			++ptr;
			++x;
			offset += 2;
		}
	} else {
		while ( *ptr )
		{
			if (x >= video_dma->x_resolution)
				return -1;
			IOWR_8DIRECT(addr, offset, *ptr);
			++ptr;
			++x;
			offset += 4;
		}
	}
	return 0;
}

void alt_up_video_dma_screen_clear(alt_up_video_dma_dev *video_dma, int backbuffer) {
	alt_up_video_dma_screen_fill(video_dma, 0, backbuffer);
}

void alt_up_video_dma_screen_fill(alt_up_video_dma_dev *video_dma, unsigned int symbol, int backbuffer) {
	alt_up_video_dma_draw_box(video_dma, symbol, 0, 0, video_dma->x_resolution, video_dma->y_resolution, backbuffer, 1);
}

/* helper functions */
void alt_up_video_dma_draw_hline(alt_up_video_dma_dev *video_dma, register unsigned int symbol, 
		register unsigned int x0, register unsigned int x1, register unsigned int y, 
		int backbuffer)
/* This method draws a vertical line between points (x,y0) and (x,y1). This 
 * method is faster than using the line method because we know the direction 
 * of the line. */
{
	register unsigned int addr = 0;
	register unsigned int x;

	/* Set up the start address of the chosen frame buffer. */
	if (backbuffer == 1)
		addr = video_dma->back_buffer_start_address;
	else
		addr = video_dma->buffer_start_address;

	/* Calculate the y offset based on the addressing modes. */
	if (video_dma->addressing_mode == ALT_UP_VIDEO_DMA_XY_ADDRESS_MODE) {
		addr += (y << video_dma->y_coord_offset);
	} else {
		addr += ((y * video_dma->x_resolution) << video_dma->x_coord_offset);
	}

	/* This portion of the code is purposefully replicated. This is because having a text for
	 * the mode would unnecessarily slow down the drawing of a horizontal line. */
	if (video_dma->data_width == 1) {
		for (x = x0; x <= x1; x++)
		{
			IOWR_8DIRECT(addr, x, symbol);
		}
	} else if (video_dma->data_width == 2) {
		for (x = x0; x <= x1; x++)
		{
			IOWR_16DIRECT(addr, x << 1, symbol);
		}
	}
	else
	{
		for (x = x0; x <= x1; x++)
		{
			IOWR_32DIRECT(addr, x << 2, symbol);
		}
	}
}

void alt_up_video_dma_draw_vline(alt_up_video_dma_dev *video_dma, register unsigned int symbol, 
		register unsigned int x, register unsigned int y0, register unsigned int y1, 
		int backbuffer)
/* This method draws a vertical line between points (x,y0) and (x,y1). This 
 * method is faster than using the line method because we know the direction 
 * of the line. */
{
	register unsigned int addr = 0;
	register unsigned int offset = 0;
	register unsigned int y;
	register unsigned int y_inc;

	/* Set up the start address of the chosen frame buffer. */
	if (backbuffer == 1)
		addr = video_dma->back_buffer_start_address;
	else
		addr = video_dma->buffer_start_address;

	addr += (x << video_dma->x_coord_offset);

	/* Calculate the y increment based on the addressing modes. */
	if (video_dma->addressing_mode == ALT_UP_VIDEO_DMA_XY_ADDRESS_MODE) {
		y_inc = (1 << video_dma->y_coord_offset);
	} else {
		y_inc = (video_dma->x_resolution << video_dma->x_coord_offset);
	}

	addr += y0 * y_inc;

	/* This portion of the code is purposefully replicated. This is because having a text for
	 * the mode would unnecessarily slow down the drawing of a horizontal line. */
	if (video_dma->data_width == 1) {
		for (y = y0; y <= y1; y++)
		{
			IOWR_8DIRECT(addr, offset, symbol);
			offset += y_inc;
		}
	} else if (video_dma->data_width == 2) {
		for (y = y0; y <= y1; y++)
		{
			IOWR_16DIRECT(addr, offset, symbol);
			offset += y_inc;
		}
	}
	else
	{
		for (y = y0; y <= y1; y++)
		{
			IOWR_32DIRECT(addr, offset, symbol);
			offset += y_inc;
		}
	}
}

void alt_up_video_dma_draw_rectangle(alt_up_video_dma_dev *video_dma, register unsigned int symbol, 
		register unsigned int x0, register unsigned int y0, register unsigned int x1, register unsigned int y1, 
		int backbuffer)
{
	alt_up_video_dma_draw_hline(video_dma, symbol, x0, x1, y0, backbuffer);
	alt_up_video_dma_draw_hline(video_dma, symbol, x0, x1, y1, backbuffer);
	alt_up_video_dma_draw_vline(video_dma, symbol, x0, y0, y1, backbuffer);
	alt_up_video_dma_draw_vline(video_dma, symbol, x1, y0, y1, backbuffer);
}

void alt_up_video_dma_draw_helper(register unsigned int buffer_start, register unsigned int offset, 
		register unsigned int symbol, register unsigned int data_width)
/* This is a helper function that draws a symbol at a given offset from the base address. 
 * Note that no boundary checks are made, so drawing off-screen may cause unpredictable side effects. */
{
	if (data_width == 1)
		IOWR_8DIRECT(buffer_start, offset, symbol);
	else if (data_width == 2)
		IOWR_16DIRECT(buffer_start, offset, symbol);
	else
		IOWR_32DIRECT(buffer_start, offset, symbol);
}

