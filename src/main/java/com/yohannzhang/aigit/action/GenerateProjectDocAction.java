package com.yohannzhang.aigit.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.yohannzhang.aigit.ui.CombinedWindowFactory;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class GenerateProjectDocAction extends AnAction {
    private static final String RESULT_BOX_TOOL_WINDOW = "AICodeMaster";
    private static final String FEIGN_CLIENT_ANNOTATION = "org.springframework.cloud.openfeign.FeignClient";
    private static final String POST_MAPPING = "org.springframework.web.bind.annotation.PostMapping";
    private static final String GET_MAPPING = "org.springframework.web.bind.annotation.GetMapping";

    public GenerateProjectDocAction() {
        super("生成API文档");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getData(CommonDataKeys.PROJECT);
        if (project == null) {
            Messages.showErrorDialog("未找到项目", "错误");
            return;
        }

        // Show tool window
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(RESULT_BOX_TOOL_WINDOW);
        if (toolWindow != null) {
            toolWindow.show();
        }

        CombinedWindowFactory windowFactory = CombinedWindowFactory.getInstance(project);
        windowFactory.startLoadingAnimation(project);

        try {
            // 获取项目根目录
            String projectPath = project.getBasePath();
            if (projectPath == null) {
                windowFactory.stopLoadingAnimation(project);
                windowFactory.updateResult("无法获取项目路径", project);
                return;
            }

            // 创建文档目录
            String docDir = projectPath + File.separator + "docs";
            try {
                Files.createDirectories(Paths.get(docDir));
            } catch (IOException ex) {
                windowFactory.stopLoadingAnimation(project);
                windowFactory.updateResult("创建文档目录失败: " + ex.getMessage(), project);
                return;
            }

            // 生成文档文件名
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String docFile = docDir + File.separator + "API文档_" + timestamp + ".json";

            // 扫描所有FeignClient接口中的Mapping注解
            List<Map<String, Object>> apiDocs = scanControllerMappings(project);

            if (apiDocs.isEmpty()) {
                windowFactory.stopLoadingAnimation(project);
                windowFactory.updateResult("未找到任何API接口，请确保项目中包含带有@FeignClient注解的接口", project);
                return;
            }

            // 生成JSON文档
            String jsonContent = generateJsonDocument(apiDocs);

            // 保存文档
            try (FileWriter writer = new FileWriter(docFile)) {
                writer.write(jsonContent);
            }

            // 获取相对路径
            String relativePath = new File(docFile).getAbsolutePath()
                    .replace(project.getBasePath(), "")
                    .replaceFirst("^[/\\\\]", "");

            // 构建成功信息
            String successMessage = String.format(
                    "<div style='padding: 15px; background-color: #e8f5e9; border-left: 4px solid #4caf50; margin: 10px 0;'>" +
                            "<h3 style='color: #2e7d32; margin-top: 0;'>✨ API文档生成成功</h3>" +
                            "<p style='margin: 5px 0;'><strong>📁 保存位置：</strong><code style='background: #f5f5f5; padding: 2px 4px; border-radius: 3px;'>%s</code></p>" +
                            "<p style='margin: 5px 0;'><strong>📝 文件大小：</strong>%.2f KB</p>" +
                            "<p style='margin: 5px 0;'><strong>⏱️ 生成时间：</strong>%s</p>" +
                            "<p style='margin: 5px 0;'><strong>🔍 扫描结果：</strong>共发现 %d 个API接口</p>" +
                            "<p style='margin: 5px 0;'><strong>💡 提示：</strong>您可以在项目目录的 docs 文件夹中找到生成的文档</p>" +
                            "</div>",
                    relativePath,
                    new File(docFile).length() / 1024.0,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    apiDocs.size()
            );

            windowFactory.stopLoadingAnimation(project);
            windowFactory.updateResult(successMessage, project);

            // 刷新文件系统以显示新文件
            VirtualFileManager.getInstance().refreshWithoutFileWatcher(true);
            VirtualFile docDirFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(docDir);
            if (docDirFile != null) {
                docDirFile.refresh(false, false);
            }

        } catch (Exception ex) {
            windowFactory.stopLoadingAnimation(project);
            windowFactory.updateResult("Error: " + ex.getMessage(), project);
        }
    }

    private List<Map<String, Object>> scanControllerMappings(Project project) {
        List<Map<String, Object>> apiDocs = new ArrayList<>();
        StringBuilder debugInfo = new StringBuilder();
        debugInfo.append("开始扫描FeignClient接口...\n");
        
        // 检查项目类型和依赖
        debugInfo.append("检查项目配置...\n");
        VirtualFile buildFile = findBuildFile(project);
        if (buildFile != null) {
            debugInfo.append("找到构建文件: ").append(buildFile.getPath()).append("\n");
            checkDependencies(buildFile, debugInfo);
        } else {
            debugInfo.append("未找到构建文件\n");
        }
        
        // 使用JavaPsiFacade查找所有带有@FeignClient注解的类
        JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        
        // 尝试不同的注解名称
        String[] possibleAnnotations = {
            FEIGN_CLIENT_ANNOTATION,
            "FeignClient",
            "org.springframework.cloud.openfeign.FeignClient",
            "org.springframework.cloud.netflix.feign.FeignClient"
        };
        
        Collection<PsiClass> feignInterfaces = new ArrayList<>();
        
        for (String annotationName : possibleAnnotations) {
            debugInfo.append("\n尝试查找注解: ").append(annotationName).append("\n");
            PsiClass[] classes = javaPsiFacade.findClasses(annotationName, scope);
            debugInfo.append("找到的类数量: ").append(classes.length).append("\n");
            
            for (PsiClass psiClass : classes) {
                debugInfo.append("检查类: ").append(psiClass.getName())
                        .append(" (是否接口: ").append(psiClass.isInterface())
                        .append(", 注解数量: ").append(psiClass.getAnnotations().length)
                        .append(")\n");
                
                // 打印所有注解
                for (PsiAnnotation annotation : psiClass.getAnnotations()) {
                    String qualifiedName = annotation.getQualifiedName();
                    debugInfo.append("  注解: ").append(qualifiedName).append("\n");
                }
                
                if (psiClass.isInterface()) {
                    feignInterfaces.add(psiClass);
                    debugInfo.append("  添加Feign接口: ").append(psiClass.getName()).append("\n");
                }
            }
        }
        
        // 如果上面的方法没有找到，尝试直接扫描文件
        if (feignInterfaces.isEmpty()) {
            debugInfo.append("\n尝试直接扫描文件...\n");
            scanJavaFiles(project.getBaseDir(), feignInterfaces, project, debugInfo);
        }
        
        debugInfo.append("\n找到的FeignClient接口数量: ").append(feignInterfaces.size()).append("\n");
        
        // 处理找到的FeignClient接口
        for (PsiClass feignInterface : feignInterfaces) {
            debugInfo.append("\n处理FeignClient接口: ").append(feignInterface.getName()).append("\n");
            
            // 获取FeignClient的name/value属性作为基础路径
            String basePath = getFeignClientPath(feignInterface);
            debugInfo.append("  - 基础路径: ").append(basePath).append("\n");
            
            // 扫描接口中的所有方法
            for (PsiMethod method : feignInterface.getMethods()) {
                Map<String, Object> apiDoc = scanMethodMapping(method, basePath);
                if (apiDoc != null) {
                    debugInfo.append("  - 找到API方法: ").append(method.getName())
                            .append(" (").append(apiDoc.get("method")).append(" ")
                            .append(apiDoc.get("url")).append(")\n");
                    apiDocs.add(apiDoc);
                }
            }
        }
        
        debugInfo.append("\n最终找到的API接口数量: ").append(apiDocs.size()).append("\n");
        
        // 显示调试信息
        CombinedWindowFactory.getInstance(project).updateResult(debugInfo.toString(), project);
        
        return apiDocs;
    }

    private VirtualFile findBuildFile(Project project) {
        VirtualFile rootDir = project.getBaseDir();
        VirtualFile[] children = rootDir.getChildren();
        
        // 首先查找build.gradle
        for (VirtualFile file : children) {
            if (file.getName().equals("build.gradle")) {
                return file;
            }
        }
        
        // 然后查找build.gradle.kts
        for (VirtualFile file : children) {
            if (file.getName().equals("build.gradle.kts")) {
                return file;
            }
        }
        
        return null;
    }

    private void checkDependencies(VirtualFile buildFile, StringBuilder debugInfo) {
        try {
            String content = new String(buildFile.contentsToByteArray());
            
            // 检查是否包含OpenFeign依赖
            boolean hasOpenFeign = content.contains("spring-cloud-starter-openfeign") ||
                                 content.contains("spring-cloud-starter-feign");
            
            debugInfo.append("OpenFeign依赖状态: ").append(hasOpenFeign ? "已添加" : "未添加").append("\n");
            
            if (!hasOpenFeign) {
                debugInfo.append("建议添加以下依赖到build.gradle:\n");
                debugInfo.append("implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'\n");
            }
            
            // 检查Spring Cloud版本
            if (content.contains("spring-cloud")) {
                debugInfo.append("已找到Spring Cloud依赖\n");
            } else {
                debugInfo.append("建议添加Spring Cloud依赖管理:\n");
                debugInfo.append("dependencyManagement {\n");
                debugInfo.append("    imports {\n");
                debugInfo.append("        mavenBom \"org.springframework.cloud:spring-cloud-dependencies:${springCloudVersion}\"\n");
                debugInfo.append("    }\n");
                debugInfo.append("}\n");
            }
            
        } catch (IOException e) {
            debugInfo.append("读取构建文件失败: ").append(e.getMessage()).append("\n");
        }
    }

    private void scanJavaFiles(VirtualFile directory, Collection<PsiClass> controllers, Project project, StringBuilder debugInfo) {
        if (directory == null) return;
        
        debugInfo.append("扫描目录: ").append(directory.getPath()).append("\n");
        
        VirtualFile[] children = directory.getChildren();
        debugInfo.append("目录中的文件数量: ").append(children.length).append("\n");
        
        for (VirtualFile file : children) {
            if (file.isDirectory()) {
                // 跳过一些不需要扫描的目录
                String dirName = file.getName().toLowerCase();
                if (dirName.equals("target") || dirName.equals("build") || dirName.equals(".git") || dirName.equals(".idea")) {
                    debugInfo.append("跳过目录: ").append(file.getPath()).append("\n");
                    continue;
                }
                scanJavaFiles(file, controllers, project, debugInfo);
            } else if (file.getName().endsWith(".java")) {
                debugInfo.append("检查Java文件: ").append(file.getPath()).append("\n");
                PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                if (psiFile instanceof PsiJavaFile) {
                    PsiClass[] classes = ((PsiJavaFile) psiFile).getClasses();
                    debugInfo.append("  文件中的类数量: ").append(classes.length).append("\n");
                    
                    for (PsiClass psiClass : classes) {
                        debugInfo.append("  检查类: ").append(psiClass.getName())
                                .append(" (是否接口: ").append(psiClass.isInterface())
                                .append(", 注解数量: ").append(psiClass.getAnnotations().length)
                                .append(")\n");
                        
                        // 打印所有注解的完整名称
                        for (PsiAnnotation annotation : psiClass.getAnnotations()) {
                            String qualifiedName = annotation.getQualifiedName();
                            debugInfo.append("    注解: ").append(qualifiedName).append("\n");
                        }
                        
                        if (isControllerClass(psiClass)) {
                            debugInfo.append("  找到FeignClient接口: ").append(psiClass.getName()).append("\n");
                            controllers.add(psiClass);
                        }
                    }
                } else {
                    debugInfo.append("  不是Java文件或无法解析\n");
                }
            }
        }
    }

    private boolean isControllerClass(PsiClass psiClass) {
        if (psiClass == null) {
            return false;
        }
        
        if (!psiClass.isInterface()) {
            return false;
        }
        
        // 检查是否有@FeignClient注解
        for (PsiAnnotation annotation : psiClass.getAnnotations()) {
            String qualifiedName = annotation.getQualifiedName();
            if (qualifiedName != null) {
                if (qualifiedName.equals(FEIGN_CLIENT_ANNOTATION) ||
                    qualifiedName.equals("FeignClient") ||
                    qualifiedName.equals("org.springframework.cloud.openfeign.FeignClient") ||
                    qualifiedName.equals("org.springframework.cloud.netflix.feign.FeignClient")) {
                    return true;
                }
            }
        }
        
        return false;
    }

    private String getFeignClientPath(PsiClass feignInterface) {
        PsiAnnotation feignClient = feignInterface.getAnnotation(FEIGN_CLIENT_ANNOTATION);
        if (feignClient == null) {
            // 尝试使用简写形式
            feignClient = feignInterface.getAnnotation("FeignClient");
        }
        
        if (feignClient != null) {
            // 首先尝试获取name属性
            PsiAnnotationMemberValue nameValue = feignClient.findAttributeValue("name");
            if (nameValue != null) {
                return nameValue.getText().replace("\"", "");
            }
            // 如果没有name属性，尝试获取value属性
            PsiAnnotationMemberValue value = feignClient.findAttributeValue("value");
            if (value != null) {
                return value.getText().replace("\"", "");
            }
        }
        return "";
    }

    private Map<String, Object> scanMethodMapping(PsiMethod method, String basePath) {
        Map<String, Object> apiDoc = null;
        
        // 检查PostMapping注解
        PsiAnnotation postMapping = method.getAnnotation(POST_MAPPING);
        if (postMapping != null) {
            apiDoc = createApiDoc(method, postMapping, basePath, "POST");
        }
        
        // 检查GetMapping注解
        PsiAnnotation getMapping = method.getAnnotation(GET_MAPPING);
        if (getMapping != null) {
            apiDoc = createApiDoc(method, getMapping, basePath, "GET");
        }
        
        return apiDoc;
    }

    private Map<String, Object> createApiDoc(PsiMethod method, PsiAnnotation mapping, String basePath, String httpMethod) {
        Map<String, Object> apiDoc = new HashMap<>();
        
        // 获取URL路径
        PsiAnnotationMemberValue value = mapping.findAttributeValue("value");
        String path = value != null ? value.getText().replace("\"", "") : "";
        
        // 组合完整路径
        String fullPath = basePath + (path.startsWith("/") ? path : "/" + path);
        apiDoc.put("url", fullPath);
        apiDoc.put("method", httpMethod);
        apiDoc.put("javaMethod", method.getName());
        
        // 获取入参
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length > 0) {
            Map<String, String> inputParams = new HashMap<>();
            for (PsiParameter param : parameters) {
                // 检查是否有@RequestBody注解
                PsiAnnotation requestBody = param.getAnnotation("org.springframework.web.bind.annotation.RequestBody");
                if (requestBody != null) {
                    inputParams.put("body", getParameterType(param));
                } else {
                    inputParams.put(param.getName(), getParameterType(param));
                }
            }
            apiDoc.put("input", inputParams);
        }
        
        // 获取返回值类型
        PsiType returnType = method.getReturnType();
        if (returnType != null) {
            apiDoc.put("output", getReturnType(returnType));
        }
        
        return apiDoc;
    }

    private String getParameterType(PsiParameter parameter) {
        PsiType type = parameter.getType();
        if (type instanceof PsiClassType) {
            PsiClass psiClass = ((PsiClassType) type).resolve();
            if (psiClass != null) {
                return psiClass.getName();
            }
        }
        return type.getPresentableText();
    }

    private String getReturnType(PsiType returnType) {
        if (returnType instanceof PsiClassType) {
            PsiClass psiClass = ((PsiClassType) returnType).resolve();
            if (psiClass != null) {
                return psiClass.getName();
            }
        }
        return returnType.getPresentableText();
    }

    private String generateJsonDocument(List<Map<String, Object>> apiDocs) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"apis\": [\n");
        
        for (int i = 0; i < apiDocs.size(); i++) {
            Map<String, Object> api = apiDocs.get(i);
            json.append("    {\n");
            json.append("      \"url\": \"").append(api.get("url")).append("\",\n");
            json.append("      \"method\": \"").append(api.get("method")).append("\",\n");
            json.append("      \"javaMethod\": \"").append(api.get("javaMethod")).append("\",\n");
            
            if (api.containsKey("input")) {
                json.append("      \"input\": ").append(convertMapToJson((Map<String, String>) api.get("input"))).append(",\n");
            }
            
            if (api.containsKey("output")) {
                json.append("      \"output\": \"").append(api.get("output")).append("\"\n");
            }
            
            json.append("    }").append(i < apiDocs.size() - 1 ? "," : "").append("\n");
        }
        
        json.append("  ]\n");
        json.append("}");
        
        return json.toString();
    }

    private String convertMapToJson(Map<String, String> map) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        
        int i = 0;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            json.append("\"").append(entry.getKey()).append("\": \"").append(entry.getValue()).append("\"");
            if (i < map.size() - 1) {
                json.append(", ");
            }
            i++;
        }
        
        json.append("}");
        return json.toString();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getData(CommonDataKeys.PROJECT);
        e.getPresentation().setEnabledAndVisible(project != null);
    }
} 