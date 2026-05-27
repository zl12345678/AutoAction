package com.auto.vision;

import org.opencv.core.Point;

public record NavigationDecision(
        NavigationAction action,
        NavigationControllerState state,
        String message,
        int waypointIndex,
        Point clickScreenPoint,
        double clickAngleOffsetRadians
) {
    public NavigationDecision {
        message = message == null ? "" : message;
    }

    public static NavigationDecision skip(NavigationControllerState state, String message, int waypointIndex) {
        return new NavigationDecision(NavigationAction.SKIP_CLICK, state, message, waypointIndex, null, 0.0);
    }

    public static NavigationDecision click(
            NavigationControllerState state,
            String message,
            int waypointIndex,
            Point screenPoint
    ) {
        return new NavigationDecision(NavigationAction.CLICK, state, message, waypointIndex, screenPoint, 0.0);
    }

    public static NavigationDecision replanAndClick(
            NavigationControllerState state,
            String message,
            int waypointIndex,
            Point screenPoint,
            double angleOffsetRadians
    ) {
        return new NavigationDecision(
                NavigationAction.REPLAN_AND_CLICK,
                state,
                message,
                waypointIndex,
                screenPoint,
                angleOffsetRadians
        );
    }

    public static NavigationDecision stopArrived(String message, int waypointIndex) {
        return new NavigationDecision(
                NavigationAction.STOP_ARRIVED,
                NavigationControllerState.ARRIVED,
                message,
                waypointIndex,
                null,
                0.0
        );
    }

    public static NavigationDecision stopFailed(String message, int waypointIndex) {
        return new NavigationDecision(
                NavigationAction.STOP_FAILED,
                NavigationControllerState.FAILED,
                message,
                waypointIndex,
                null,
                0.0
        );
    }
}
