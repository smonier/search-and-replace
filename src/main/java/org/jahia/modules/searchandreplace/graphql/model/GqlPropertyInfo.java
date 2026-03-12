package org.jahia.modules.searchandreplace.graphql.model;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;

/**
 * GraphQL type for property info
 */
@GraphQLName("SearchAndReplacePropertyInfo")
public class GqlPropertyInfo {

    private String name;
    private String label;
    private String type;

    public GqlPropertyInfo() {
    }

    public GqlPropertyInfo(String name, String label, String type) {
        this.name = name;
        this.label = label;
        this.type = type;
    }

    @GraphQLField
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @GraphQLField
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    @GraphQLField
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
