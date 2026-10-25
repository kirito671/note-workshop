package com.example.noteworkshop.exception;

public enum ErrorCode {
    VALIDATION_ERROR(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或 token 无效"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    USERNAME_EXISTS(409, "用户名已存在"),
    INTERNAL_ERROR(500, "服务器内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}