package com.auto.vision;

import com.auto.config.NavigationConfig;
import com.auto.opencv.utils.ImageProcessor;
import org.opencv.core.Point;

public final class NavigationController {
    private final NavigationConfig config;
    private NavigationControllerState state = NavigationControllerState.IDLE;
    private Point lastMapPoint;
    private long lastProgressAtMs;
    private long lastClickAtMs;
    private int stuckCount;
    private int waypointIndex;
    private int consecutiveLowConfidenceFrames;
    private int consecutiveAnalysisFailures;
    private String lastMessage = "";

    public NavigationController(NavigationConfig config) {
        this.config = config;
    }

    public void reset() {
        state = NavigationControllerState.IDLE;
        lastMapPoint = null;
        lastProgressAtMs = 0L;
        lastClickAtMs = 0L;
        stuckCount = 0;
        waypointIndex = 0;
        consecutiveLowConfidenceFrames = 0;
        consecutiveAnalysisFailures = 0;
        lastMessage = "";
    }

    public NavigationControllerState state() {
        return state;
    }

    public int waypointIndex() {
        return waypointIndex;
    }

    public String lastMessage() {
        return lastMessage;
    }

    public NavigationDecision decide(NavigationAnalysis analysis, long nowMs) {
        if (analysis == null) {
            return record(stopFailed("缺少导航分析结果"));
        }

        if (analysis.arrived()) {
            state = NavigationControllerState.ARRIVED;
            return record(NavigationDecision.stopArrived(
                    analysis.message().isBlank() ? "已到达目标" : analysis.message(),
                    waypointIndex
            ));
        }

        if (!analysis.success() || analysis.currentMapPoint() == null) {
            consecutiveAnalysisFailures++;
            state = NavigationControllerState.MOVING;
            if (consecutiveAnalysisFailures >= 5) {
                return record(stopFailed(analysis.message().isBlank()
                        ? "连续定位失败"
                        : analysis.message()));
            }
            return record(NavigationDecision.skip(
                    state,
                    analysis.message().isBlank() ? "定位失败，等待下一帧" : analysis.message(),
                    waypointIndex
            ));
        }

        consecutiveAnalysisFailures = 0;

        if (analysis.localizationConfidence() < config.minLocalizationConfidence()) {
            consecutiveLowConfidenceFrames++;
            state = NavigationControllerState.MOVING;
            if (consecutiveLowConfidenceFrames > config.localizationMaxPredictFrames()) {
                return record(stopFailed("定位置信度过低: "
                        + String.format("%.2f", analysis.localizationConfidence())));
            }
            return record(NavigationDecision.skip(
                    state,
                    "低置信度，暂不发点击 (" + String.format("%.2f", analysis.localizationConfidence()) + ")",
                    waypointIndex
            ));
        }

        consecutiveLowConfidenceFrames = 0;
        updateProgress(analysis.currentMapPoint(), nowMs);
        advanceWaypointIfReached(analysis);

        if (lastClickAtMs == 0L) {
            state = NavigationControllerState.MOVING;
            lastClickAtMs = nowMs;
            return record(NavigationDecision.click(
                    state,
                    "首次移动点击",
                    waypointIndex,
                    analysis.nextScreenPoint()
            ));
        }

        if (isStuck(nowMs)) {
            stuckCount++;
            state = NavigationControllerState.STUCK;
            if (stuckCount > config.maxStuckRetries()) {
                return record(stopFailed("连续卡住超过重试次数"));
            }
            skipToNextWaypoint(analysis.path());
            lastClickAtMs = nowMs;
            lastProgressAtMs = nowMs;
            double angleOffset = stuckCount % 2 == 0 ? Math.toRadians(15) : Math.toRadians(-15);
            return record(NavigationDecision.replanAndClick(
                    state,
                    "检测到卡住，跳过路点并重试 (第 " + stuckCount + " 次)",
                    waypointIndex,
                    analysis.nextScreenPoint(),
                    angleOffset
            ));
        }

        if (shouldIssueNextClick(analysis)) {
            state = NavigationControllerState.MOVING;
            stuckCount = 0;
            lastClickAtMs = nowMs;
            return record(NavigationDecision.click(
                    state,
                    "路点推进，发送下一次点击",
                    waypointIndex,
                    analysis.nextScreenPoint()
            ));
        }

        state = NavigationControllerState.MOVING;
        return record(NavigationDecision.skip(
                state,
                "仍在向当前路点移动",
                waypointIndex
        ));
    }

    private void updateProgress(Point currentMapPoint, long nowMs) {
        if (lastMapPoint == null) {
            lastMapPoint = currentMapPoint;
            lastProgressAtMs = nowMs;
            return;
        }
        if (ImageProcessor.getDistance(lastMapPoint, currentMapPoint) >= config.stuckDistanceThreshold()) {
            lastProgressAtMs = nowMs;
            stuckCount = 0;
        }
        lastMapPoint = currentMapPoint;
    }

    private void advanceWaypointIfReached(NavigationAnalysis analysis) {
        int[][] path = analysis.path();
        if (path.length == 0 || analysis.currentMapPoint() == null) {
            return;
        }
        while (waypointIndex < path.length) {
            Point waypoint = new Point(path[waypointIndex][0], path[waypointIndex][1]);
            if (ImageProcessor.getDistance(analysis.currentMapPoint(), waypoint) <= config.waypointReachDistance()) {
                waypointIndex++;
            } else {
                break;
            }
        }
    }

    private boolean isStuck(long nowMs) {
        return lastProgressAtMs > 0L && nowMs - lastProgressAtMs >= config.stuckTimeoutMs();
    }

    private boolean shouldIssueNextClick(NavigationAnalysis analysis) {
        if (analysis.nextMapPoint() == null || analysis.currentMapPoint() == null) {
            return true;
        }
        return ImageProcessor.getDistance(analysis.currentMapPoint(), analysis.nextMapPoint())
                <= config.waypointReachDistance();
    }

    private void skipToNextWaypoint(int[][] path) {
        if (path.length == 0) {
            return;
        }
        waypointIndex = Math.min(waypointIndex + 1, path.length - 1);
    }

    private NavigationDecision stopFailed(String message) {
        state = NavigationControllerState.FAILED;
        return NavigationDecision.stopFailed(message, waypointIndex);
    }

    private NavigationDecision record(NavigationDecision decision) {
        lastMessage = decision.message();
        state = decision.state();
        waypointIndex = decision.waypointIndex();
        return decision;
    }
}
