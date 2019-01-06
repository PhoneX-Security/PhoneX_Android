MODULE_PATH := $(call my-dir)/..

##################
#   PJSIP lib    #
##################
include $(CLEAR_VARS)
LOCAL_PATH := $(MODULE_PATH)

LOCAL_MODULE := sipstackjni

PJ_ROOT_DIR := $(LOCAL_PATH)/../pjsip/sources
PJ_ANDROID_ROOT_DIR := $(LOCAL_PATH)/../pjsip/android_sources

OPENSSL_SRC_DIR := $(LOCAL_PATH)/../openssl/sources

#OpenSSL includes
LOCAL_C_INCLUDES += $(OPENSSL_SRC_DIR)/include 

#Include PJ interfaces
LOCAL_C_INCLUDES += $(PJ_ROOT_DIR)/pjsip/include $(PJ_ROOT_DIR)/pjlib-util/include \
			$(PJ_ROOT_DIR)/pjlib/include/ $(PJ_ROOT_DIR)/pjmedia/include \
			$(PJ_ROOT_DIR)/pjnath/include $(PJ_ROOT_DIR)/pjlib/include
#Include PJ android interfaces
LOCAL_C_INCLUDES += $(PJ_ANDROID_ROOT_DIR)/pjmedia/include/pjmedia-audiodev
LOCAL_C_INCLUDES += $(PJ_ANDROID_ROOT_DIR)/pjmedia/include/pjmedia-videodev

#Include pjsip pjlip timers
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../pjsip/android_sources/pjlib/include

# Include WebRTC 
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../webrtc/pj_sources

# Include ZRTP interface 
#LOCAL_C_INCLUDES += $(LOCAL_PATH)/../zrtp4pj/sources/zsrtp/include $(LOCAL_PATH)/../zrtp4pj/sources/zsrtp/zrtp/zrtp
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../zrtpglue/sources/zsrtp/include \
		    $(LOCAL_PATH)/../zrtpcpp/sources/zrtp/zrtp

# Include Crash
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../crash/include

# Include swig wrapper headers
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../swig-glue

# Include self headers
LOCAL_C_INCLUDES += $(LOCAL_PATH)/include


LOCAL_CFLAGS := $(MY_PJSIP_FLAGS) -rdynamic -g -funwind-tables

JNI_SRC_DIR := src

LOCAL_SRC_FILES := $(JNI_SRC_DIR)/pjsua_jni_addons.cpp \
	$(JNI_SRC_DIR)/q850_reason_parser.c \
	$(JNI_SRC_DIR)/zrtp_android.c \
	$(JNI_SRC_DIR)/zrtp_android_callback.cpp \
	$(JNI_SRC_DIR)/PjTone.cpp \
	$(JNI_SRC_DIR)/PjToneRingback.cpp \
	$(JNI_SRC_DIR)/PjToneBusy.cpp \
	$(JNI_SRC_DIR)/PjToneError.cpp \
	$(JNI_SRC_DIR)/PjToneZRTPOK.cpp \
	$(JNI_SRC_DIR)/pjsip_opus_sdp_rewriter.c \
	$(JNI_SRC_DIR)/android_logger.c \
	$(JNI_SRC_DIR)/audio_codecs.c \
	$(JNI_SRC_DIR)/sipstack_codecs_utils.c 


LOCAL_LDLIBS += -llog -ldl


ifeq ($(MY_USE_AMR),1)
	LOCAL_C_INCLUDES += $(LOCAL_PATH)/../amr-stagefright/pj_sources
	LOCAL_STATIC_LIBRARIES += pj_amr_stagefright_codec
endif
# Pjsip basics
LOCAL_STATIC_LIBRARIES += swig-glue pjsip pjmedia swig-glue pjnath pjlib-util pjlib resample srtp 

# Pjsip modules
LOCAL_STATIC_LIBRARIES += pjsip_mod_reghandler
LOCAL_STATIC_LIBRARIES += pjsip_mod_sign
LOCAL_STATIC_LIBRARIES += crash
#LOCAL_STATIC_LIBRARIES += pjsip_mod_sipclf
#LOCAL_STATIC_LIBRARIES += pjsip_mod_earlylock

ifeq ($(MY_USE_ILBC),1)
	LOCAL_STATIC_LIBRARIES += ilbc
endif
ifeq ($(MY_USE_GSM),1)
	LOCAL_STATIC_LIBRARIES += gsm
endif
ifeq ($(MY_USE_SPEEX),1)
	LOCAL_STATIC_LIBRARIES += speex
endif
ifeq ($(MY_USE_AMR),1)
	LOCAL_STATIC_LIBRARIES += android_dyn_opencore
endif
ifeq ($(MY_USE_SILK),1)
	LOCAL_STATIC_LIBRARIES += pj_silk_codec
endif
#ifeq ($(MY_USE_OPUS),1)
#	LOCAL_STATIC_LIBRARIES += pj_opus_codec
#endif

ifeq ($(MY_USE_ZRTP),1)
ifeq ($(MY_USE_DYNAMIC_ZRTPGLUE),1)
    LOCAL_CFLAGS += -DZRTP_DYNAMIC_LINKING=1
else
	LOCAL_STATIC_LIBRARIES += zrtpglue
	LOCAL_SHARED_LIBRARIES += libzrtpcpp
endif
endif

ifeq ($(MY_USE_TLS),1)
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
endif


ifeq ($(MY_USE_WEBRTC),1)

	
# Codecs wrap
	LOCAL_STATIC_LIBRARIES +=  pj_webrtc_codec libwebrtc_audio_coding libwebrtc_cng libwebrtc_vad libwebrtc_neteq libwebrtc_resampler
	
#Codecs implementations 
	LOCAL_STATIC_LIBRARIES += libwebrtc_ilbc libwebrtc_g711 libwebrtc_g722 libwebrtc_pcm16b
	#libwebrtc_pcm16b
	#libwebrtc_g722 

# AEC
ifeq ($(USE_FIXED_POINT),1)
	LOCAL_STATIC_LIBRARIES += libwebrtc_isacfix libwebrtc_aecm
	
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
	# We add neon libs that will be used only if neon autodetected by webrtc
	LOCAL_STATIC_LIBRARIES += libwebrtc_isacfix_neon libwebrtc_aecm_neon 
endif

else
	LOCAL_STATIC_LIBRARIES += libwebrtc_isac libwebrtc_aec
endif

#NS
	LOCAL_STATIC_LIBRARIES += libwebrtc_ns

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)	
	LOCAL_STATIC_LIBRARIES += libwebrtc_ns_neon
endif
	
#Common
	LOCAL_STATIC_LIBRARIES += libwebrtc_apm_utility libwebrtc_system_wrappers libwebrtc_spl 

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)	
	LOCAL_STATIC_LIBRARIES += libwebrtc_spl_neon
endif

endif

LOCAL_STATIC_LIBRARIES += libgcc

include $(BUILD_SHARED_LIBRARY)

