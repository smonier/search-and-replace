package org.jahia.modules.searchandreplace.graphql.model;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;

/**
 * GraphQL type for node type info
 */
@GraphQLName("SearchAndReplaceNodeTypeInfo")
public class GqlNodeTypeInfo {

    private String name;
    private String label;
    private Integer count;

    public GqlNodeTypeInfo() {
    }

    public GqlNodeTypeInfo(String name, String label, Integer count) {
        this.name = name;
        this.label = label;
        this.count = count;
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
    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }
}
