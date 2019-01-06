/* 
 * File:   CryptoUtils.cpp
 * Author: miroc
 */

#include <android/log.h>

#include <openssl/evp.h>

#include "crypto_utils.h"

#define LOG_TAG "crypto_utils.c"
#define DPRINTF(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#define IPRINTF(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define EPRINTF(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

// PKCS5_PBKDF2_HMAC() and PBKCS5_PBKDF2_HMAC_SHA1() return 1 on success or 0 on error.
int pbkdf2_hmac_sha256(const char *pass, const char salt[], size_t saltLen, int iter, char out[], size_t keyLen){
    const unsigned char *salt_u = (const unsigned char *) (salt);
    unsigned char *out_u = (unsigned char *) (out);

    return PKCS5_PBKDF2_HMAC(pass, strlen(pass), salt_u, saltLen, iter, EVP_sha256(), keyLen, out_u);
}

int pbkdf2_hmac_sha1(const char *pass, const char salt[], size_t saltLen, int iter, char out[], size_t keyLen){
    const unsigned char *salt_u = (const unsigned char*) (salt);
    unsigned char *out_u = (unsigned char *) (out);

    return PKCS5_PBKDF2_HMAC_SHA1(pass, strlen(pass), salt_u, saltLen, iter, keyLen, out_u);
}