package com.firstclub.membership.common.exception;
public class AlreadySubscribedException extends RuntimeException {
    public AlreadySubscribedException(String m) { super(m); }
}
