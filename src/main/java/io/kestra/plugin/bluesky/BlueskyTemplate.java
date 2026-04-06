package io.kestra.plugin.bluesky;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.io.IOUtils;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString(exclude = "appPassword")
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class BlueskyTemplate extends AbstractBlueskyConnection {

    private static final int MAX_POST_GRAPHEMES = 300;
    private static final String CREATE_SESSION_PATH = "/xrpc/com.atproto.server.createSession";
    private static final String CREATE_RECORD_PATH = "/xrpc/com.atproto.repo.createRecord";
    private static final String POST_COLLECTION = "app.bsky.feed.post";

    @Schema(title = "Bluesky identifier", description = "Your Bluesky handle (e.g. `myhandle.bsky.social`) or email address used to authenticate.")
    @NotNull
    @PluginProperty(group = "main")
    protected Property<String> identifier;

    @Schema(title = "App password", description = """
        A Bluesky app password generated from Settings > Privacy and Security > App Passwords.
        Never use your main account password here.
        """)
    @NotNull
    @PluginProperty(group = "main")
    protected Property<String> appPassword;

    @Schema(title = "Base URL", description = "The base URL of the Bluesky PDS (Personal Data Server). Defaults to `https://bsky.social`.")
    @Builder.Default
    @PluginProperty(group = "connection")
    protected Property<String> baseUrl = Property.ofValue("https://bsky.social");

    @Schema(title = "Template URI", description = "Classpath Pebble template URI used to render the post body", hidden = true)
    @PluginProperty(hidden = true, group = "advanced")
    protected Property<String> templateUri;

    @Schema(title = "Template variables", description = "Key-value map rendered and injected into the template before sending")
    @PluginProperty(group = "advanced")
    protected Property<Map<String, Object>> templateRenderMap;

    @Schema(title = "Post text body", description = "Direct post text that bypasses the template; must fit within 300 graphemes")
    @PluginProperty(group = "advanced")
    protected Property<String> textBody;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rIdentifier = runContext.render(this.identifier).as(String.class).orElseThrow();
        var rAppPassword = runContext.render(this.appPassword).as(String.class).orElseThrow();
        var rBaseUrl = runContext.render(this.baseUrl).as(String.class).orElse("https://bsky.social");

        var rPostText = getPostText(runContext);

        // Bluesky's limit is 300 graphemes; we count by code points as a reasonable proxy
        var graphemeCount = rPostText.codePointCount(0, rPostText.length());
        if (graphemeCount > MAX_POST_GRAPHEMES) {
            throw new IllegalArgumentException(
                String.format(
                    "Post message exceeds maximum length of %d graphemes. Current length: %d",
                    MAX_POST_GRAPHEMES, graphemeCount
                )
            );
        }

        try (var client = new HttpClient(runContext, super.httpClientConfigurationWithOptions())) {
            // Step 1: authenticate and obtain the access JWT + DID
            logger.debug("Authenticating to Bluesky as '{}'", rIdentifier);
            var session = createSession(runContext, client, rBaseUrl, rIdentifier, rAppPassword);
            logger.info("Authenticated to Bluesky, DID: {}", session.did);

            // Step 2: create the post record
            logger.debug("Posting to Bluesky");
            createRecord(runContext, client, rBaseUrl, session.accessJwt, session.did, rPostText);
            logger.info("Bluesky post sent successfully");
        }

        return null;
    }

    private String getPostText(RunContext runContext) throws Exception {
        final var rTemplateUri = runContext.render(this.templateUri).as(String.class);

        if (rTemplateUri.isPresent()) {
            var resourceStream = this.getClass().getClassLoader().getResourceAsStream(rTemplateUri.get());
            if (resourceStream == null) {
                throw new IllegalArgumentException("Template resource not found: " + rTemplateUri.get());
            }
            var template = IOUtils.toString(resourceStream, StandardCharsets.UTF_8);
            var rTemplateVars = runContext.render(templateRenderMap).asMap(String.class, Object.class);
            return runContext.render(template, rTemplateVars);
        }

        return runContext.render(this.textBody).as(String.class).orElse("");
    }

    private BlueskySession createSession(RunContext runContext, HttpClient client, String baseUrl,
        String rIdentifier, String rAppPassword) throws Exception {

        var payload = JacksonMapper.ofJson().writeValueAsString(
            Map.of("identifier", rIdentifier, "password", rAppPassword)
        );

        var request = createRequestBuilder(runContext)
            .uri(URI.create(baseUrl + CREATE_SESSION_PATH))
            .method("POST")
            .addHeader("Content-Type", "application/json")
            .body(HttpRequest.StringRequestBody.builder().content(payload).build())
            .build();

        try {
            HttpResponse<String> response = client.request(request, String.class);
            return JacksonMapper.ofJson().readValue(response.getBody(), BlueskySession.class);
        } catch (HttpClientResponseException e) {
            throw new IllegalStateException(
                "Bluesky authentication failed (HTTP " + e.getResponse().getStatus().getCode() + "): " + e.getMessage(),
                e
            );
        }
    }

    private void createRecord(RunContext runContext, HttpClient client, String baseUrl,
        String accessJwt, String did, String text) throws Exception {

        var record = new LinkedHashMap<String, Object>();
        record.put("$type", POST_COLLECTION);
        record.put("text", text);
        record.put("createdAt", Instant.now().toString());

        var body = new LinkedHashMap<String, Object>();
        body.put("repo", did);
        body.put("collection", POST_COLLECTION);
        body.put("record", record);

        var payload = JacksonMapper.ofJson().writeValueAsString(body);

        var request = createRequestBuilder(runContext)
            .uri(URI.create(baseUrl + CREATE_RECORD_PATH))
            .method("POST")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer " + accessJwt)
            .body(HttpRequest.StringRequestBody.builder().content(payload).build())
            .build();

        try {
            client.request(request, String.class);
        } catch (HttpClientResponseException e) {
            throw new IllegalStateException(
                "Bluesky post creation failed (HTTP " + e.getResponse().getStatus().getCode() + "): " + e.getMessage(),
                e
            );
        }
    }

    // -------------------------------------------------------------------------
    // Internal deserialization types (AT Protocol response shapes)
    // -------------------------------------------------------------------------

    /** Session returned by {@code com.atproto.server.createSession}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class BlueskySession {
        @JsonProperty("did")
        public String did;
        @JsonProperty("handle")
        public String handle;
        @JsonProperty("accessJwt")
        public String accessJwt;
        @JsonProperty("refreshJwt")
        public String refreshJwt;
    }

    /** Record creation response from {@code com.atproto.repo.createRecord}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class BlueskyRecordResponse {
        @JsonProperty("uri")
        public String uri;
        @JsonProperty("cid")
        public String cid;
    }
}
