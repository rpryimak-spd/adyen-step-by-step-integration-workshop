package com.adyen.workshop;

public class Storage {
    public static final String SHOPPER_REFERENCE = "shopper-12345";
    public static volatile String STORED_PAYMENT_METHOD_ID = null;

    private Storage() {}
}