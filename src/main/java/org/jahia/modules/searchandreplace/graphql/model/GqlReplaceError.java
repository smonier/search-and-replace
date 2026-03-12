package org.jahia.modules.searchandreplace.graphql.model;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;

/**
 * GraphQL type for replace error
 */
@GraphQLName("SearchAndReplaceReplaceError")
public class GqlReplaceError {

    private String nodeUuid;
    private String message;
    private String nodePath;

    public GqlReplaceError() {
    }

    public GqlReplaceError(String nodeUuid, String message, String nodePath) {
        this.nodeUuid = nodeUuid;
        this.message = message;
        this.nodePath = nodePath;
    }

    @GraphQLField
    public String getNodeUuid() {
        return nodeUuid;
    }

    public void setNodeUuid(String nodeUuid) {
        this.nodeUuid = nodeUuid;
    }

    @GraphQLField
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @GraphQLField
    public String getNodePath() {
        return nodePath;
    }

    public void setNodePath(String nodePath) {
        this.nodePath = nodePath;
    }
}
