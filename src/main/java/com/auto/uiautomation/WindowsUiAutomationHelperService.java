package com.auto.uiautomation;

import com.auto.config.UiAutomationActionConfig;
import com.auto.config.UiAutomationConfig;
import com.auto.config.UiAutomationRuleConfig;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class WindowsUiAutomationHelperService implements UiAutomationService {
    private final ProcessInvoker processInvoker;

    public WindowsUiAutomationHelperService() {
        this(new DefaultProcessInvoker());
    }

    WindowsUiAutomationHelperService(ProcessInvoker processInvoker) {
        this.processInvoker = processInvoker;
    }

    @Override
    public UiAutomationCommandResult inspectWindow(String windowTitleContains, UiAutomationConfig config) {
        JSONObject request = new JSONObject()
                .put("operation", "inspectWindow")
                .put("windowTitleContains", windowTitleContains);
        return parseResponse(processInvoker.invoke(config.helperCommand(), request.toString(), config.timeoutMs()));
    }

    @Override
    public UiAutomationCommandResult executeRule(UiAutomationRuleConfig rule, UiAutomationConfig config) {
        JSONObject request = new JSONObject()
                .put("operation", "executeRule")
                .put("windowTitleContains", rule.windowTitleContains())
                .put("actions", serializeActions(rule.actions()));
        return parseResponse(processInvoker.invoke(config.helperCommand(), request.toString(), config.timeoutMs()));
    }

    @Override
    public List<UiAutomationRuleExecution> executeRules(UiAutomationConfig config) {
        if (!config.enabled()) {
            return List.of();
        }
        List<UiAutomationRuleExecution> executions = new ArrayList<>();
        for (UiAutomationRuleConfig rule : config.rules()) {
            UiAutomationCommandResult result = executeRule(rule, config);
            executions.add(new UiAutomationRuleExecution(rule.name(), result.success(), result.message()));
        }
        return executions;
    }

    static UiAutomationCommandResult parseResponse(String json) {
        JSONObject object = new JSONObject(json);
        JSONArray elementsArray = object.optJSONArray("elements");
        List<UiAutomationElement> elements = new ArrayList<>();
        if (elementsArray != null) {
            for (int i = 0; i < elementsArray.length(); i++) {
                JSONObject item = elementsArray.getJSONObject(i);
                elements.add(new UiAutomationElement(
                        item.optString("name", ""),
                        item.optString("automationId", ""),
                        item.optString("controlType", "")
                ));
            }
        }
        return new UiAutomationCommandResult(
                object.optBoolean("success", false),
                object.optString("message", ""),
                elements
        );
    }

    private static JSONArray serializeActions(List<UiAutomationActionConfig> actions) {
        JSONArray array = new JSONArray();
        for (UiAutomationActionConfig action : actions) {
            array.put(new JSONObject()
                    .put("controlType", action.controlType())
                    .put("name", action.name())
                    .put("automationId", action.automationId())
                    .put("pattern", action.pattern().name())
                    .put("value", action.value()));
        }
        return array;
    }

    interface ProcessInvoker {
        String invoke(List<String> command, String requestJson, int timeoutMs);
    }

    static final class DefaultProcessInvoker implements ProcessInvoker {
        @Override
        public String invoke(List<String> command, String requestJson, int timeoutMs) {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            try {
                Process process = processBuilder.start();
                try (Writer writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
                    writer.write(requestJson);
                }
                boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    throw new IllegalStateException("UI Automation helper timed out");
                }
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (output.isBlank()) {
                    throw new IllegalStateException("UI Automation helper returned no output");
                }
                return output;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Failed to execute UI Automation helper", e);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to execute UI Automation helper", e);
            }
        }
    }
}
