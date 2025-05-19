package com.yohannzhang.aigit.ui;

import com.intellij.openapi.project.Project;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefClient;
import com.sun.istack.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class MarkdownViewer {
    private static final String TOOL_WINDOW_ID = "MarkdownPreview";
    private static final String PREVIEW_HTML_TEMPLATE = "<!DOCTYPE html>\n" +
            "<html lang=\"zh-CN\">\n" +
            "<head>\n" +
            "    <meta charset=\"UTF-8\">\n" +
            "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
            "    <title>Markdown 预览</title>\n" +
            "    <script src=\"https://cdn.jsdelivr.net/npm/vditor@3.9.9/dist/js/vditor.min.js\"></script>\n" +
            "    <link rel=\"stylesheet\" href=\"https://cdn.jsdelivr.net/npm/vditor@3.9.9/dist/css/vditor.min.css\">\n" +
            "    <style>\n" +
            "        body, html {\n" +
            "            margin: 0;\n" +
            "            padding: 0;\n" +
            "            height: 100%;\n" +
            "            overflow: hidden;\n" +
            "        }\n" +
            "        #vditor {\n" +
            "            height: 100%;\n" +
            "        }\n" +
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <div id=\"vditor\"></div>\n" +
            "    <script>\n" +
            "        let vditor;\n" +
            "        function initVditor(markdown) {\n" +
            "            vditor = new Vditor('vditor', {\n" +
            "                mode: 'preview',\n" +
            "                preview: {\n" +
            "                    markdown: {\n" +
            "                        toc: true,\n" +
            "                        linkify: true,\n" +
            "                        breaks: true\n" +
            "                    },\n" +
            "                    theme: 'light'\n" +
            "                },\n" +
            "                after: () => {\n" +
            "                    vditor.preview.render(markdown);\n" +
            "                    %CALLBACK%\n" +
            "                }\n" +
            "            });\n" +
            "        }\n" +
            "        function updateMarkdown(markdown) {\n" +
            "            if (vditor) {\n" +
            "                vditor.preview.render(markdown);\n" +
            "            } else {\n" +
            "                initVditor(markdown);\n" +
            "            }\n" +
            "        }\n" +
            "    </script>\n" +
            "</body>\n" +
            "</html>";

    private final Project project;
    private final Map<String, JBCefBrowser> activeBrowsers = new HashMap<>();
    private JBCefClient jbCefClient;

    public MarkdownViewer(@NotNull Project project) {
        this.project = project;
//        initializeJBCefClient();
    }

    // 其他方法保持不变
}
