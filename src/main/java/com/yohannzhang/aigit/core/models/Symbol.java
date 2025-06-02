package com.yohannzhang.aigit.core.models;

import java.util.*;

public class Symbol {
    private final String name;
    private final String type;
    private final String visibility;
    private final int startLine;
    private final int endLine;
    private final List<String> modifiers;
    private final Map<String, String> attributes;
    private final String documentation;
    private final List<String> references;

    public Symbol(String name, String type, String visibility, int startLine, int endLine,
                 List<String> modifiers, Map<String, String> attributes,
                 String documentation, List<String> references) {
        this.name = name;
        this.type = type;
        this.visibility = visibility;
        this.startLine = startLine;
        this.endLine = endLine;
        this.modifiers = new ArrayList<>(modifiers);
        this.attributes = new HashMap<>(attributes);
        this.documentation = documentation;
        this.references = new ArrayList<>(references);
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
        return Collections.unmodifiableList(modifiers);
    }

    public Map<String, String> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public String getDocumentation() {
        return documentation;
    }

    public List<String> getReferences() {
        return Collections.unmodifiableList(references);
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