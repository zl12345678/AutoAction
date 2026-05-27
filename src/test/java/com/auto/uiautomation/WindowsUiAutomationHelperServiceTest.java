package com.auto.uiautomation;

import com.auto.config.UiAutomationActionConfig;
import com.auto.config.UiAutomationConfig;
import com.auto.config.UiAutomationPattern;
import com.auto.config.UiAutomationRuleConfig;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WindowsUiAutomationHelperServiceTest {
    @Test
    public void parseResponseReadsElements() {
        UiAutomationCommandResult result = WindowsUiAutomationHelperService.parseResponse("""
                {
                  "success": true,
                  "message": "ok",
                  "elements": [
                    {
                      "name": "OK",
                      "automationId": "confirmButton",
                      "controlType": "Button"
                    }
                  ]
                }
                """);

        assertTrue(result.success());
        assertEquals("ok", result.message());
        assertEquals(1, result.elements().size());
        assertEquals("OK", result.elements().get(0).name());
        assertEquals("confirmButton", result.elements().get(0).automationId());
        assertEquals("Button", result.elements().get(0).controlType());
    }

    @Test
    public void executeRulesBuildsHelperRequestAndMapsResult() {
        AtomicReference<List<String>> commandRef = new AtomicReference<>();
        AtomicReference<String> requestRef = new AtomicReference<>();
        AtomicInteger timeoutRef = new AtomicInteger();
        WindowsUiAutomationHelperService service = new WindowsUiAutomationHelperService((command, requestJson, timeoutMs) -> {
            commandRef.set(command);
            requestRef.set(requestJson);
            timeoutRef.set(timeoutMs);
            return "{\"success\":true,\"message\":\"clicked\",\"elements\":[]}";
        });

        UiAutomationRuleConfig rule = new UiAutomationRuleConfig(
                "dismiss-confirmation",
                "Confirm",
                List.of(new UiAutomationActionConfig("Button", "OK", "confirmButton", UiAutomationPattern.INVOKE, ""))
        );
        UiAutomationConfig config = new UiAutomationConfig(
                true,
                List.of("powershell", "-File", "helper.ps1"),
                2_000,
                List.of(rule)
        );

        List<UiAutomationRuleExecution> executions = service.executeRules(config);

        assertEquals(List.of("powershell", "-File", "helper.ps1"), commandRef.get());
        assertEquals(2_000, timeoutRef.get());
        assertEquals(1, executions.size());
        assertTrue(executions.get(0).success());
        assertEquals("dismiss-confirmation", executions.get(0).ruleName());
        assertEquals("clicked", executions.get(0).message());

        JSONObject request = new JSONObject(requestRef.get());
        assertEquals("executeRule", request.getString("operation"));
        assertEquals("Confirm", request.getString("windowTitleContains"));
        assertEquals("Button", request.getJSONArray("actions").getJSONObject(0).getString("controlType"));
        assertEquals("INVOKE", request.getJSONArray("actions").getJSONObject(0).getString("pattern"));
    }
}
