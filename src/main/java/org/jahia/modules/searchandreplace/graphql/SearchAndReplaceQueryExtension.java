package org.jahia.modules.searchandreplace.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;

/**
 * GraphQL Query extension to add searchAndReplace field
 */
@GraphQLTypeExtension(DXGraphQLProvider.Query.class)
public final class SearchAndReplaceQueryExtension {

    private SearchAndReplaceQueryExtension() {
        // utility
    }

    @GraphQLField
    @GraphQLName("searchAndReplace")
    @GraphQLDescription("Search and Replace operations")
    public static SearchAndReplaceOperations searchAndReplace() {
        return new SearchAndReplaceOperations();
    }
}
