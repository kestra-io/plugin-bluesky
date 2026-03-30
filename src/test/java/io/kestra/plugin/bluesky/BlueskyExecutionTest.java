package io.kestra.plugin.bluesky;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.TestRunner;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;

@KestraTest
public class BlueskyExecutionTest extends AbstractBlueskyTest {

    @Inject
    protected TestRunner runner;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    @BeforeEach
    protected void init() throws IOException, URISyntaxException {
        repositoryLoader
            .load(Objects.requireNonNull(BlueskyExecutionTest.class.getClassLoader().getResource("flows")));
        this.runner.run();
    }

    @Test
    void flow_failedExecutionSendsNotification() throws Exception {
        var execution = runAndCaptureExecution(
            "main-flow-that-fails",
            "bluesky"
        );

        var receivedBody = waitForPostBody(() -> FakeBlueskyController.lastPostBody, 5000);

        assertThat(receivedBody, notNullValue());
        assertThat(receivedBody, containsString(execution.getId()));
        assertThat(receivedBody, containsString("app.bsky.feed.post"));
        assertThat(receivedBody, containsString("FAILED"));
        assertThat(receivedBody, containsString("Environment: DEV"));
        assertThat(receivedBody, containsString("Cloud: GCP"));
        assertThat(receivedBody, containsString("myCustomMessage"));
    }
}
