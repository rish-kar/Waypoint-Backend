package com.waypoint.backend.model.entitlement;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum FeatureCode {
    INSTANT_TAB_SEARCH("instant-tab-search"),
    DUPLICATE_TABS("duplicate-tabs"),
    SAVED_WORKSPACES("saved-workspaces"),
    TAB_TASKS("tab-tasks"),
    SNOOZE_TABS("snooze-tabs"),
    SMART_TAB_GROUPS("smart-tab-groups"),
    CALENDAR_SLACK_INTEGRATIONS("calendar-slack-integrations"),
    SESSION_TIMELINE("session-timeline"),
    AI_SUMMARY("ai-summary");

    private final String value;

    FeatureCode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Optional<FeatureCode> fromValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(feature -> feature.value.equals(normalized))
                .findFirst();
    }
}
