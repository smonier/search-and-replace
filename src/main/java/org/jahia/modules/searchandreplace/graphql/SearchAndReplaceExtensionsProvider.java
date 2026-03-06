package org.jahia.modules.searchandreplace.graphql;

import org.jahia.modules.graphql.provider.dxm.DXGraphQLExtensionsProvider;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exposes this module's @GraphQLTypeExtension classes to the DX GraphQL provider.
 */
@Component(service = DXGraphQLExtensionsProvider.class, immediate = true)
public class SearchAndReplaceExtensionsProvider implements DXGraphQLExtensionsProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(SearchAndReplaceExtensionsProvider.class);
    
    @Activate
    public void activate() {
        logger.debug("SearchAndReplaceExtensionsProvider activated");
    }
}
