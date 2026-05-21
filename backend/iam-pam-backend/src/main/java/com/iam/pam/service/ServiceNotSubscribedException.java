package com.iam.pam.service;

public class ServiceNotSubscribedException extends RuntimeException {
    public ServiceNotSubscribedException(String message) {
        super(message);
    }
}
