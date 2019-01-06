#!/bin/bash
cd ../app/src
javah -o crypto_wrapper.h net.phonex.util.crypto.NativeCryptoHelper
mv crypto_wrapper.h ../jni/crypto-wrapper/include
