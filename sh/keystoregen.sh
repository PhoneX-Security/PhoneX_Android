#!/bin/bash

#
# Define important variables here
#

UDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BCPROV="${UDIR}/bcprov-jdk15on-1.47.jar"
CLASSPATH=$CLASSPATH:$BCPROV

PASS=aeshizoo6Zee

#
# Add certificates to key store
#

function main() {
	add2keystore "${UDIR}/../crypto/ca_gi.crt" 0
	add2keystore "${UDIR}/../crypto/ca_gi_signing-ca-1.crt" 1

	add2keystore "${UDIR}/../crypto/ca_brno.crt" 2
    add2keystore "${UDIR}/../crypto/ca_brno_signing-ca-10.crt" 3
}

#
# Functions
#
function add2keystore(){
	local crt=$1
	local als=$2
	if [ X"$crt" == X"" -a ! -f $crt ]; then
		echo "certificate is not found\n"
		return
	fi

	if [ X"$als" == X"" ]; then
		echo "Alias not entered\n"
		return
	fi

	keytool \
	      -import \
	      -v \
	      -alias $als \
	      -file $crt \
	      -keystore "${UDIR}/../app/src/main/res/raw/truststore.bks" \
	      -storetype BKS \
	      -provider org.bouncycastle.jce.provider.BouncyCastleProvider \
	      -providerpath $BCPROV \
	      -storepass $PASS 
}

# just call main function
main "$@"

#################################################################################################

# not all
#      -trustcacerts \
# old provider      
#      -provider org.bouncycastle.jce.provider.BouncyCastleProvider \
# new provider
#      -provider org.spongycastle.jce.provider.BouncyCastleProvider \
