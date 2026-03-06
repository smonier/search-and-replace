package org.jahia.modules.searchandreplace.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.modules.graphql.provider.dxm.osgi.annotations.GraphQLOsgiService;
import org.jahia.modules.searchandreplace.graphql.model.*;
import org.jahia.services.content.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.jcr.*;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.nodetype.PropertyDefinition;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.jcr.query.qom.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GraphQL Operations for Search and Replace
 */
@GraphQLName("SearchAndReplaceOperations")
@GraphQLDescription("Search and Replace operations")
public class SearchAndReplaceOperations {

    private static final Logger logger = LoggerFactory.getLogger(SearchAndReplaceOperations.class);
    private static final SimpleDateFormat ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    private static final SimpleDateFormat DATE_FILTER_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final int MAX_SCAN_CANDIDATES = 250000;
    private static final int MAX_SEARCH_TERM_LENGTH = 512;
    private static final int MAX_FILTER_PROPERTIES = 500;
    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{M}+");
    private static final Pattern SITE_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final Pattern LANGUAGE_PATTERN = Pattern.compile("^[A-Za-z]{2,8}([_-][A-Za-z0-9]{2,8})*$");
    private static final Pattern NODE_TYPE_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9:_-]*$");
    private static final Pattern PROPERTY_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9:_-]*$");
    private static final DateTimeFormatter DAY_MONTH_YEAR_SLASH_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DAY_MONTH_YEAR_DASH_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter MONTH_DAY_YEAR_SLASH_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private JCRTemplate jcrTemplate;

    @Inject
    @GraphQLOsgiService
    public void setJcrTemplate(JCRTemplate jcrTemplate) {
        this.jcrTemplate = jcrTemplate;
    }

    @GraphQLField
    @GraphQLDescription("Search for nodes containing a specific term")
    public GqlSearchNodesResult searchNodes(
            @GraphQLName("termToSearch") @GraphQLNonNull String termToSearch,
            @GraphQLName("siteKey") @GraphQLNonNull String siteKey,
            @GraphQLName("language") @GraphQLNonNull String language,
            @GraphQLName("filters") GqlSearchFiltersInput filters,
            @GraphQLName("limit") Integer limit
    ) throws RepositoryException {
        String normalizedSearchTerm = normalizeSearchTerm(termToSearch);
        String validatedSiteKey = validateSiteKey(siteKey);
        String normalizedLanguage = normalizeLanguage(language);
        GqlSearchFiltersInput safeFilters = sanitizeFilters(filters);
        int maxResults = resolveLimit(limit);

        if (StringUtils.isBlank(normalizedSearchTerm)) {
            return new GqlSearchNodesResult(new ArrayList<>(), 0, false);
        }

        if (jcrTemplate == null) {
            logger.error("JCRTemplate is null! Returning empty result.");
            return new GqlSearchNodesResult(new ArrayList<>(), 0, false);
        }

        return jcrTemplate.doExecuteWithSystemSessionAsUser(null, "default", null, session -> {
            QueryManager qm = session.getWorkspace().getQueryManager();
            Query indexedQuery = buildSearchQuery(qm, session.getValueFactory(), normalizedSearchTerm, validatedSiteKey, safeFilters, true);

            QueryResult queryResult;
            try {
                logger.debug("Executing indexed search query: {}", indexedQuery.getStatement());
                queryResult = indexedQuery.execute();
            } catch (RepositoryException e) {
                logger.error("Search query failed for siteKey={} term='{}': {}", validatedSiteKey, normalizedSearchTerm, e.getMessage(), e);
                throw e;
            }

            List<GqlSearchResultNode> resultNodes = new ArrayList<>();
            Set<String> processedNodeIds = new HashSet<>();
            int count = 0;
            int scannedCandidates = 0;
            boolean truncated = false;

            NodeIterator nodes = queryResult.getNodes();
            while (nodes.hasNext() && count < maxResults && scannedCandidates < MAX_SCAN_CANDIDATES) {
                Node node = nodes.nextNode();
                scannedCandidates++;
                JCRNodeWrapper wrapper = session.getNodeByIdentifier(node.getIdentifier());
                JCRNodeWrapper searchableNode = resolveSearchableNode(wrapper);
                if (searchableNode == null) {
                    continue;
                }

                String searchableNodeId = searchableNode.getIdentifier();
                if (processedNodeIds.contains(searchableNodeId)) {
                    continue;
                }

                GqlSearchResultNode resultNode = createSearchResultNode(searchableNode, normalizedSearchTerm, safeFilters, normalizedLanguage);
                if (resultNode != null) {
                    resultNodes.add(resultNode);
                    processedNodeIds.add(searchableNodeId);
                    count++;
                }
            }

            if (nodes.hasNext() || scannedCandidates >= MAX_SCAN_CANDIDATES) {
                truncated = true;
            }

            if (resultNodes.isEmpty()) {
                Query fallbackQuery = buildSearchQuery(qm, session.getValueFactory(), normalizedSearchTerm, validatedSiteKey, safeFilters, false);
                QueryResult fallbackResult;
                try {
                    logger.debug("Indexed query returned no results, running fallback scan query: {}", fallbackQuery.getStatement());
                    fallbackResult = fallbackQuery.execute();
                } catch (RepositoryException e) {
                    logger.error("Fallback scan query failed for siteKey={} term='{}': {}", validatedSiteKey, normalizedSearchTerm, e.getMessage(), e);
                    throw e;
                }

                NodeIterator fallbackNodes = fallbackResult.getNodes();
                while (fallbackNodes.hasNext() && count < maxResults && scannedCandidates < MAX_SCAN_CANDIDATES) {
                    Node fallbackNode = fallbackNodes.nextNode();
                    scannedCandidates++;
                    JCRNodeWrapper wrapper = session.getNodeByIdentifier(fallbackNode.getIdentifier());
                    JCRNodeWrapper searchableNode = resolveSearchableNode(wrapper);
                    if (searchableNode == null) {
                        continue;
                    }

                    String searchableNodeId = searchableNode.getIdentifier();
                    if (processedNodeIds.contains(searchableNodeId)) {
                        continue;
                    }

                    GqlSearchResultNode resultNode = createSearchResultNode(searchableNode, normalizedSearchTerm, safeFilters, normalizedLanguage);
                    if (resultNode != null) {
                        resultNodes.add(resultNode);
                        processedNodeIds.add(searchableNodeId);
                        count++;
                    }
                }

                if (fallbackNodes.hasNext() || scannedCandidates >= MAX_SCAN_CANDIDATES) {
                    truncated = true;
                }
            }
            
            GqlSearchNodesResult result = new GqlSearchNodesResult();
            result.setNodes(resultNodes);
            result.setTotalCount(count);
            result.setTruncated(truncated);
            return result;
        });
    }

    @GraphQLField
    @GraphQLDescription("Get available node types for filtering")
    public List<GqlNodeTypeInfo> getNodeTypes(@GraphQLName("siteKey") @GraphQLNonNull String siteKey) throws RepositoryException {
        validateSiteKey(siteKey);

        if (jcrTemplate == null) {
            logger.error("JCRTemplate is null! Returning empty list.");
            return new ArrayList<>();
        }
        
        return jcrTemplate.doExecuteWithSystemSessionAsUser(null, "default", null, session -> {
            List<GqlNodeTypeInfo> nodeTypes = new ArrayList<>();
            
            String[] commonTypes = {
                "jnt:page", "jnt:contentFolder", "jnt:folder", "jnt:file",
                "jnt:text", "jnt:bigText", "jmix:editorialContent"
            };
            
            for (String typeName : commonTypes) {
                try {
                    NodeType nodeType = session.getWorkspace().getNodeTypeManager().getNodeType(typeName);
                    String label = nodeType.getName();
                    nodeTypes.add(new GqlNodeTypeInfo(typeName, label, null));
                } catch (NoSuchNodeTypeException e) {
                    // Skip
                }
            }
            
            return nodeTypes;
        });
    }

    @GraphQLField
    @GraphQLDescription("Get properties for a specific node type")
    public List<GqlPropertyInfo> getNodeTypeProperties(@GraphQLName("nodeType") @GraphQLNonNull String nodeType) throws RepositoryException {
        String safeNodeType = StringUtils.trimToNull(nodeType);
        if (safeNodeType == null || !NODE_TYPE_PATTERN.matcher(safeNodeType).matches()) {
            throw new IllegalArgumentException("Invalid node type");
        }
        if (jcrTemplate == null) {
            throw new IllegalStateException("JCRTemplate service is unavailable");
        }

        return jcrTemplate.doExecuteWithSystemSessionAsUser(null, "default", null, session -> {
            List<GqlPropertyInfo> properties = new ArrayList<>();
            
            try {
                NodeType nt = session.getWorkspace().getNodeTypeManager().getNodeType(safeNodeType);
                PropertyDefinition[] propDefs = nt.getPropertyDefinitions();
                
                for (PropertyDefinition propDef : propDefs) {
                    String propName = propDef.getName();
                    if (!propName.startsWith("jcr:") && !propName.startsWith("j:")) {
                        properties.add(new GqlPropertyInfo(
                            propName,
                            propName,
                            propDef.getRequiredType() + ""
                        ));
                    }
                }
            } catch (NoSuchNodeTypeException e) {
                logger.warn("Node type not found: {}", nodeType);
            }
            
            return properties;
        });
    }

    @GraphQLField
    @GraphQLDescription("Replace text in selected nodes")
    public GqlReplaceResult replaceInNodes(
            @GraphQLName("siteKey") @GraphQLNonNull String siteKey,
            @GraphQLName("nodeUuids") @GraphQLNonNull List<String> nodeUuids,
            @GraphQLName("termToReplace") @GraphQLNonNull String termToReplace,
            @GraphQLName("replacementTerm") @GraphQLNonNull String replacementTerm,
            @GraphQLName("language") @GraphQLNonNull String language,
            @GraphQLName("propertiesToReplace") List<String> propertiesToReplace,
            @GraphQLName("searchMode") GqlSearchMode searchMode
    ) throws RepositoryException {
        if (jcrTemplate == null) {
            throw new IllegalStateException("JCRTemplate service is unavailable");
        }

        String validatedSiteKey = validateSiteKey(siteKey);
        String normalizedLanguage = normalizeLanguage(language);
        String normalizedSearchTerm = normalizeSearchTerm(termToReplace);
        List<String> safePropertiesToReplace = sanitizePropertyNames(propertiesToReplace);
        GqlSearchMode effectiveSearchMode = searchMode == null ? GqlSearchMode.CASE_INSENSITIVE : searchMode;

        if (effectiveSearchMode == GqlSearchMode.REGEX) {
            validateRegexPattern(normalizedSearchTerm);
        }

        return jcrTemplate.doExecuteWithSystemSessionAsUser(null, "default", null, session -> {
            GqlReplaceResult result = new GqlReplaceResult();
            result.setSuccessfulNodes(new ArrayList<>());
            result.setFailedNodes(new ArrayList<>());
            result.setTotalPropertiesUpdated(0);
            result.setErrors(new ArrayList<>());

            if (nodeUuids == null || nodeUuids.isEmpty()) {
                return result;
            }

            for (String uuid : nodeUuids) {
                try {
                    JCRNodeWrapper node = session.getNodeByIdentifier(uuid);
                    if (!node.getPath().startsWith("/sites/" + validatedSiteKey + "/")) {
                        throw new RepositoryException("Node is outside of the requested site: " + uuid);
                    }

                    int propsUpdated = replaceInNode(
                            node,
                            normalizedSearchTerm,
                            replacementTerm,
                            normalizedLanguage,
                            safePropertiesToReplace,
                            effectiveSearchMode
                    );
                    
                    if (propsUpdated > 0) {
                        result.getSuccessfulNodes().add(uuid);
                        result.setTotalPropertiesUpdated(result.getTotalPropertiesUpdated() + propsUpdated);
                    }
                } catch (Exception e) {
                    result.getFailedNodes().add(uuid);
                    GqlReplaceError error = new GqlReplaceError();
                    error.setNodeUuid(uuid);
                    error.setMessage(e.getMessage());
                    result.getErrors().add(error);
                    logger.error("Error replacing in node {}", uuid, e);
                }
            }
            
            session.save();
            return result;
        });
    }

    @GraphQLField
    @GraphQLDescription("Preview what would be replaced without making changes")
    public GqlReplacePreview previewReplace(
            @GraphQLName("nodeUuid") @GraphQLNonNull String nodeUuid,
            @GraphQLName("termToReplace") @GraphQLNonNull String termToReplace,
            @GraphQLName("replacementTerm") @GraphQLNonNull String replacementTerm,
            @GraphQLName("propertiesToReplace") List<String> propertiesToReplace
    ) throws RepositoryException {
        if (jcrTemplate == null) {
            throw new IllegalStateException("JCRTemplate service is unavailable");
        }

        if (StringUtils.isBlank(nodeUuid)) {
            throw new IllegalArgumentException("nodeUuid is required");
        }

        String normalizedSearchTerm = normalizeSearchTerm(termToReplace);
        List<String> safePropertiesToReplace = sanitizePropertyNames(propertiesToReplace);

        return jcrTemplate.doExecuteWithSystemSessionAsUser(null, "default", null, session -> {
            GqlReplacePreview preview = new GqlReplacePreview();
            preview.setNodeUuid(nodeUuid);
            preview.setPropertyPreviews(new ArrayList<>());
            
            try {
                JCRNodeWrapper node = session.getNodeByIdentifier(nodeUuid);
                preview.setNodePath(node.getPath());
                PropertyIterator properties = node.getProperties();
                
                while (properties.hasNext()) {
                    Property property = properties.nextProperty();
                    String propName = property.getName();
                    
                    if (safePropertiesToReplace != null && !safePropertiesToReplace.contains(propName)) {
                        continue;
                    }
                    
                    if (property.getType() == PropertyType.STRING && !property.getDefinition().isProtected()) {
                        String value = property.getString();
                        if (containsIgnoreCase(value, normalizedSearchTerm)) {
                            String newValue = performReplace(value, normalizedSearchTerm, replacementTerm, GqlSearchMode.CASE_INSENSITIVE);
                            GqlPropertyPreview propPreview = new GqlPropertyPreview();
                            propPreview.setName(propName);
                            propPreview.setLabel(propName);
                            propPreview.setCurrentValue(value);
                            propPreview.setNewValue(newValue);
                            preview.getPropertyPreviews().add(propPreview);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Error previewing replace for node {}", nodeUuid, e);
            }
            
            return preview;
        });
    }

    // Helper methods
    private int resolveLimit(Integer limit) {
        if (limit != null && limit > 0) {
            return limit;
        }

        return Integer.MAX_VALUE;
    }

    private String normalizeSearchTerm(String rawTerm) {
        String trimmed = StringUtils.trimToEmpty(rawTerm);
        if (trimmed.length() > MAX_SEARCH_TERM_LENGTH) {
            throw new IllegalArgumentException("Search term is too long. Maximum length is " + MAX_SEARCH_TERM_LENGTH);
        }
        return trimmed;
    }

    private String validateSiteKey(String siteKey) {
        String trimmed = StringUtils.trimToNull(siteKey);
        if (trimmed == null || !SITE_KEY_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid site key");
        }
        return trimmed;
    }

    private String normalizeLanguage(String language) {
        String normalized = StringUtils.trimToEmpty(language).replace('-', '_');
        if (StringUtils.isBlank(normalized)) {
            return "en";
        }

        if (!LANGUAGE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid language");
        }

        return normalized;
    }

    private GqlSearchFiltersInput sanitizeFilters(GqlSearchFiltersInput filters) {
        if (filters == null) {
            return null;
        }

        GqlSearchFiltersInput sanitized = new GqlSearchFiltersInput();
        String nodeType = StringUtils.trimToNull(filters.getNodeType());
        if (nodeType != null) {
            if (!NODE_TYPE_PATTERN.matcher(nodeType).matches()) {
                throw new IllegalArgumentException("Invalid nodeType filter");
            }
            sanitized.setNodeType(nodeType);
        }

        sanitized.setCreatedAfter(StringUtils.trimToNull(filters.getCreatedAfter()));
        sanitized.setCreatedBefore(StringUtils.trimToNull(filters.getCreatedBefore()));
        sanitized.setModifiedAfter(StringUtils.trimToNull(filters.getModifiedAfter()));
        sanitized.setModifiedBefore(StringUtils.trimToNull(filters.getModifiedBefore()));
        sanitized.setProperties(sanitizePropertyNames(filters.getProperties()));

        if (StringUtils.isBlank(sanitized.getNodeType()) &&
                StringUtils.isBlank(sanitized.getCreatedAfter()) &&
                StringUtils.isBlank(sanitized.getCreatedBefore()) &&
                StringUtils.isBlank(sanitized.getModifiedAfter()) &&
                StringUtils.isBlank(sanitized.getModifiedBefore()) &&
                (sanitized.getProperties() == null || sanitized.getProperties().isEmpty())) {
            return null;
        }

        return sanitized;
    }

    private List<String> sanitizePropertyNames(List<String> propertyNames) {
        if (propertyNames == null || propertyNames.isEmpty()) {
            return null;
        }

        List<String> sanitized = new ArrayList<>();
        for (String propertyName : propertyNames) {
            String trimmed = StringUtils.trimToNull(propertyName);
            if (trimmed == null) {
                continue;
            }

            if (!PROPERTY_NAME_PATTERN.matcher(trimmed).matches()) {
                logger.debug("Ignoring unsupported property filter '{}'", trimmed);
                continue;
            }

            sanitized.add(trimmed);
            if (sanitized.size() >= MAX_FILTER_PROPERTIES) {
                break;
            }
        }

        return sanitized.isEmpty() ? null : sanitized;
    }

    private void validateRegexPattern(String expression) {
        try {
            Pattern.compile(expression);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid regular expression", e);
        }
    }

    private Query buildSearchQuery(QueryManager queryManager, ValueFactory valueFactory, String termToSearch,
                                   String siteKey, GqlSearchFiltersInput filters, boolean withFullText) throws RepositoryException {
        QueryObjectModelFactory qom = queryManager.getQOMFactory();
        String selectorType = "nt:base";
        if (filters != null && StringUtils.isNotBlank(filters.getNodeType())) {
            selectorType = filters.getNodeType();
        }
        Selector selector = qom.selector(selectorType, "s");

        Constraint constraint = qom.descendantNode("s", "/sites/" + siteKey);
        if (withFullText) {
            // Use QOM fullTextSearch with null property to search across all indexed properties.
            String fullTextExpression = buildFullTextSearchExpression(termToSearch);
            constraint = qom.and(constraint, qom.fullTextSearch("s", null, qom.literal(valueFactory.createValue(fullTextExpression))));
        }

        return qom.createQuery(selector, constraint, null, null);
    }

    private String buildFullTextSearchExpression(String rawTerm) {
        String normalized = rawTerm == null ? "" : rawTerm.trim();
        // Wrap user term in quotes and escape special characters to avoid query parser errors.
        String escapedRaw = normalized.replace("\\", "\\\\").replace("\"", "\\\"");
        String folded = stripDiacritics(normalized);
        String escapedFolded = folded.replace("\\", "\\\\").replace("\"", "\\\"");

        if (!escapedFolded.equalsIgnoreCase(escapedRaw) && !escapedFolded.isEmpty()) {
            return "\"" + escapedRaw + "\" OR \"" + escapedFolded + "\"";
        }

        return "\"" + escapedRaw + "\"";
    }

    private GqlSearchResultNode createSearchResultNode(JCRNodeWrapper node, String searchTerm,
                                                       GqlSearchFiltersInput filters, String language) {
        try {
            if (node.getName() != null && node.getName().startsWith("j:translation_")) {
                return null;
            }

            if (filters != null && StringUtils.isNotBlank(filters.getNodeType()) && !node.isNodeType(filters.getNodeType())) {
                return null;
            }

            if (!matchesDateFilters(node, filters, language)) {
                return null;
            }

            GqlSearchResultNode resultNode = new GqlSearchResultNode();
            resultNode.setUuid(node.getIdentifier());
            resultNode.setPath(node.getPath());
            resultNode.setName(node.getName());
            resultNode.setDisplayName(resolveDisplayName(node, language));
            resultNode.setNodeType(node.getPrimaryNodeTypeName());
            resultNode.setNodeTypeLabel(node.getPrimaryNodeType().getName());
            
            JCRNodeWrapper translationNode = getTranslationNode(node, language);
            Calendar creationDate = resolveNodeDate(node, translationNode, "jcr:created");
            resultNode.setCreated(formatDate(creationDate));
            
            Calendar lastModified = resolveNodeDate(node, translationNode, "jcr:lastModified");
            resultNode.setLastModified(formatDate(lastModified));
            resultNode.setParentPath(node.getParent().getPath());
            resultNode.setParentContainerPath(findParentContainerPath(node));
            
            List<String> propertyFilter = filters != null && filters.getProperties() != null ? 
                                        filters.getProperties() : null;
            List<GqlPropertyMatch> matchingProperties = collectMatchingPropertiesForLanguage(node, searchTerm, propertyFilter, language);
            if (matchingProperties.isEmpty()) {
                return null;
            }
            resultNode.setMatchingProperties(matchingProperties);
            return resultNode;
            
        } catch (RepositoryException e) {
            logger.error("Error creating search result node", e);
            return null;
        }
    }

    private List<GqlPropertyMatch> collectMatchingPropertiesForLanguage(JCRNodeWrapper node,
                                                                        String searchTerm,
                                                                        List<String> propertyFilter,
                                                                        String language) throws RepositoryException {
        List<GqlPropertyMatch> matches = new ArrayList<>();
        collectMatchingPropertiesRecursively(node, searchTerm, propertyFilter, language, "", matches);
        return dedupeMatches(matches);
    }

    private void collectMatchingPropertiesRecursively(JCRNodeWrapper currentNode,
                                                      String searchTerm,
                                                      List<String> propertyFilter,
                                                      String language,
                                                      String nodePathPrefix,
                                                      List<GqlPropertyMatch> matches) throws RepositoryException {
        String prefix = StringUtils.isBlank(nodePathPrefix) ? "" : nodePathPrefix + "/";

        JCRNodeWrapper translationNode = getTranslationNode(currentNode, language);
        if (translationNode != null) {
            collectMatchingPropertiesInSubtree(
                    translationNode,
                    searchTerm,
                    propertyFilter,
                    prefix + translationNode.getName() + "/",
                    matches
            );
        }

        matches.addAll(collectMatchingProperties(currentNode, searchTerm, propertyFilter, prefix, true));

        NodeIterator children = currentNode.getNodes();
        while (children.hasNext()) {
            Node childNode = children.nextNode();
            JCRNodeWrapper child = toNodeWrapper(currentNode, childNode);
            if (child == null) {
                continue;
            }
            String childName = child.getName();
            if (childName != null && childName.startsWith("j:translation_")) {
                continue;
            }

            String childPrefix = StringUtils.isBlank(nodePathPrefix) ? childName : nodePathPrefix + "/" + childName;
            collectMatchingPropertiesRecursively(child, searchTerm, propertyFilter, language, childPrefix, matches);
        }
    }

    private void collectMatchingPropertiesInSubtree(JCRNodeWrapper sourceNode,
                                                    String searchTerm,
                                                    List<String> propertyFilter,
                                                    String propertyPrefix,
                                                    List<GqlPropertyMatch> matches) throws RepositoryException {
        matches.addAll(collectMatchingProperties(sourceNode, searchTerm, propertyFilter, propertyPrefix, true));

        NodeIterator children = sourceNode.getNodes();
        while (children.hasNext()) {
            Node childNode = children.nextNode();
            JCRNodeWrapper child = toNodeWrapper(sourceNode, childNode);
            if (child == null) {
                continue;
            }
            String childName = child.getName();
            String childPrefix = propertyPrefix + childName + "/";
            collectMatchingPropertiesInSubtree(child, searchTerm, propertyFilter, childPrefix, matches);
        }
    }

    private List<GqlPropertyMatch> collectMatchingProperties(JCRNodeWrapper sourceNode,
                                                             String searchTerm,
                                                             List<String> propertyFilter,
                                                             String propertyPrefix,
                                                             boolean replaceable) {
        List<GqlPropertyMatch> matches = new ArrayList<>();

        try {
            PropertyIterator properties = sourceNode.getProperties();
            while (properties.hasNext()) {
                Property property = properties.nextProperty();
                String propName = property.getName();

                if (propertyFilter != null && !propertyFilter.contains(propName)) {
                    continue;
                }

                if (property.getType() != PropertyType.STRING) {
                    continue;
                }

                boolean isProtected = property.getDefinition().isProtected();
                boolean isReplaceable = replaceable && !isProtected;
                String label = normalizePropertyLabel(propertyPrefix + propName);

                if (property.isMultiple()) {
                    Value[] values = property.getValues();
                    for (Value value : values) {
                        String raw = value != null ? value.getString() : null;
                        if (containsIgnoreCase(raw, searchTerm)) {
                            matches.add(new GqlPropertyMatch(propName, raw, label, isReplaceable));
                        }
                    }
                    continue;
                }

                String raw = property.getString();
                if (containsIgnoreCase(raw, searchTerm)) {
                    matches.add(new GqlPropertyMatch(propName, raw, label, isReplaceable));
                }
            }
        } catch (RepositoryException e) {
            logger.debug("Unable to inspect matching properties on node {}", sourceNode, e);
        }

        return matches;
    }

    private String normalizePropertyLabel(String rawLabel) {
        if (StringUtils.isBlank(rawLabel)) {
            return rawLabel;
        }

        String[] segments = rawLabel.split("/");
        List<String> filtered = new ArrayList<>();
        for (String segment : segments) {
            if (segment != null && segment.startsWith("j:translation_")) {
                continue;
            }
            filtered.add(segment);
        }

        return String.join("/", filtered).replaceAll("//+", "/");
    }

    private boolean containsIgnoreCase(String value, String searchTerm) {
        if (value == null || searchTerm == null) {
            return false;
        }

        String valueLower = value.toLowerCase(Locale.ROOT);
        String normalizedValue = normalizeTextForSearch(valueLower);
        String termLower = searchTerm.toLowerCase(Locale.ROOT);
        String normalizedTerm = normalizeTextForSearch(termLower);

        if (valueLower.contains(termLower)) {
            return true;
        }

        if (stripDiacritics(valueLower).contains(stripDiacritics(termLower))) {
            return true;
        }

        if (normalizedValue.contains(normalizedTerm)) {
            return true;
        }

        return stripDiacritics(normalizedValue).contains(stripDiacritics(normalizedTerm));
    }

    private String stripDiacritics(String value) {
        if (value == null) {
            return "";
        }

        return DIACRITICS_PATTERN.matcher(Normalizer.normalize(value, Normalizer.Form.NFD)).replaceAll("");
    }

    private String normalizeTextForSearch(String value) {
        if (value == null) {
            return "";
        }

        String unescaped = StringEscapeUtils.unescapeHtml(value);
        String withoutTags = unescaped.replaceAll("<[^>]+>", " ");
        return withoutTags.replaceAll("\\s+", " ").trim();
    }

    private JCRNodeWrapper resolveSearchableNode(JCRNodeWrapper hitNode) throws RepositoryException {
        if (hitNode == null) {
            return null;
        }

        JCRNodeWrapper current = hitNode;
        while (current != null) {
            String currentName = current.getName();
            if (currentName != null && currentName.startsWith("j:translation_")) {
                return current.getParent();
            }

            if ("/".equals(current.getPath()) || "/sites".equals(current.getPath())) {
                break;
            }

            current = current.getParent();
        }

        return hitNode;
    }

    private List<GqlPropertyMatch> dedupeMatches(List<GqlPropertyMatch> matches) {
        Map<String, GqlPropertyMatch> unique = new LinkedHashMap<>();
        for (GqlPropertyMatch match : matches) {
            if (match == null) {
                continue;
            }
            String key = match.getLabel() + "::" + match.getValue();
            unique.putIfAbsent(key, match);
        }
        return new ArrayList<>(unique.values());
    }

    private String resolveDisplayName(JCRNodeWrapper node, String language) throws RepositoryException {
        String translatedTitle = getNodeTitle(getTranslationNode(node, language));
        if (StringUtils.isNotBlank(translatedTitle)) {
            return translatedTitle;
        }

        String localTitle = getNodeTitle(node);
        if (StringUtils.isNotBlank(localTitle)) {
            return localTitle;
        }

        String displayableName = StringUtils.trimToNull(node.getDisplayableName());
        return displayableName != null ? displayableName : node.getName();
    }

    private String getNodeTitle(JCRNodeWrapper node) throws RepositoryException {
        if (node == null || !node.hasProperty("jcr:title")) {
            return null;
        }

        return StringUtils.trimToNull(node.getProperty("jcr:title").getString());
    }

    private boolean matchesDateFilters(JCRNodeWrapper node,
                                       GqlSearchFiltersInput filters,
                                       String language) throws RepositoryException {
        if (filters == null) {
            return true;
        }

        Calendar createdAfter = parseDateFilter(filters.getCreatedAfter(), false);
        Calendar createdBefore = parseDateFilter(filters.getCreatedBefore(), true);
        Calendar modifiedAfter = parseDateFilter(filters.getModifiedAfter(), false);
        Calendar modifiedBefore = parseDateFilter(filters.getModifiedBefore(), true);

        if (createdAfter == null && createdBefore == null && modifiedAfter == null && modifiedBefore == null) {
            return true;
        }

        JCRNodeWrapper translationNode = getTranslationNode(node, language);
        Calendar created = resolveNodeDate(node, translationNode, "jcr:created");
        Calendar modified = resolveNodeDate(node, translationNode, "jcr:lastModified");

        if (createdAfter != null && (created == null || created.before(createdAfter))) {
            return false;
        }
        if (createdBefore != null && (created == null || created.after(createdBefore))) {
            return false;
        }
        if (modifiedAfter != null && (modified == null || modified.before(modifiedAfter))) {
            return false;
        }
        return modifiedBefore == null || (modified != null && !modified.after(modifiedBefore));
    }

    private Calendar resolveNodeDate(JCRNodeWrapper node,
                                     JCRNodeWrapper translationNode,
                                     String propertyName) throws RepositoryException {
        if (translationNode != null && translationNode.hasProperty(propertyName)) {
            return translationNode.getProperty(propertyName).getDate();
        }
        if (node != null && node.hasProperty(propertyName)) {
            return node.getProperty(propertyName).getDate();
        }
        return null;
    }

    private JCRNodeWrapper getTranslationNode(JCRNodeWrapper node, String language) throws RepositoryException {
        if (StringUtils.isBlank(language)) {
            return null;
        }
        String translationNodeName = "j:translation_" + language.replace('-', '_');
        if (node.hasNode(translationNodeName)) {
            return node.getNode(translationNodeName);
        }
        String languageOnly = language.split("[-_]")[0];
        if (StringUtils.isNotBlank(languageOnly)) {
            String fallbackTranslationNodeName = "j:translation_" + languageOnly;
            if (node.hasNode(fallbackTranslationNodeName)) {
                return node.getNode(fallbackTranslationNodeName);
            }
        }
        return null;
    }

    private String findParentContainerPath(JCRNodeWrapper node) throws RepositoryException {
        JCRNodeWrapper current = node.getParent();
        while (current != null) {
            if (current.isNodeType("jnt:page") || current.isNodeType("jnt:contentFolder")) {
                return current.getPath();
            }
            if ("/".equals(current.getPath()) || "/sites".equals(current.getPath())) {
                break;
            }
            current = current.getParent();
        }
        return node.getParent() != null ? node.getParent().getPath() : null;
    }

    private int replaceInNode(JCRNodeWrapper node, String termToReplace, String replacementTerm, String language,
                             List<String> propertiesToReplace, GqlSearchMode searchMode) throws RepositoryException {
        return replaceInNodeRecursively(node, termToReplace, replacementTerm, language, propertiesToReplace, searchMode);
    }

    private int replaceInNodeRecursively(JCRNodeWrapper currentNode, String termToReplace, String replacementTerm,
                                         String language, List<String> propertiesToReplace,
                                         GqlSearchMode searchMode) throws RepositoryException {
        int updatedCount = 0;

        JCRNodeWrapper translationNode = getTranslationNode(currentNode, language);
        if (translationNode != null) {
            updatedCount += replaceInNodeTree(translationNode, termToReplace, replacementTerm, propertiesToReplace, searchMode);
        }

        updatedCount += replaceInSingleNode(currentNode, termToReplace, replacementTerm, propertiesToReplace, searchMode);

        NodeIterator children = currentNode.getNodes();
        while (children.hasNext()) {
            Node childNode = children.nextNode();
            JCRNodeWrapper child = toNodeWrapper(currentNode, childNode);
            if (child == null) {
                continue;
            }
            String childName = child.getName();
            if (childName != null && childName.startsWith("j:translation_")) {
                continue;
            }

            updatedCount += replaceInNodeRecursively(child, termToReplace, replacementTerm, language, propertiesToReplace, searchMode);
        }

        return updatedCount;
    }

    private int replaceInNodeTree(JCRNodeWrapper sourceNode, String termToReplace, String replacementTerm,
                                  List<String> propertiesToReplace, GqlSearchMode searchMode) throws RepositoryException {
        int updatedCount = replaceInSingleNode(sourceNode, termToReplace, replacementTerm, propertiesToReplace, searchMode);

        NodeIterator children = sourceNode.getNodes();
        while (children.hasNext()) {
            Node childNode = children.nextNode();
            JCRNodeWrapper child = toNodeWrapper(sourceNode, childNode);
            if (child == null) {
                continue;
            }
            updatedCount += replaceInNodeTree(child, termToReplace, replacementTerm, propertiesToReplace, searchMode);
        }

        return updatedCount;
    }

    private int replaceInSingleNode(JCRNodeWrapper node, String termToReplace, String replacementTerm,
                                    List<String> propertiesToReplace, GqlSearchMode searchMode) throws RepositoryException {
        int updatedCount = 0;
        PropertyIterator properties = node.getProperties();
        
        while (properties.hasNext()) {
            Property property = properties.nextProperty();
            String propName = property.getName();
            
            if (propertiesToReplace != null && !propertiesToReplace.contains(propName)) {
                continue;
            }
            
            if (property.getType() == PropertyType.STRING && !property.getDefinition().isProtected()) {
                if (property.isMultiple()) {
                    Value[] values = property.getValues();
                    String[] replacedValues = new String[values.length];
                    boolean changed = false;

                    for (int idx = 0; idx < values.length; idx++) {
                        String oldValue = values[idx] != null ? values[idx].getString() : null;
                        String newValue = performReplace(oldValue, termToReplace, replacementTerm, searchMode);
                        replacedValues[idx] = newValue;
                        if (!Objects.equals(oldValue, newValue)) {
                            changed = true;
                        }
                    }

                    if (changed) {
                        property.setValue(replacedValues);
                        updatedCount++;
                    }
                    continue;
                }

                String oldValue = property.getString();
                String newValue = performReplace(oldValue, termToReplace, replacementTerm, searchMode);
                
                if (!oldValue.equals(newValue)) {
                    property.setValue(newValue);
                    updatedCount++;
                }
            }
        }
        
        return updatedCount;
    }

    private JCRNodeWrapper toNodeWrapper(JCRNodeWrapper contextNode, Node node) throws RepositoryException {
        if (node instanceof JCRNodeWrapper) {
            return (JCRNodeWrapper) node;
        }

        Session session = contextNode.getSession();
        if (session instanceof JCRSessionWrapper) {
            return ((JCRSessionWrapper) session).getNodeByIdentifier(node.getIdentifier());
        }

        return null;
    }

    private String performReplace(String text, String searchTerm, String replacement, GqlSearchMode mode) {
        if (text == null || searchTerm == null) {
            return text;
        }

        if (searchTerm.isEmpty()) {
            return text;
        }

        String safeReplacement = replacement == null ? "" : replacement;

        if (mode == GqlSearchMode.REGEX) {
            return text.replaceAll(searchTerm, safeReplacement);
        }

        if (mode == GqlSearchMode.EXACT_MATCH) {
            return text.replace(searchTerm, safeReplacement);
        }

        Pattern caseInsensitivePattern = Pattern.compile(Pattern.quote(searchTerm), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return caseInsensitivePattern.matcher(text).replaceAll(Matcher.quoteReplacement(safeReplacement));
    }

    private Calendar parseDateFilter(String dateFilter, boolean endOfDay) {
        if (StringUtils.isBlank(dateFilter)) {
            return null;
        }

        LocalDate localDate = parseToLocalDate(dateFilter.trim());
        if (localDate == null) {
            logger.warn("Invalid date filter format: {}", dateFilter);
            return null;
        }

        ZonedDateTime zonedDateTime = endOfDay ?
                localDate.atTime(23, 59, 59, 999_000_000).atZone(ZoneId.systemDefault()) :
                localDate.atStartOfDay(ZoneId.systemDefault());
        return GregorianCalendar.from(zonedDateTime);
    }

    private LocalDate parseToLocalDate(String rawValue) {
        if (StringUtils.isBlank(rawValue)) {
            return null;
        }

        List<DateTimeFormatter> dateOnlyFormats = Arrays.asList(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DAY_MONTH_YEAR_SLASH_FORMAT,
                DAY_MONTH_YEAR_DASH_FORMAT,
                MONTH_DAY_YEAR_SLASH_FORMAT
        );

        for (DateTimeFormatter formatter : dateOnlyFormats) {
            try {
                return LocalDate.parse(rawValue, formatter);
            } catch (DateTimeParseException e) {
                // Try next parser
            }
        }

        try {
            return OffsetDateTime.parse(rawValue, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDate();
        } catch (DateTimeParseException e) {
            // Try next parser
        }

        try {
            return ZonedDateTime.parse(rawValue, DateTimeFormatter.ISO_ZONED_DATE_TIME).toLocalDate();
        } catch (DateTimeParseException e) {
            // Try next parser
        }

        try {
            return Instant.parse(rawValue).atZone(ZoneId.systemDefault()).toLocalDate();
        } catch (DateTimeParseException e) {
            // Try next parser
        }

        try {
            return LocalDateTime.parse(rawValue, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toLocalDate();
        } catch (DateTimeParseException e) {
            // Try next parser
        }

        try {
            Date parsedLegacyDate;
            synchronized (DATE_FILTER_FORMAT) {
                parsedLegacyDate = DATE_FILTER_FORMAT.parse(rawValue);
            }
            return parsedLegacyDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        } catch (Exception e) {
            return null;
        }
    }

    private String formatDate(Calendar calendar) {
        if (calendar == null) {
            return null;
        }
        synchronized (ISO_DATE_FORMAT) {
            return ISO_DATE_FORMAT.format(calendar.getTime());
        }
    }
}
