package com.yohannzhang.aigit.core.models;

public class ActionResult {
    private final boolean success;
    private final String message;
    private final Object data;

    public ActionResult(boolean success, String message, Object data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Object getData() {
        return data;
    }
} 