package org.jahia.modules.searchandreplace.graphql.model;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import java.util.List;

/**
 * GraphQL type for replace preview
 */
@GraphQLName("ReplacePreview")
public class GqlReplacePreview {

    private String nodeUuid;
    private String nodePath;
    private List<GqlPropertyPreview> propertyPreviews;

    public GqlReplacePreview() {
    }

    public GqlReplacePreview(String nodeUuid, String nodePath, List<GqlPropertyPreview> propertyPreviews) {
        this.nodeUuid = nodeUuid;
        this.nodePath = nodePath;
        this.propertyPreviews = propertyPreviews;
    }

    @GraphQLField
    public String getNodeUuid() {
        return nodeUuid;
    }

    public void setNodeUuid(String nodeUuid) {
        this.nodeUuid = nodeUuid;
    }

    @GraphQLField
    public String getNodePath() {
        return nodePath;
    }

    public void setNodePath(String nodePath) {
        this.nodePath = nodePath;
    }

    @GraphQLField
    public List<GqlPropertyPreview> getPropertyPreviews() {
        return propertyPreviews;
    }

    public void setPropertyPreviews(List<GqlPropertyPreview> propertyPreviews) {
        this.propertyPreviews = propertyPreviews;
    }
}
