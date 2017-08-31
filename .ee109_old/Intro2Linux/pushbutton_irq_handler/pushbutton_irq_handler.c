#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/init.h>
#include <linux/interrupt.h>
#include <asm/io.h>

MODULE_LICENSE("GPL");
MODULE_AUTHOR("EE109 Lab 1");
MODULE_DESCRIPTION("DE1SoC Pushbutton Interrupt Handler");

void * lwbridgebase;

irq_handler_t irq_handler(int irq, void *dev_id, struct pt_regs *regs)
{
    // Increment the value on the LEDs
		// TODO: Use iowrite32(ioread32(BASE_ADDR) + 1, BASE_ADDR)
		// We use ioread32(BASE_ADDR) to get the current value of 
		// LED registers. We then increment it by 1 and send it back 
		// to where the LED register is mapped.
		
    // Clear the edgecapture register (clears current interrupt)
    iowrite32(0xf,lwbridgebase+0x5c);
    
    return (irq_handler_t) IRQ_HANDLED;
}

static int __init intitialize_pushbutton_handler(void)
{
		// For every kernel module, you will need to complete a __init,
		// __exit, and an interrupt routine function.
		// Please check the README file included in this diretory to get 
		// a better sense of how the pushbuttons are mapped.
		//
		// Here are the steps you need to register an interrupt handler:
    // TODO: Get the virtual addr that maps to 0xff200000
		// Please use ioremap_nocache(START_OF_ARM2FPG, REGSPAN(0x200000)) to get 
		// a base address for ARM2FPGA bridge. Since LED registers' offset is 0x0, 
		// the LED registers get mapped at the same address as the ARM2FPGA bridge.
		
    // TODO: Set LEDs to 0x200 (the leftmost LED will turn on)
		// Please use iowrite32(0x200(the left most LED), BASE_ADDR(the address you mapped in step 1))
		
    // TODO: Clear the PIO edgecapture register (clear any pending interrupt)
		// Please use iowrite32(0xf(to clear the pending interrupts), BASE_ADDR + 0x5c(Offset of edgecapture register))
		
    // TODO: Enable IRQ generation for the 4 buttons 
		// Please use iowrite32(0xf(to enable IRQ generation), BASE_ADDR + 0x58(Offset of IRQ register for the four buttons))
    
    // Register the interrupt handler.
    return request_irq(73, (irq_handler_t)irq_handler,
               IRQF_SHARED, "pushbutton_irq_handler",
               (void *)(irq_handler));
}

static void __exit cleanup_pushbutton_handler(void)
{
    // Turn off LEDs and de-register irq handler
    iowrite32(0x0,lwbridgebase);
    free_irq(73, (void*) irq_handler);
}

module_init(intitialize_pushbutton_handler);
module_exit(cleanup_pushbutton_handler);
