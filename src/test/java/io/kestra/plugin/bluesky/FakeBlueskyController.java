package io.kestra.plugin.bluesky;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

/**
 * Fake Bluesky API controller used in integration tests.
 * Stubs the two AT Protocol endpoints used by {@link BlueskyTemplate}:
 * createSession (authentication) and createRecord (post creation).
 */
@Controller("/xrpc")
public class FakeBlueskyController {

    /** Last payload received by the createRecord endpoint (body as string). */
    public static volatile String lastPostBody;

    @Post("/com.atproto.server.createSession")
    @Consumes(MediaType.APPLICATION_JSON)
    public HttpResponse<String> createSession(@Body String body) {
        return HttpResponse.ok("""
            {
              "did": "did:plc:testdid12345",
              "handle": "testhandle.bsky.social",
              "accessJwt": "test-access-jwt",
              "refreshJwt": "test-refresh-jwt"
            }
            """);
    }

    @Post("/com.atproto.repo.createRecord")
    @Consumes(MediaType.APPLICATION_JSON)
    public HttpResponse<String> createRecord(@Body String body) {
        lastPostBody = body;
        return HttpResponse.ok("""
            {
              "uri": "at://did:plc:testdid12345/app.bsky.feed.post/abc123",
              "cid": "bafyreidef456"
            }
            """);
    }
}
