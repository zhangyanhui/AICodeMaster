package com.yohannzhang.aigit.util;


public class CodeUtil {
    public CodeUtil() {
    }

    public String formatCode(String code) {
        String withoutComments = code.replaceAll("//.*", "");
        String formattedCode = withoutComments.replaceAll("(?m)^[ \t]*\r?\n", "").trim();
        return formattedCode.replace("\n", "\\n").replace("\"", "\\\"");
    }

    public String formatComment(String comment) {
        String formattedCode = comment.replaceAll("(?m)^[ \t]*\r?\n", "").trim();
        return formattedCode.replace("\n", "\\n").replace("\"", "\\\"");
    }
}