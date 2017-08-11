#!bin/bash

# $1 = flags (--retime, --syncMem, etc)
# $2 = modifier ("retime", "basic", etc..)

SPATIAL_HOME=`pwd`
cd ${SPATIAL_HOME}/apps
ab=`git rev-parse --abbrev-ref HEAD`
ac=`git rev-parse HEAD`
cd ../
if [[ $ab != "asplos2018" ]]; then 
	read -p "You seem to be on an apps branch that is not asplos2018.  Continue? [y/N]: " choice
	echo    # (optional) move to a new line
	case "$choice" in 
	  y|Y ) echo "Continuing..";;
	  n|N ) exit 1;;
	  * ) exit 1;;
	esac
fi


cd $SPATIAL_HOME
sed -i "s/override val target = .*/override val target = AWS_F1/g" apps/src/ASPLOS2018.scala

# Create a new screen session in detached mode
screen -S $2 -X quit
screen -d -m -S $2

# annotated_list=(`cat ${SPATIAL_HOME}/apps/src/MachSuite.scala | grep "// Regression" | sed 's/object //g' | sed 's/ extends.*//g'`)
annotated_list=(
				 "SW1" 
				# "SW2" 
				# "SW3" 
				 "MD_Grid1" 
				# "MD_Grid2" 
				# "MD_Grid3" 
				 "GEMM_Blocked1"
				# "GEMM_Blocked2"
				# "GEMM_Blocked3"
				 "TPCHQ61" 
				# "TPCHQ62" 
				# "TPCHQ63" 
				 "AES1" 
				# "AES2" 
				# "AES3" 
				 "Kmeans1"
				# "Kmeans2"
				# "Kmeans3"
				 "GDA1"
				# "GDA2"
				# "GDA3"
				 "Sobel1"
				# "Sobel2"
				# "Sobel3"
				# "SPMV_CRS" 
				# "PageRank" 
				# "BlackScholes" 
								)

				# "LeNet" "DjinnASR" "VGG16"  
				# "KalmanFilter" "GACT" "AlexNet" "Network_in_Network" 
				# "VGG_CNN_S" "Overfeat" "Cifar10_Full"  )
# annotated_list=(`cat ${SPATIAL_HOME}/apps/src/MachSuite.scala | grep "// Regression" | sed 's/object //g' | sed 's/ extends.*//g'`)

for a in ${annotated_list[@]}; do

	CMD="export SPATIAL_HOME=${SPATIAL_HOME};cd /home/mattfel/aws-fpga;source /home/mattfel/aws-fpga/hdk_setup.sh;source /home/mattfel/aws-fpga/sdk_setup.sh;cd $SPATIAL_HOME;rm -rf out_$2_$a;bin/spatial $a --synth --out=out_${2}_${a} $1;cd out_$2_$a;make aws-F1-afi"
    # Creates a new screen window with title '$f' in existing screen session
    screen -S $2 -X screen -t $a

    # Switch terminal to bash
    screen -S $2 -p $a -X stuff "bash$(printf \\r)"
    
    # Launch $CMD in newly created screen window
    screen -S $2 -p $a -X stuff "$CMD$(printf \\r)"

    sleep 3
done

screen -S $2 -X screen -t $ac
screen -S $2 -X screen -t "$1"
