#include <android/log.h>

#include <openssl/obj_mac.h>
#include <openssl/ec.h>

#include "crypto_wrapper.h"
#include "crypto_utils.h"
#include "crypto_scrypt.h"

//==============================
// Declarations
//==============================
#define LOG_TAG "crypto_wrapper.c"
#define DPRINTF(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#define IPRINTF(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define EPRINTF(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

// Values also defined in Java
#define ALG_HMAC_SHA1 1
#define ALG_HMAC_SHA256 2


JNIEXPORT jint JNICALL Java_net_phonex_util_crypto_NativeCryptoHelper_pbkdf2
  (JNIEnv *env, jobject thiz, jstring pass, jbyteArray salt, jint saltLen, jint iter, jbyteArray out, jint outLen, jint macType){

    //  n_pass - native string pointer
    const char *n_pass = env->GetStringUTFChars(pass, 0);
    char* salt_buffer = new char[saltLen];

    env->GetByteArrayRegion(salt, 0, saltLen, (jbyte*) salt_buffer);

    char* out_buffer = new char[outLen];
    int status;
    if (macType == ALG_HMAC_SHA1){
        status = pbkdf2_hmac_sha1(n_pass, salt_buffer, saltLen, iter, out_buffer, outLen);
    } else {
        status = pbkdf2_hmac_sha256(n_pass, salt_buffer, saltLen, iter, out_buffer, outLen);
    }

    env->SetByteArrayRegion(out, 0, outLen, (jbyte*) out_buffer);
    env->ReleaseStringUTFChars(pass, n_pass);
    delete[] salt_buffer;
    delete[] out_buffer;

    return status;
  }

  JNIEXPORT jint JNICALL Java_net_phonex_util_crypto_NativeCryptoHelper_scrypt
    (JNIEnv *env, jobject thiz, jbyteArray pass, jint passLen, jbyteArray salt, jint saltLen, jint N, jint r, jint p, jbyteArray out, jint outLen){

    uint8_t* salt_buffer = new uint8_t[saltLen];
    uint8_t* pass_buffer = new uint8_t[passLen];
    uint8_t* out_buffer = new uint8_t[outLen];

    env->GetByteArrayRegion(salt, 0, saltLen, (jbyte*) salt_buffer);
    env->GetByteArrayRegion(pass, 0, passLen, (jbyte*) pass_buffer);

    int value = crypto_scrypt(pass_buffer, passLen, salt_buffer, saltLen, N, r, p, out_buffer, outLen);

    env->SetByteArrayRegion(out, 0, outLen, (jbyte*) out_buffer);

    delete[] salt_buffer;
    delete[] pass_buffer;
    delete[] out_buffer;

    return value;
}