package io.kestra.plugin.bluesky;

import java.util.Map;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.plugins.notifications.ExecutionInterface;
import io.kestra.core.plugins.notifications.ExecutionService;
import io.kestra.core.runners.RunContext;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.annotations.PluginProperty;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Post execution summary to Bluesky",
    description = """
        Renders the bundled bluesky.peb template with execution details (id, namespace, link, status, timings) \
        plus custom fields/message, then sends it to Bluesky via identifier and app password with a 300-grapheme cap.\
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Send a Bluesky notification on a failed flow execution.",
            full = true,
            code = """
                id: failure_alert_bluesky
                namespace: company.team

                tasks:
                  - id: send_bluesky_alert
                    type: io.kestra.plugin.bluesky.BlueskyExecution
                    identifier: "myhandle.bsky.social"
                    appPassword: "{{ secret('BLUESKY_APP_PASSWORD') }}"
                    executionId: "{{ trigger.executionId ?? execution.id }}"
                    customMessage: "Production workflow failed - immediate attention required!"
                    customFields:
                      Environment: "Production"
                      Team: "DevOps"
                      Priority: "High"

                triggers:
                  - id: failed_prod_workflows
                    type: io.kestra.plugin.core.trigger.Flow
                    conditions:
                      - type: io.kestra.plugin.core.condition.ExecutionStatus
                        in:
                          - FAILED
                          - WARNING
                      - type: io.kestra.plugin.core.condition.ExecutionNamespace
                        namespace: prod
                        prefix: true
                """
        ),
        @Example(
            title = "Send a Bluesky notification on a successful flow execution.",
            full = true,
            code = """
                id: success_alert_bluesky
                namespace: company.team

                tasks:
                  - id: send_bluesky_success
                    type: io.kestra.plugin.bluesky.BlueskyExecution
                    identifier: "myhandle.bsky.social"
                    appPassword: "{{ secret('BLUESKY_APP_PASSWORD') }}"
                    executionId: "{{ trigger.executionId ?? execution.id }}"
                    customMessage: "Deployment completed successfully!"
                    options:
                      readTimeout: PT5S
                      headers:
                        X-Datacenter: "eu-west-1"

                triggers:
                  - id: successful_deployments
                    type: io.kestra.plugin.core.trigger.Flow
                    conditions:
                      - type: io.kestra.plugin.core.condition.ExecutionStatus
                        in:
                          - SUCCESS
                """
        )
    }
)
public class BlueskyExecution extends BlueskyTemplate implements ExecutionInterface {

    @Schema(
        title = "Execution ID",
        description = "Execution to include in the template; defaults to the current execution and should be set to {{ trigger.executionId ?? execution.id }} when called from a Flow trigger"
    )
    @Builder.Default
    @PluginProperty(group = "advanced")
    private final Property<String> executionId = Property.ofExpression("{{ execution.id }}");

    @Schema(title = "Custom fields", description = "Key-value pairs appended to the rendered template output")
    @PluginProperty(group = "advanced")
    private Property<Map<String, Object>> customFields;

    @Schema(title = "Custom message", description = "Optional extra text appended to the template output")
    @PluginProperty(group = "advanced")
    private Property<String> customMessage;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        this.templateUri = Property.ofValue("bluesky.peb");
        this.templateRenderMap = Property.ofValue(ExecutionService.executionMap(runContext, this));
        return super.run(runContext);
    }
}
