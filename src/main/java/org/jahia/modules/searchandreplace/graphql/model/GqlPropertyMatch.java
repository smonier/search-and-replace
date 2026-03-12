package org.jahia.modules.searchandreplace.graphql.model;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;

/**
 * GraphQL type for a property match
 */
@GraphQLName("SearchAndReplacePropertyMatch")
public class GqlPropertyMatch {

    private String name;
    private String value;
    private String label;
    private boolean replaceable;

    public GqlPropertyMatch() {
    }

    public GqlPropertyMatch(String name, String value, String label, boolean replaceable) {
        this.name = name;
        this.value = value;
        this.label = label;
        this.replaceable = replaceable;
    }

    @GraphQLField
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @GraphQLField
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @GraphQLField
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    @GraphQLField
    public boolean isReplaceable() {
        return replaceable;
    }

    public void setReplaceable(boolean replaceable) {
        this.replaceable = replaceable;
    }
}
