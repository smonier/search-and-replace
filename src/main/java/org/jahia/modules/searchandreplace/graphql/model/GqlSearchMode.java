package org.jahia.modules.searchandreplace.graphql.model;

import graphql.annotations.annotationTypes.GraphQLName;

/**
 * GraphQL enum for search mode
 */
@GraphQLName("SearchMode")
public enum GqlSearchMode {
    EXACT_MATCH,
    CASE_INSENSITIVE,
    REGEX
}
