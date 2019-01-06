/*
 * File:   CryptoUtils.h
 * Author: miroc
 */

#ifndef CRYPTOUTILS_H
#define	CRYPTOUTILS_H
#ifdef __cplusplus
extern "C" {
#endif

int pbkdf2_hmac_sha256(const char *pass, const char salt[], size_t saltLen, int iter, char out[], size_t keyLen);
int pbkdf2_hmac_sha1(const char *pass, const char salt[], size_t saltLen, int iter, char out[], size_t keyLen);

#ifdef __cplusplus
}
#endif
#endif	/* CRYPTOUTILS_H */

