package com.ayun.common;

/**
 * 业务异常：参数校验不通过、状态冲突、资源不存在这类「调用方可读懂的拒绝」。
 * 与系统异常（IO、网络、第三方）分开，前者回 400 原样提示，后者回 500 通用文案
 */
public class BizException extends RuntimeException {

    /**
     * @param message 面向用户的中文提示，会原样出现在响应体里
     */
    public BizException(String message) {
        super(message);
    }
}
