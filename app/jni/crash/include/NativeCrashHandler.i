%module (jniclassname="crash") crash
%header %{
#include "NCH.h"
%}
#include "NCH.h"

//%ignore nRegisterForNativeCrash();
//%ignore nUnregisterForNativeCrash();
//%native(nRegisterForNativeCrash) jboolean nRegisterForNativeCrash();
//%native(nUnregisterForNativeCrash) void nUnregisterForNativeCrash();
%{
/*JNIEXPORT jboolean JNICALL Java_org_pjsip_pjsua_pjsuaJNI_nRegisterForNativeCrash(JNIEnv * env, jobject obj){
  return Java_com_github_nativehandler_NativeCrashHandler_nRegisterForNativeCrash(env, obj);
}
JNIEXPORT void JNICALL Java_org_pjsip_pjsua_pjsuaJNI_nUnregisterForNativeCrash(JNIEnv *env, jobject obj){
  return Java_com_github_nativehandler_NativeCrashHandler_nUnregisterForNativeCrash(env, obj);
}*/

%}
