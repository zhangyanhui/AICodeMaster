
package com.yohannzhang.aigit.util;

public class ActionControl {
    private static boolean isActionInProgress = false;

    public static synchronized boolean startAction() {
        if (isActionInProgress) {
            return false;
        }
        isActionInProgress = true;
        return true;
    }

    public static synchronized void endAction() {
        isActionInProgress = false;
    }
}