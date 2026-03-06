package org.jahia.modules.searchandreplace.graphql.model;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;

/**
 * GraphQL type for property preview
 */
@GraphQLName("PropertyPreview")
public class GqlPropertyPreview {

    private String name;
   private String currentValue;
    private String newValue;
    private String label;

    public GqlPropertyPreview() {
    }

    public GqlPropertyPreview(String name, String currentValue, String newValue, String label) {
        this.name = name;
        this.currentValue = currentValue;
        this.newValue = newValue;
        this.label = label;
    }

    @GraphQLField
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @GraphQLField
    public String getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(String currentValue) {
        this.currentValue = currentValue;
    }

    @GraphQLField
    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    @GraphQLField
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
