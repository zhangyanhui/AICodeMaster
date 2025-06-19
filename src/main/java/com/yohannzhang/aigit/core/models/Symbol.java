package com.yohannzhang.aigit.core.models;

import java.util.*;

public class Symbol {
    private final String name;
    private final String type;
    private final String visibility;
    private final int startLine;
    private final int endLine;
    private final List<String> modifiers;
    private final Map<String, Object> attributes;
    private final String documentation;
    private final List<String> references;
    private String content;

    public Symbol(String name, String type, String visibility, int startLine, int endLine,
                 List<String> modifiers, Map<String, Object> attributes,
                 String documentation, List<String> references) {
        this.name = name;
        this.type = type;
        this.visibility = visibility;
        this.startLine = startLine;
        this.endLine = endLine;
        this.modifiers = modifiers;
        this.attributes = attributes;
        this.documentation = documentation;
        this.references = references;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getVisibility() {
        return visibility;
    }

    public int getStartLine() {
        return startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    public List<String> getModifiers() {
        return modifiers;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public String getDocumentation() {
        return documentation;
    }

    public List<String> getReferences() {
        return references;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void addReference(String reference) {
        references.add(reference);
    }

    @Override
    public String toString() {
        return String.format("Symbol{name='%s', type='%s', visibility='%s', " +
                           "startLine=%d, endLine=%d, modifiers=%s}",
                           name, type, visibility, startLine, endLine, modifiers);
    }
} 