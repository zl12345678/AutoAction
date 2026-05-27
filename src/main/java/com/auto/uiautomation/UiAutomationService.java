package com.auto.uiautomation;

import com.auto.config.UiAutomationConfig;
import com.auto.config.UiAutomationRuleConfig;

import java.util.List;

public interface UiAutomationService {
    UiAutomationCommandResult inspectWindow(String windowTitleContains, UiAutomationConfig config);

    UiAutomationCommandResult executeRule(UiAutomationRuleConfig rule, UiAutomationConfig config);

    List<UiAutomationRuleExecution> executeRules(UiAutomationConfig config);
}
