/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 2.0.12
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package net.phonex.xv;

public enum pjsip_ssl_method {
  PJSIP_SSL_UNSPECIFIED_METHOD(XviJNI.PJSIP_SSL_UNSPECIFIED_METHOD_get()),
  PJSIP_SSLV2_METHOD(XviJNI.PJSIP_SSLV2_METHOD_get()),
  PJSIP_SSLV3_METHOD(XviJNI.PJSIP_SSLV3_METHOD_get()),
  PJSIP_TLSV1_METHOD(XviJNI.PJSIP_TLSV1_METHOD_get()),
  PJSIP_TLSV1_1_METHOD(XviJNI.PJSIP_TLSV1_1_METHOD_get()),
  PJSIP_TLSV1_2_METHOD(XviJNI.PJSIP_TLSV1_2_METHOD_get()),
  PJSIP_SSLV23_METHOD(XviJNI.PJSIP_SSLV23_METHOD_get());

  public final int swigValue() {
    return swigValue;
  }

  public static pjsip_ssl_method swigToEnum(int swigValue) {
    pjsip_ssl_method[] swigValues = pjsip_ssl_method.class.getEnumConstants();
    if (swigValue < swigValues.length && swigValue >= 0 && swigValues[swigValue].swigValue == swigValue)
      return swigValues[swigValue];
    for (pjsip_ssl_method swigEnum : swigValues)
      if (swigEnum.swigValue == swigValue)
        return swigEnum;
    throw new IllegalArgumentException("No enum " + pjsip_ssl_method.class + " with value " + swigValue);
  }

  @SuppressWarnings("unused")
  private pjsip_ssl_method() {
    this.swigValue = SwigNext.next++;
  }

  @SuppressWarnings("unused")
  private pjsip_ssl_method(int swigValue) {
    this.swigValue = swigValue;
    SwigNext.next = swigValue+1;
  }

  @SuppressWarnings("unused")
  private pjsip_ssl_method(pjsip_ssl_method swigEnum) {
    this.swigValue = swigEnum.swigValue;
    SwigNext.next = this.swigValue+1;
  }

  private final int swigValue;

  private static class SwigNext {
    private static int next = 0;
  }
}

