package com.ayun.common;

/**
 * 资源不存在异常
 * 无权访问某资源时同样按「不存在」抛出，避免向未授权方泄露该资源是否存在
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
