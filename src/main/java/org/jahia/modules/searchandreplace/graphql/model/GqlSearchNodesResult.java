package org.jahia.modules.searchandreplace.graphql.model;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import java.util.List;

/**
 * GraphQL type for search results
 */
@GraphQLName("SearchAndReplaceSearchNodesResult")
public class GqlSearchNodesResult {

    private List<GqlSearchResultNode> nodes;
    private int totalCount;
    private boolean truncated;

    public GqlSearchNodesResult() {
    }

    public GqlSearchNodesResult(List<GqlSearchResultNode> nodes, int totalCount, boolean truncated) {
        this.nodes = nodes;
        this.totalCount = totalCount;
        this.truncated = truncated;
    }

    @GraphQLField
    public List<GqlSearchResultNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<GqlSearchResultNode> nodes) {
        this.nodes = nodes;
    }

    @GraphQLField
    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    @GraphQLField
    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }
}
