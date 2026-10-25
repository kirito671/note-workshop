package com.example.noteworkshop.security;

import com.example.noteworkshop.exception.BusinessException;
import com.example.noteworkshop.exception.ErrorCode;

public final class UserContext {
    private static final ThreadLocal<CurrentUser> CURRENT_USER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(CurrentUser user) {
        CURRENT_USER.set(user);
    }

    public static CurrentUser get() {
        CurrentUser user = CURRENT_USER.get();
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    public static CurrentUser getOrNull() {
        return CURRENT_USER.get();
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}