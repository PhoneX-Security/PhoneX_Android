#!/bin/bash
#CLASSPATH=$CLASSPATH:/home/ph4r05/workspace/PV079_01/lib/bcprov-jdk15on-147.jar
CLASSPATH=$CLASSPATH:/home/ph4r05/workspace/Phoenix2/libs/scprov-jdk15on-1.47.0.2.jar

/usr/java/latest/bin/keytool \
      -import \
      -v \
      -alias 0 \
      -file ../ca/ca.crt \
      -keystore res/raw/truststore.bks \
      -storetype BKS \
      -provider org.spongycastle.jce.provider.BouncyCastleProvider \
      -providerpath /home/ph4r05/workspace/Phoenix2/libs/scprov-jdk15on-1.47.0.2.jar \
      -storepass chageit

# spongy castle /home/ph4r05/workspace/Phoenix2/libs/scprov-jdk15on-1.47.0.2.jar

# not all
#      -trustcacerts \

# old provider      
#      -provider org.bouncycastle.jce.provider.BouncyCastleProvider \
# new provider
#      -provider org.spongycastle.jce.provider.BouncyCastleProvider \

# old version of bcprov      
#      -providerpath /home/ph4r05/workspace/PV079_01/lib/bcprov-jdk15on-147.jar  /usr/share/java/bcprov.jar \
