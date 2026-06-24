package com.firstclub.membership.common.exception;
public class PaymentFailedException extends RuntimeException {
    public PaymentFailedException(String m) { super(m); }
}
