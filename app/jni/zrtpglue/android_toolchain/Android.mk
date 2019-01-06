########
# ZRTP #
########

LOCAL_PATH := $(call my-dir)/../sources/zsrtp

include $(CLEAR_VARS)
LOCAL_MODULE    := zrtpglue

PJ_SRC_DIR := $(LOCAL_PATH)/../../../pjsip/sources
OPENSSL_SRC_DIR := $(LOCAL_PATH)/../../../openssl/sources

# Self includes
LOCAL_C_INCLUDES += $(LOCAL_PATH)/include \
			$(LOCAL_PATH)/srtp \
			$(LOCAL_PATH)/zsrtp \
			$(LOCAL_PATH)/../../../zrtpcpp/sources/zrtp \
			$(LOCAL_PATH)/../../../zrtpcpp/sources/zrtp/srtp \
			$(LOCAL_PATH)/../../../zrtpcpp/sources/zrtp/zrtp \
			$(LOCAL_PATH)/../../../zrtpcpp/sources/zrtp/zrtp/libzrtpcpp

# Pj includes
LOCAL_C_INCLUDES += $(PJ_SRC_DIR)/pjsip/include $(PJ_SRC_DIR)/pjlib-util/include \
			$(PJ_ROOT_DIR)/pjlib/include $(PJ_SRC_DIR)/pjmedia/include \
			$(PJ_SRC_DIR)/pjnath/include $(PJ_SRC_DIR)/pjlib/include
			
#OpenSSL includes
LOCAL_C_INCLUDES += $(OPENSSL_SRC_DIR)/include 

LOCAL_CFLAGS := $(MY_PJSIP_FLAGS) -DDYNAMIC_TIMER=1
LOCAL_LDLIBS :=  -llog -ldl

# If compiled as a shared library
ifeq ($(MY_USE_DYNAMIC_ZRTPGLUE),1)
LOCAL_SHARED_LIBRARIES += libdl liblog libzrtpcpp libsipstackjni
else

# If compiled as a static library
LOCAL_SHARED_LIBRARIES += libdl liblog libzrtpcpp
endif

LOCAL_STATIC_LIBRARIES += crypto_ec_static ssl_static crypto_static
#LOCAL_SHARED_LIBRARIES += libssl libcrypto
###### From make file

srtpobj = srtp/ZsrtpCWrapper.o
transportobj = transport_zrtp.o zrtp_timer.o
cryptobj =  $(ciphersossl) $(skeinmac) $(twofish)
# -- END OF ZRTP4PJ makefile

srtpsrc := $(srtpobj:%.o=%.cpp)
transportsrc := $(transportobj:%.o=%.c)

LOCAL_SRC_FILES += $(srtpsrc) $(transportsrc)

ifeq ($(MY_USE_DYNAMIC_ZRTPGLUE),1)
include $(BUILD_SHARED_LIBRARY)
else
include $(BUILD_STATIC_LIBRARY)
endif
