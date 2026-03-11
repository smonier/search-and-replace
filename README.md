# Search And Replace (Jahia Module)

Search-and-replace module for Jahia Content Editor tools.

It provides:
- A Moonstone-based admin UI integrated in JContent.
- A DX GraphQL API extension for search, preview, and replacement operations.
- Language-aware search/replacement through `j:translation_<lang>` content.
- Recursive matching across string and multi-string properties, including rich text content.

## 1. Module Overview

### Main use cases
- Find a term across a site subtree.
- Filter results by content type and date range.
- Preview and apply replacement on selected nodes/properties.
- Work in multilingual sites by choosing the target language.

### Current workspace behavior
- Search and replacement execute in the **`default` (EDIT)** JCR workspace.
- They do not execute against `live`.

## 2. Functional Capabilities

### Search
- Scope: descendants of `/sites/{siteKey}`.
- Language: selected in UI, mapped to translation node `j:translation_<language>`.
- Matching:
  - Case-insensitive matching.
  - Accent/diacritics tolerant matching.
  - HTML-aware text normalization (richtext content is searchable).
- Property types scanned:
  - `STRING`
  - Multi-valued `STRING[]`
- Filters:
  - `nodeType`
  - `createdAfter`, `createdBefore`
  - `modifiedAfter`, `modifiedBefore`
  - `properties` (optional property-name whitelist)

### Replace
- Supports `EXACT_MATCH`, `CASE_INSENSITIVE`, and `REGEX`.
- Replaces only non-protected `STRING` / `STRING[]` properties.
- Supports property-level targeting (`propertiesToReplace`).
- Recurses through selected nodes and their descendants.
- For each visited node:
  - Applies replacement in selected language translation subtree first (if exists).
  - Applies replacement on the node itself.

### UI
- Language dropdown based on site active languages.
- Content-type dropdown based on editorial types and current result set.
- Date filters (start/end for created and modified).
- Result table with:
  - Full path
  - Parent container link (page/content-folder)
  - Match counters (`replaceable / total`)
  - Expandable row with matching properties preview
- Replace modal with preview and property selection.

## 3. Architecture

### Frontend (React + Moonstone)
- Registration bootstrap:
  - `src/javascript/init.js`
  - `src/javascript/AdminPanel.register.js`
- Route + app registration:
  - `src/javascript/AdminPanel/AdminPanel.routes.jsx`
- Main feature:
  - `src/javascript/SearchAndReplace/SearchAndReplace.jsx`
  - `src/javascript/SearchAndReplace/components/*`
  - `src/javascript/SearchAndReplace/utils/highlight.utils.jsx`

### Backend (GraphQL + OSGi + JCR)
- GraphQL extensions:
  - `src/main/java/org/jahia/modules/searchandreplace/graphql/SearchAndReplaceQueryExtension.java`
  - `src/main/java/org/jahia/modules/searchandreplace/graphql/SearchAndReplaceMutationExtension.java`
- Provider registration:
  - `src/main/java/org/jahia/modules/searchandreplace/graphql/SearchAndReplaceExtensionsProvider.java`
- Core service:
  - `src/main/java/org/jahia/modules/searchandreplace/graphql/SearchAndReplaceOperations.java`

## 4. GraphQL Contract

The module adds `searchAndReplace` on both `Query` and `Mutation`.

### Query operations
- `searchNodes(termToSearch, siteKey, language, filters, limit)`
- `getNodeTypes(siteKey)`
- `getNodeTypeProperties(nodeType)`

### Mutation operations
- `replaceInNodes(siteKey, nodeUuids, termToReplace, replacementTerm, language, propertiesToReplace, searchMode)`
- `previewReplace(nodeUuid, termToReplace, replacementTerm, propertiesToReplace)`

### Input/Enums
- `InputSearchFiltersInput` (generated from `SearchFiltersInput`)
  - `nodeType`
  - `createdBefore`
  - `createdAfter`
  - `modifiedBefore`
  - `modifiedAfter`
  - `properties`
- `SearchMode`
  - `EXACT_MATCH`
  - `CASE_INSENSITIVE`
  - `REGEX`

### Example search query
```graphql
query SearchNodesQuery(
  $termToSearch: String!
  $siteKey: String!
  $language: String!
  $filters: InputSearchFiltersInput
) {
  searchAndReplace {
    searchNodes(
      termToSearch: $termToSearch
      siteKey: $siteKey
      language: $language
      filters: $filters
    ) {
      totalCount
      truncated
      nodes {
        uuid
        path
        displayName
        nodeType
        created
        lastModified
        parentContainerPath
        matchingProperties {
          name
          label
          value
          replaceable
        }
      }
    }
  }
}
```

### Example replace mutation
```graphql
mutation ReplaceInNodesMutation(
  $siteKey: String!
  $nodeUuids: [String!]!
  $termToReplace: String!
  $replacementTerm: String!
  $language: String!
  $propertiesToReplace: [String!]
  $searchMode: SearchMode
) {
  searchAndReplace {
    replaceInNodes(
      siteKey: $siteKey
      nodeUuids: $nodeUuids
      termToReplace: $termToReplace
      replacementTerm: $replacementTerm
      language: $language
      propertiesToReplace: $propertiesToReplace
      searchMode: $searchMode
    ) {
      successfulNodes
      failedNodes
      totalPropertiesUpdated
      errors {
        nodeUuid
        nodePath
        message
      }
    }
  }
}
```

## 5. Security Notes

- User inputs are validated/sanitized (`siteKey`, `language`, `nodeType`, property names, regex pattern).
- Replacement is hard-bounded to the requested site path (`/sites/{siteKey}/`).
- HTML shown in previews/results is sanitized (scriptable elements and unsafe attributes removed).
- Replacement uses safe regex replacement escaping in case-insensitive mode.

Important:
- Operations are executed with system session helper APIs. Access to this tool should remain restricted to trusted back-office users.

## 6. Performance Notes

- Search uses:
  - Indexed full-text query first.
  - Fallback traversal query if indexed query returns no usable result.
- Result processing is deduplicated per resolved content node.
- Guardrails:
  - `MAX_SCAN_CANDIDATES = 250000`
  - `MAX_SEARCH_TERM_LENGTH = 512`
  - `MAX_FILTER_PROPERTIES = 500`
- `limit` can be provided in GraphQL API, but UI currently does not send it.

## 7. Date Filter Behavior

- Date filters can be provided as:
  - `yyyy-MM-dd`
  - `dd/MM/yyyy`
  - `dd-MM-yyyy`
  - `MM/dd/yyyy`
  - ISO datetime formats
- `start` dates are interpreted as start-of-day.
- `end` dates are interpreted as end-of-day.

## 8. Build and Packaging

### Prerequisites
- Java 11+
- Maven 3.8+

### Standard build
```bash
mvn clean install
```

This build:
- Installs Node `v20.18.0` and Yarn `v1.22.10` via `frontend-maven-plugin`.
- Runs frontend build and produces module assets.
- Packages OSGi bundle:
  - `target/search-and-replace-1.0.0-SNAPSHOT.jar`

### Frontend-only build (local)
```bash
node --max_old_space_size=2048 ./node_modules/.bin/webpack --mode=production
```

## 9. Deployment

- Deploy `target/search-and-replace-1.0.0-SNAPSHOT.jar` using Jahia Module Manager or your deployment pipeline.
- Ensure the module is installed on the target site (`requireModuleInstalledOnSite: "search-and-replace"` is enforced in UI registration).

## 10. Developer Commands

```bash
yarn lint
yarn lint:fix
yarn dev
yarn build
mvn -q -DskipTests compile
```

## 11. Troubleshooting

### `Field 'searchAndReplace' in type 'Mutation' is undefined`
- Deployed bundle does not match current code or extension is not loaded.
- Check module state and restart/redeploy if needed.

### Query validation error on input type
- Use `InputSearchFiltersInput` in GraphQL queries (generated input type name).

### No result for known richtext keyword
- Verify the selected language matches the translated content language.
- Validate the keyword with accents/diacritics exactly as expected; matching is accent-tolerant but language context still matters.

### Replacement fails on protected/read-only properties
- Protected properties are intentionally excluded from replacement.

### Local `yarn build:production` fails with lockfile/workspace mismatch
- Prefer Maven build (`mvn clean install`) which uses the module-pinned Yarn 1 toolchain.

## 12. Localization

- English: `src/main/resources/javascript/locales/en.json`
- French: `src/main/resources/javascript/locales/fr.json`

## 13. Repository Structure (Quick Map)

```text
src/main/java/org/jahia/modules/searchandreplace/graphql/   # GraphQL API + operations
src/main/resources/javascript/locales/                      # i18n labels
src/javascript/AdminPanel/                                  # Jahia route registration
src/javascript/SearchAndReplace/                            # UI feature (search, filters, table, replace modal)
```

## 14. Legacy Docs

`QUICKSTART.md`, `README_IMPLEMENTATION.md`, and `IMPLEMENTATION_SUMMARY.md` are retained for historical context.
This `README.md` is the authoritative functional and technical documentation.
