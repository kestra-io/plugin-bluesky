# How to use the Bluesky plugin

Post execution alerts and flow summaries to Bluesky from Kestra flows.

## Authentication

All tasks require `identifier` (your Bluesky handle, e.g. `myhandle.bsky.social`, or email address, required) and `appPassword` (a Bluesky app password generated from Settings → Privacy and Security → App Passwords; never use your main account password, required). Optionally set `baseUrl` (the Bluesky PDS base URL, default `https://bsky.social`) and `options` for HTTP client configuration (connect timeout, read timeout, custom headers). Store secrets in [secrets](https://kestra.io/docs/concepts/secret) and apply connection properties globally with [plugin defaults](https://kestra.io/docs/workflow-components/plugin-defaults).

## Tasks

`BlueskyExecution` posts a structured execution summary — set `identifier` and `appPassword` (both required). It is designed for use with a [Flow trigger](https://kestra.io/docs/workflow-components/triggers) in a dedicated monitoring namespace that reacts to failures in other namespaces. Optionally set `customMessage` and `customFields` (key-value pairs appended to the summary) to augment the notification, or override `executionId` (default `{{ execution.id }}`). Posts are capped at Bluesky's 300-grapheme limit.
