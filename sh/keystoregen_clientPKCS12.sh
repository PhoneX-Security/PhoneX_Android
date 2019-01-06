#!/bin/bash
CLASSPATH=$CLASSPATH:/home/ph4r05/Download/bcprov-jdk16-1.46.jar
#CLASSPATH=$CLASSPATH:/home/ph4r05/workspace/PV079_01/lib/bcprov-jdk15on-147.jar
#CLASSPATH=$CLASSPATH:/usr/share/java/bcprov-1.46.jar

/bin/rm res/raw/client*

/usr/java/latest/bin/keytool \
 	-v -importkeystore \
	-alias 1 \
        -srcstoretype PKCS12 \
	-srckeystore '../ca/clientY.p12'	 \
	-destkeystore res/raw/client1_p.bks \
	-srcstorepass chageit \
        -destkeypass chageit \
        -deststorepass chageit \
	-deststoretype PKCS12 \
	-provider org.bouncycastle.jce.provider.BouncyCastleProvider \
        -providerpath /home/ph4r05/workspace/Phoenix2/libs/scprov-jdk15on-1.47.0.2.jar
#	-providerpath  /usr/share/java/bcprov-1.46.jar

/usr/java/latest/bin/keytool \
      -importkeystore \
      -v \
      -alias 1 \
      -srckeystore '../ca/clientY.p12' \
      -srcstoretype PKCS12 \
      -destkeystore res/raw/client_p.bks \
      -storetype PKCS12 \
      -srcstorepass chageit \
      -destkeypass chageit \
      -deststorepass chageit \
      -provider org.bouncycastle.jce.provider.BouncyCastleProvider \
      -providerpath /home/ph4r05/Download/bcprov-jdk16-1.46.jar
#     -providerpath /home/ph4r05/workspace/Phoenix2/libs/scprov-jdk15on-1.47.0.2.jar
##      -providerpath /usr/share/java/bcprov-1.46.jar
##      -alias client \




# spongy castle /home/ph4r05/workspace/Phoenix2/libs/scprov-jdk15on-1.47.0.2.jar

# not all
#      -trustcacerts \

# old provider      
#      -provider org.bouncycastle.jce.provider.BouncyCastleProvider \
# new provider
#      -provider org.spongycastle.jce.provider.BouncyCastleProvider \

# old version of bcprov      
#      -providerpath /home/ph4r05/workspace/PV079_01/lib/bcprov-jdk15on-147.jar  /usr/share/java/bcprov.jar \
