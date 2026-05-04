package com.adyen.workshop;

public class Storage {
    public static final String SHOPPER_REFERENCE = "shopper-12345";
    public static volatile String STORED_PAYMENT_METHOD_ID = null;
    public static volatile String LATEST_PRE_AUTH_PSP_REFERENCE = null;
    public static volatile String CAPTURED_PSP_REFERENCE = null;

    private Storage() {}
}