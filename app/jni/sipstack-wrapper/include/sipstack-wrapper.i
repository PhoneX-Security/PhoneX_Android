%header %{

#include "pjsua_jni_addons.h"
#include "sipstack_codecs_utils.h"
#include "zrtp_android.h"
#include "zrtp_android_callback.h"

%}

%apply (char *STRING, size_t LENGTH) { (const char entropyBuffer[], size_t entropyBufferLen) };
%feature("director") ZrtpCallback;

%include zrtp_android_callback.h
