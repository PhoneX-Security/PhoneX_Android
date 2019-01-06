#!/bin/bash
ldir=$1
if [[ "x$ldir" == "x" || ! -d $ldir ]]; then
	echo "Usage: $0 logfiledir"
	exit 1
fi

cd $ldir
for i in `find . -mindepth 1 -type f -name '*.logcat.gz'`;do  
	gzip -dc $i > ${i%.gz}; 
done


echo "Finished"

