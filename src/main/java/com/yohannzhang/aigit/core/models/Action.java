package com.yohannzhang.aigit.core.models;

public interface Action {
    ActionResult execute();
    String getDescription();
    String getActionType();
} 