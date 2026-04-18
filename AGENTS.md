# Kestra Template Plugin

## What

- Provides plugin components under `io.kestra.plugin.bluesky`.
- Includes classes such as `BlueskyTemplate`, `BlueskyExecution`.

## Why

- This plugin integrates Kestra with Bluesky.
- It provides tasks that post messages to Bluesky (AT Protocol).

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin`:

- `templates`

Infrastructure dependencies (Docker Compose services):

- `app`

### Key Plugin Classes

- `io.kestra.plugin.templates.Example`

### Project Structure

```
plugin-template/
├── src/main/java/io/kestra/plugin/templates/
├── src/test/java/io/kestra/plugin/templates/
├── build.gradle
└── README.md
```

## Local rules

- Base the wording on the implemented packages and classes, not on template README text.

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
