# Kestra Template Plugin

## What

- Provides plugin components under `io.kestra.plugin.bluesky`.
- Includes classes such as `BlueskyTemplate`, `BlueskyExecution`.

## Why

- What user problem does this solve? Teams need to post messages to Bluesky (AT Protocol) from orchestrated workflows instead of relying on manual console work, ad hoc scripts, or disconnected schedulers.
- Why would a team adopt this plugin in a workflow? It keeps Bluesky steps in the same Kestra flow as upstream preparation, approvals, retries, notifications, and downstream systems.
- What operational/business outcome does it enable? It reduces manual handoffs and fragmented tooling while improving reliability, traceability, and delivery speed for processes that depend on Bluesky.

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
