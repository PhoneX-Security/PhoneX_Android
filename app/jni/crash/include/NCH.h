#ifndef __NCH__
#define __NCH__
#include <jni.h>
#define NDEBUG 1

#ifdef __cplusplus
extern "C" {
#endif
JNIEXPORT jboolean JNICALL Java_com_github_nativehandler_NativeCrashHandler_nRegisterForNativeCrash(JNIEnv * env, jobject obj);
JNIEXPORT void JNICALL Java_com_github_nativehandler_NativeCrashHandler_nUnregisterForNativeCrash(JNIEnv *env, jobject);
#ifdef __cplusplus
}
#endif

#endif // !__NATIVE_CRASH_HANDLER__