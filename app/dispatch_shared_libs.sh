#!/bin/sh

move_generic_lib() {
	echo -n "Moving $1.so to $2 project ... "
	libs_files=$(ls libs/*/${1}.so 2> /dev/null | wc -l | sed -e 's/^[ \t]*//')
	if [ "$libs_files" != "0" ]; then
		for lib_folder in libs/*; do
			if [ -d ${lib_folder} ]; then
				mkdir -p ../${2}/${lib_folder};
				mv ${lib_folder}/${1}.so ../${2}/${lib_folder}/${1}.so;
			fi
		done
		echo "[OK]";
	else
		echo "[--] - plugin not built"
	fi
}

move_lib() {
	move_generic_lib libpj_${1}_codec PhoneX${2}
}

move_lib "g7221" "Audio"
move_lib "codec2" "Audio"
move_lib "opus" "Audio"
move_lib "g726" "Audio"
move_lib "aac" "Audio"
move_lib "g729" "AudioG729"
move_generic_lib "libcrypto" "PhoneXCrypto"
move_generic_lib "libssl" "PhoneXCrypto"
move_generic_lib "libpj_video_android" "PhoneXVideo"
move_generic_lib "libpj_screen_capture_android" "PhoneXVideo"
move_generic_lib "libpj_vpx" "PhoneXVideo"
