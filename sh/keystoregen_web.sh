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
    #add2keystore "${UDIR}/../crypto/web/web3.crt" 0
    #add2keystore "${UDIR}/../crypto/web/web2.crt" 1
    #add2keystore "${UDIR}/../crypto/web/web1.crt" 2
    #add2keystore "${UDIR}/../crypto/web/web0.crt" 3

    add2keystore "${UDIR}/../crypto/letsencrypt/isrgrootx1.pem" 4
    add2keystore "${UDIR}/../crypto/letsencrypt/lets-encrypt-x1-cross-signed.pem" 5
    add2keystore "${UDIR}/../crypto/letsencrypt/lets-encrypt-x2-cross-signed.pem" 6
    add2keystore "${UDIR}/../crypto/letsencrypt/lets-encrypt-x3-cross-signed.pem" 7
    add2keystore "${UDIR}/../crypto/letsencrypt/lets-encrypt-x4-cross-signed.pem" 8
    add2keystore "${UDIR}/../crypto/letsencrypt/letsencryptauthorityx1.pem" 9
    add2keystore "${UDIR}/../crypto/letsencrypt/letsencryptauthorityx2.pem" 10

    # For export you may use:
    # keytool  -keystore ../app/src/main/res/raw/truststore_web.bks -storetype BKS -provider org.bouncycastle.jce.provider.BouncyCastleProvider -providerpath bcprov-jdk15on-1.47.jar  -export -alias 4 -file ../crypto/web4.crt
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
	      -keystore "${UDIR}/../app/src/main/res/raw/truststore_web.bks" \
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
