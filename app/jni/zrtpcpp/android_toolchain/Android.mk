########
# ZRTP #
########

LOCAL_PATH := $(call my-dir)/../sources/zrtp

include $(CLEAR_VARS)
LOCAL_MODULE    := zrtpcpp

PJ_SRC_DIR := $(LOCAL_PATH)/../../../pjsip/sources
OPENSSL_SRC_DIR := $(LOCAL_PATH)/../../../openssl/sources

# Self includes
LOCAL_C_INCLUDES += $(LOCAL_PATH)         \
			$(LOCAL_PATH)/zrtp            \
			$(LOCAL_PATH)/zrtp/libzrtpcpp \
			$(LOCAL_PATH)/srtp

# Pj includes
LOCAL_C_INCLUDES += $(PJ_SRC_DIR)/pjsip/include $(PJ_SRC_DIR)/pjlib-util/include \
			$(PJ_ROOT_DIR)/pjlib/include $(PJ_SRC_DIR)/pjmedia/include \
			$(PJ_SRC_DIR)/pjnath/include $(PJ_SRC_DIR)/pjlib/include
			
#OpenSSL includes
LOCAL_C_INCLUDES += $(OPENSSL_SRC_DIR)/include 

LOCAL_CFLAGS := $(MY_PJSIP_FLAGS) -DDYNAMIC_TIMER=1
LOCAL_LDLIBS :=  -llog -ldl
LOCAL_SHARED_LIBRARIES += libdl liblog

ifeq ($(MY_USE_STATIC_SSL),1)
# This is to do builds with full openssl built in.
# Not use unless you know what you do and create a ssl_static target in openssl lib
	LOCAL_STATIC_LIBRARIES += ssl_static crypto_static
	LOCAL_LDLIBS += -lz
else
# Normal mainstream users mode
	LOCAL_STATIC_LIBRARIES += crypto_ec_static
	LOCAL_SHARED_LIBRARIES += libssl libcrypto
endif
###### From make file

ciphersossl = srtp/crypto/openssl/SrtpSymCrypto.o \
    srtp/crypto/openssl/hmac.o \
    zrtp/crypto/openssl/zrtpDH.o \
    zrtp/crypto/openssl/hmac256.o \
    zrtp/crypto/openssl/sha256.o \
    zrtp/crypto/openssl/hmac384.o \
    zrtp/crypto/openssl/sha384.o \
    zrtp/crypto/openssl/aesCFB.o

skeinmac = cryptcommon/skein.o cryptcommon/skein_block.o cryptcommon/skeinApi.o cryptcommon/macSkein.o

twofish = cryptcommon/twofish.o \
	cryptcommon/twofish_cfb.o \
    zrtp/crypto/twoCFB.o

# Gcrypt support currently not tested
#ciphersgcrypt = crypto/gcrypt/gcryptAesSrtp.o crypto/gcrypt/gcrypthmac.o \
#          crypto/gcrypt/InitializeGcrypt.o
zrtpobj = zrtp/ZrtpCallbackWrapper.o \
    zrtp/ZIDCacheFile.o \
    zrtp/ZIDRecordFile.o \
    zrtp/ZRtp.o \
    zrtp/ZrtpCrc32.o \
    zrtp/ZrtpPacketCommit.o \
    zrtp/ZrtpPacketConf2Ack.o \
    zrtp/ZrtpPacketConfirm.o \
    zrtp/ZrtpPacketDHPart.o \
    zrtp/ZrtpPacketGoClear.o \
    zrtp/ZrtpPacketClearAck.o \
    zrtp/ZrtpPacketHelloAck.o \
    zrtp/ZrtpPacketHello.o \
    zrtp/ZrtpPacketError.o \
    zrtp/ZrtpPacketErrorAck.o \
    zrtp/ZrtpPacketPingAck.o \
    zrtp/ZrtpPacketPing.o \
    zrtp/ZrtpPacketSASrelay.o \
    zrtp/ZrtpPacketRelayAck.o \
    zrtp/ZrtpStateClass.o \
    zrtp/ZrtpTextData.o \
    zrtp/ZrtpConfigure.o \
    zrtp/ZrtpCWrapper.o \
    zrtp/Base32.o
    

srtpobj = srtp/CryptoContext.o srtp/CryptoContextCtrl.o
cryptobj =  $(ciphersossl) $(skeinmac) $(twofish)
# -- END OF ZRTP4PJ makefile

zrtpsrc := $(zrtpobj:%.o=%.cpp)
cryptsrc := $(cryptobj:%.o=%.cpp)
cryptsrc := $(cryptsrc:%skein.cpp=%skein.c)
cryptsrc := $(cryptsrc:%skein_block.cpp=%skein_block.c)
cryptsrc := $(cryptsrc:%skeinApi.cpp=%skeinApi.c)
cryptsrc := $(cryptsrc:%twofish.cpp=%twofish.c)
cryptsrc := $(cryptsrc:%twofish_cfb.cpp=%twofish_cfb.c)
srtpsrc := $(srtpobj:%.o=%.cpp)

LOCAL_SRC_FILES += $(zrtpsrc) $(cryptsrc) $(srtpsrc) common/osSpecifics.c

#include $(BUILD_STATIC_LIBRARY)
include $(BUILD_SHARED_LIBRARY)
