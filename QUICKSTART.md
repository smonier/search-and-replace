# Quick Start

Use `README.md` for full and up-to-date documentation.

## Build and deploy
```bash
mvn clean install
```

Deploy:
- `target/search-and-replace-1.0.0-SNAPSHOT.jar`

## Local frontend build
```bash
node --max_old_space_size=2048 ./node_modules/.bin/webpack --mode=production
```
