package com.auto.input;

public record MouseMoveResult(
        int requestedX,
        int requestedY,
        int actualX,
        int actualY,
        boolean setCursorPosOk,
        boolean usedRobotFallback,
        double errorPx
) {
    public boolean succeeded(int tolerancePx) {
        return errorPx <= tolerancePx;
    }

    public String diagnostic() {
        return "请求(" + requestedX + "," + requestedY + ")"
                + " 实际(" + actualX + "," + actualY + ")"
                + " 偏差 " + Math.round(errorPx) + "px"
                + " SetCursorPos=" + (setCursorPosOk ? "成功" : "失败")
                + (usedRobotFallback ? " Robot已回退" : "");
    }
}
