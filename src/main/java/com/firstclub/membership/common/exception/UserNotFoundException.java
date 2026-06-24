package com.firstclub.membership.common.exception;
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String m) { super(m); }
}
