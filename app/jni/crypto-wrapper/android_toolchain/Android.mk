MODULE_PATH := $(call my-dir)/..
###################
# OpenSSL-wrapper #
###################
include $(CLEAR_VARS)
LOCAL_PATH := $(MODULE_PATH)

# Warning: cannot be named the same as the directory for unknown reason
LOCAL_MODULE := openssl-wrapper

# OpenSSL includes
OPENSSL_SRC_DIR := $(LOCAL_PATH)/../openssl/sources
LOCAL_C_INCLUDES += $(OPENSSL_SRC_DIR)/include

# Include self headers
LOCAL_C_INCLUDES += $(LOCAL_PATH)/include

# Source files
JNI_SRC_DIR := src
LOCAL_SRC_FILES := $(JNI_SRC_DIR)/sha256.c $(JNI_SRC_DIR)/crypto_scrypt-nosse.c $(JNI_SRC_DIR)/crypto_utils.c $(JNI_SRC_DIR)/crypto_wrapper.cpp
		
LOCAL_LDLIBS += -llog -ldl

# LOCAL_CPPFLAGS += -std=c++11
# LOCAL_CFLAGS := -std=gnu++11

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


LOCAL_STATIC_LIBRARIES += libgcc
include $(BUILD_SHARED_LIBRARY)

