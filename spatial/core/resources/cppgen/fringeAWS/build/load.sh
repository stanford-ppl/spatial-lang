# TODO:
# See comments in FringeContextAWS.h -- eventually this script will be done within fringe load()
# This script can also parse args and call sudo ./Top directly as its final step

# sudo fpga-clear-local-image -S 0
# sleep 25
echo 'Loading image...'
sudo fpga-load-local-image -S 0 -I agfi-[PLACE ID HERE]
echo 'Waiting 15 seconds for Status to change from "busy" to "loaded"...'
sleep 10
echo 'Checking for "loaded"...'
sudo fpga-describe-local-image -S 0 -R -H
# echo 'Resetting EDMA driver...'
# sudo rmmod edma-drv && sudo insmod /home/centos/src/project_data/aws-fpga/sdk/linux_kernel_drivers/edma/edma-drv.ko
echo 'Application is now ready to run using "sudo ./Top <args>" (make sure not to forget sudo)'
