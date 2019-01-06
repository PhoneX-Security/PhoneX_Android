LOCAL_PATH := $(call my-dir)
JNI_PATH := $(LOCAL_PATH)


USE_FIXED_POINT := 0
ifeq ($(TARGET_ARCH_ABI),$(filter $(TARGET_ARCH_ABI),armeabi armeabi-v7a))
USE_FIXED_POINT := 1
endif

## C++ 11 compilation
#LOCAL_CPPFLAGS = -std=c++0x
#LOCAL_CFLAGS += -mllvm -sub -mllvm -fla -mllvm -bcf

# Add a static target for libgcc
#include $(CLEAR_VARS)
#LOCAL_PATH := /
#LOCAL_MODULE    := libgcc 
#LOCAL_SRC_FILES := $(TARGET_LIBGCC)
#LOCAL_SHARED_LIBRARIES += libc 
#pj_opus_codec
#include $(PREBUILT_STATIC_LIBRARY)

# Include all submodules declarations
include $(JNI_PATH)/pjsip/android_toolchain/Android.mk
include $(JNI_PATH)/webrtc/android_toolchain/Android.mk
include $(JNI_PATH)/amr-stagefright/android_toolchain/Android.mk
include $(JNI_PATH)/silk/android_toolchain/Android.mk
include $(JNI_PATH)/g726/android_toolchain/Android.mk
include $(JNI_PATH)/g729/android_toolchain/Android.mk
include $(JNI_PATH)/codec2/android_toolchain/Android.mk
include $(JNI_PATH)/opus/android_toolchain/Android.mk
include $(JNI_PATH)/fdk_aac/android_toolchain/Android.mk
#include $(JNI_PATH)/zrtp4pj/android_toolchain/Android.mk
include $(JNI_PATH)/zrtpcpp/android_toolchain/Android.mk
include $(JNI_PATH)/zrtpglue/android_toolchain/Android.mk

ifeq ($(MY_USE_TLS),1)
NDK_PROJECT_PATH := $(JNI_PATH)/openssl/sources/
include $(JNI_PATH)/openssl/Android.mk
#include $(JNI_PATH)/openssl/android_toolchain/Android.mk
NDK_PROJECT_PATH := $(JNI_PATH)
endif

include $(JNI_PATH)/third_party/android_toolchain/libyuv/Android.mk
include $(JNI_PATH)/libvpx/android_toolchain/Android.mk
include $(JNI_PATH)/swig-glue/android_toolchain/Android.mk
include $(JNI_PATH)/sipstack-wrapper/android_toolchain/Android.mk
include $(JNI_PATH)/pjsip_mod_reghandler/android_toolchain/Android.mk
include $(JNI_PATH)/pjsip_mod_sign/android_toolchain/Android.mk
#include $(JNI_PATH)/pjsip_mod_sipclf/android_toolchain/Android.mk
#include $(JNI_PATH)/pjsip_mod_earlylock/android_toolchain/Android.mk
include $(JNI_PATH)/crash/android_toolchain/Android.mk
include $(JNI_PATH)/libcutils/Android.mk
include $(JNI_PATH)/libgccdemangle/Android.mk
include $(JNI_PATH)/libcorkscrew/Android.mk

include $(JNI_PATH)/crypto-wrapper/android_toolchain/Android.mk

