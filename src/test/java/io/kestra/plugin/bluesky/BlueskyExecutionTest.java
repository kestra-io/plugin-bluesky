package io.kestra.plugin.bluesky;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.runners.TestRunner;
import io.kestra.core.serializers.YamlParser;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

@KestraTest
public class BlueskyExecutionTest extends AbstractBlueskyTest {

    @Inject
    protected TestRunner runner;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    @Inject
    private ModelValidator modelValidator;

    @Inject
    private RunContextFactory runContextFactory;

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

    @Test
    void flow_textBodyTakesPrecedenceOverTemplate() throws Exception {
        this.runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "bluesky-textbody");

        var receivedBody = waitForPostBody(() -> FakeBlueskyController.lastPostBody, 5000);

        assertThat(receivedBody, notNullValue());
        assertThat(receivedBody, containsString("Direct text body bypassing the template"));
        // textBody bypasses the template entirely, so template-only fields must not appear
        assertThat(receivedBody, not(containsString("myCustomMessage")));
        assertThat(receivedBody, not(containsString("Environment: DEV")));
    }

    @Test
    void flow_blankTextBodyFallsBackToTemplate() throws Exception {
        var execution = this.runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "bluesky-textbody-blank");

        var receivedBody = waitForPostBody(() -> FakeBlueskyController.lastPostBody, 5000);

        assertThat(receivedBody, notNullValue());
        assertThat(receivedBody, containsString(execution.getId()));
        assertThat(receivedBody, containsString("myCustomMessage"));
        assertThat(receivedBody, containsString("Environment: DEV"));
    }

    @SuppressWarnings("deprecation")
    @Test
    void flowSettingDeprecatedOptionsStillValidatesAndRuns() throws Exception {
        var flow = YamlParser.parse(
            new File(
                Objects.requireNonNull(
                    BlueskyExecutionTest.class.getClassLoader().getResource("flows/bluesky-deprecated-options.yaml")
                ).toURI()
            ),
            Flow.class
        );

        assertThat(modelValidator.isValid(flow).isEmpty(), is(true));

        var runContext = runContextFactory.of(Map.of());
        var options = ((BlueskyExecution) flow.getTasks().getFirst()).getOptions();
        assertThat(runContext.render(options.getReadTimeout()).as(Duration.class).orElseThrow(), is(Duration.ofSeconds(30)));
        assertThat(runContext.render(options.getConnectionPoolIdleTimeout()).as(Duration.class).orElseThrow(), is(Duration.ofSeconds(10)));
        assertThat(runContext.render(options.getMaxContentLength()).as(Integer.class).orElseThrow(), is(1024));

        var execution = this.runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "bluesky-deprecated-options");
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        var receivedBody = waitForPostBody(() -> FakeBlueskyController.lastPostBody, 5000);

        assertThat(receivedBody, containsString("Sent with deprecated HTTP options"));
    }
}
