# Search And Replace (Jahia Module)

Production-ready Jahia module to search and replace text in site content, with a Moonstone admin UI and GraphQL backend.

## Scope
- Searches string and string[] properties in a site subtree (`/sites/{siteKey}`).
- Supports language-specific content (`j:translation_{lang}`) with fallback handling.
- Replaces text in selected nodes/properties with preview and result reporting.

## Architecture

### Frontend
- React + Moonstone UI extension.
- Entry points:
  - `src/javascript/init.js`
  - `src/javascript/AdminPanel.register.js`
  - `src/javascript/AdminPanel/AdminPanel.routes.jsx`
- Main app:
  - `src/javascript/SearchAndReplace/SearchAndReplace.jsx`
  - `src/javascript/SearchAndReplace/components/*`
- Shared HTML-safe highlighting utilities:
  - `src/javascript/SearchAndReplace/utils/highlight.utils.jsx`

### Backend
- GraphQL extensions:
  - `src/main/java/org/jahia/modules/searchandreplace/graphql/SearchAndReplaceQueryExtension.java`
  - `src/main/java/org/jahia/modules/searchandreplace/graphql/SearchAndReplaceMutationExtension.java`
- Core operations service (OSGi-managed):
  - `src/main/java/org/jahia/modules/searchandreplace/graphql/SearchAndReplaceOperations.java`

## Registration and Compatibility
- GraphQL `searchAndReplace` fields (Query + Mutation) return a new `SearchAndReplaceOperations` instance.
- `SearchAndReplaceOperations` receives required Jahia services through `@Inject` + `@GraphQLOsgiService` (setter injection).
- This follows the same extension pattern used in Jahia modules such as `vote-service` and keeps service wiring compatible with DX GraphQL.

## Security Hardening
- Input validation:
  - `siteKey`, `language`, `nodeType`, and property names are validated/sanitized.
- HTML rendering safety in UI preview/results:
  - strips `script/iframe/object/embed`
  - strips inline event handlers (`on*`)
  - strips `javascript:` URLs in `href/src`
- Replace safety:
  - safe replacement handling for case-insensitive mode (`Matcher.quoteReplacement`).
  - regex mode pattern validation before execution.
- Site boundary enforcement:
  - replacements are blocked for nodes outside `/sites/{siteKey}/`.

## Performance Guardrails
- Search has no frontend query cap by default; server can still accept an optional `limit` argument.
- Candidate scan cap prevents unbounded full-site traversal (`MAX_SCAN_CANDIDATES`).
- Fallback scan only runs when indexed query yields no match.
- Search term length is bounded to prevent pathological input.

Current defaults (backend):
- `MAX_SCAN_CANDIDATES = 250000`
- `MAX_SEARCH_TERM_LENGTH = 512`

## Build

### Full Maven build (recommended)
```bash
mvn clean install
```

### Frontend-only build
```bash
node --max_old_space_size=2048 ./node_modules/.bin/webpack --mode=production
```

## Deploy
- Built artifact: `target/search-and-replace-1.0.0-SNAPSHOT.jar`
- Deploy through Jahia Module Manager or module deployment folder.

## Usage
1. Open the module from content tools.
2. Choose a search language.
3. Search text.
4. Optionally apply filters.
5. Select nodes and run replace preview.
6. Execute replacement on selected properties/nodes.

## Troubleshooting

### `Field 'searchAndReplace' in type 'Mutation' is undefined`
- Ensure the new jar is deployed and active.
- Verify `SearchAndReplaceMutationExtension` is loaded.

### No results for text that exists in richtext
- Confirm the selected language matches the content translation.
- Confirm module version includes fallback scan behavior and recursive wrapper traversal fixes.

### Build fails on ESLint prop order (`react/jsx-sort-props`)
- Ensure reserved JSX props (for example `dangerouslySetInnerHTML`) are listed before non-reserved props.

### Yarn mismatch in local shell
- Maven build uses Yarn 1 via `frontend-maven-plugin`.
- Local shell may use Corepack/Yarn 4; prefer Maven build for release artifacts.

## Localization
- English: `src/main/resources/javascript/locales/en.json`
- French: `src/main/resources/javascript/locales/fr.json`

## Legacy docs
- `QUICKSTART.md`, `README_IMPLEMENTATION.md`, and `IMPLEMENTATION_SUMMARY.md` are retained for history.
- Use this `README.md` as the source of truth.
