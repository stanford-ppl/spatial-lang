#!bin/bash

# $1 = flags (--retime, --syncMem, etc)
# $2 = modifier ("retime", "basic", etc..)
# $3 = make target ("zynq", "aws-F1-afi")

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
sed -i "s/override val target = .*/override val target = Zynq/g" apps/src/ASPLOS2018.scala

# Create a new screen session in detached mode
screen -S $2 -X quit
screen -d -m -S $2

# annotated_list=(`cat ${SPATIAL_HOME}/apps/src/MachSuite.scala | grep "// Regression" | sed 's/object //g' | sed 's/ extends.*//g'`)
annotated_list=(
				"SW" 
				"MD_Grid" 
				"GEMM_Blocked"
				# "SPMV_CRS" 
				# "PageRank" 
				# "BlackScholes" 
				"TPCHQ6" 
				"AES" 
				"Kmeans"
				"GDA"
				"Sobel"

				# # Testing
				# "SimpleTileLoadStore"
				# "MultiplexedWriteTest"
				)

				# "LeNet" "DjinnASR" "VGG16"  
				# "KalmanFilter" "GACT" "AlexNet" "Network_in_Network" 
				# "VGG_CNN_S" "Overfeat" "Cifar10_Full"  )
for a in ${annotated_list[@]}; do
	CMD="cd $SPATIAL_HOME;rm -rf out_$2_$a;bin/spatial $a --synth --out=out_${2}_${a} $1;cd out_$2_$a;make zynq"
    # Creates a new screen window with title '$f' in existing screen session
    screen -S $2 -X screen -t $a

    # Switch terminal to bash
    screen -S $2 -p $a -X stuff "bash$(printf \\r)"
    
    # Launch $CMD in newly created screen window
    screen -S $2 -p $a -X stuff "$CMD$(printf \\r)"

    sleep 3

done

screen -S $2 -X screen -t $ac


