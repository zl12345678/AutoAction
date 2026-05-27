package com.auto.vision;

import java.util.List;

public record NavigationPreflightResult(
        List<PreflightItem> items,
        boolean ready
) {
    public String summary() {
        int ok = 0;
        int warn = 0;
        int fail = 0;
        for (PreflightItem item : items) {
            switch (item.level()) {
                case OK -> ok++;
                case WARN -> warn++;
                case FAIL -> fail++;
            }
        }
        if (fail > 0) {
            return "预检未通过：失败 " + fail + "，警告 " + warn + "，通过 " + ok;
        }
        if (warn > 0) {
            return "预检可启动（有警告 " + warn + "）：通过 " + ok;
        }
        return "预检全部通过（" + ok + " 项）";
    }
}
