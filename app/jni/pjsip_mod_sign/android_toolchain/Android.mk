MODULE_PATH := $(call my-dir)/..

###################
# pjsip module reghandler  #
###################
include $(CLEAR_VARS)
LOCAL_PATH := $(MODULE_PATH)

LOCAL_MODULE := pjsip_mod_sign

PJ_ROOT_DIR := $(LOCAL_PATH)/../pjsip/sources
PJ_ANDROID_ROOT_DIR := $(LOCAL_PATH)/../pjsip/android_sources

#Include PJ interfaces
LOCAL_C_INCLUDES += $(PJ_ROOT_DIR)/pjsip/include $(PJ_ROOT_DIR)/pjlib-util/include \
			$(PJ_ROOT_DIR)/pjlib/include/ $(PJ_ROOT_DIR)/pjmedia/include \
			$(PJ_ROOT_DIR)/pjnath/include $(PJ_ROOT_DIR)/pjlib/include   \
			$(LOCAL_PATH)/../openssl/sources/include

LOCAL_C_INCLUDES += $(MODULE_PATH)/include

LOCAL_CFLAGS := $(MY_PJSIP_FLAGS)

LOCAL_SRC_FILES := src/pjsip_sign.cpp

LOCAL_LDLIBS += -llog -ldl
LOCAL_STATIC_LIBRARIES += libgcc

include $(BUILD_STATIC_LIBRARY)

