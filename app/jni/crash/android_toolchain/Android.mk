MODULE_PATH := $(call my-dir)/..

###################
# native crash handler  #
###################
include $(CLEAR_VARS)
LOCAL_PATH := $(MODULE_PATH)

LOCAL_MODULE := crash

PJ_ROOT_DIR := $(LOCAL_PATH)/../pjsip/sources
PJ_ANDROID_ROOT_DIR := $(LOCAL_PATH)/../pjsip/android_sources

#Include PJ interfaces
LOCAL_C_INCLUDES += $(PJ_ROOT_DIR)/pjsip/include $(PJ_ROOT_DIR)/pjlib-util/include \
			$(PJ_ROOT_DIR)/pjlib/include/ $(PJ_ROOT_DIR)/pjmedia/include \
			$(PJ_ROOT_DIR)/pjnath/include $(PJ_ROOT_DIR)/pjlib/include

# Include self headers
LOCAL_C_INCLUDES += $(LOCAL_PATH)/include

LOCAL_CFLAGS := $(MY_PJSIP_FLAGS) -rdynamic -g -funwind-tables

JNI_SRC_DIR := $(LOCAL_PATH)/src

LOCAL_SRC_FILES := src/NativeCrashHandler.cpp 

LOCAL_LDLIBS += -llog -ldl 

LOCAL_STATIC_LIBRARIES += libgcc

#include $(BUILD_STATIC_LIBRARY)
include $(BUILD_SHARED_LIBRARY)

