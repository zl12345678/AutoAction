package com.auto.vision;

import com.auto.config.NavigationConfig;
import org.junit.Test;
import org.opencv.core.Point;

import static org.junit.Assert.assertEquals;

public class NavigationControllerTest {
  private final NavigationController controller = new NavigationController(NavigationConfig.defaults());

    @Test
    public void firstSuccessfulAnalysisRequestsClick() {
        NavigationAnalysis analysis = movingAnalysis(new Point(10, 10), new Point(30, 10), new Point(400, 300));

        NavigationDecision decision = controller.decide(analysis, 1_000L);

        assertEquals(NavigationAction.CLICK, decision.action());
        assertEquals(NavigationControllerState.MOVING, decision.state());
    }

    @Test
    public void movingTowardWaypointSkipsRepeatedClicks() {
        controller.decide(movingAnalysis(new Point(10, 10), new Point(80, 10), new Point(400, 300)), 1_000L);

        NavigationDecision decision = controller.decide(
                movingAnalysis(new Point(20, 10), new Point(80, 10), new Point(420, 300)),
                1_200L
        );

        assertEquals(NavigationAction.SKIP_CLICK, decision.action());
    }

    @Test
    public void stuckMovementTriggersReplanClick() {
        NavigationAnalysis first = movingAnalysis(new Point(10, 10), new Point(80, 10), new Point(400, 300));
        controller.decide(first, 1_000L);
        controller.decide(movingAnalysis(new Point(10, 10), new Point(80, 10), new Point(400, 300)), 1_200L);

        NavigationDecision decision = controller.decide(
                movingAnalysis(new Point(10, 10), new Point(80, 10), new Point(400, 300)),
                5_000L
        );

        assertEquals(NavigationAction.REPLAN_AND_CLICK, decision.action());
        assertEquals(NavigationControllerState.STUCK, decision.state());
    }

    @Test
    public void arrivedStopsNavigation() {
        NavigationAnalysis analysis = new NavigationAnalysis(
                "test",
                null,
                null,
                null,
                null,
                null,
                new Point(1, 1),
                new Point(100, 100),
                new Point(100, 100),
                new Point(100, 100),
                null,
                new int[][]{{1, 1}, {100, 100}},
                true,
                true,
                "arrived",
                0.95,
                LocalizationMethod.MAP_MATCH
        );

        NavigationDecision decision = controller.decide(analysis, 1_000L);

        assertEquals(NavigationAction.STOP_ARRIVED, decision.action());
    }

    private static NavigationAnalysis movingAnalysis(Point current, Point nextMap, Point nextScreen) {
        return new NavigationAnalysis(
                "test",
                null,
                null,
                null,
                null,
                null,
                new Point(1, 1),
                current,
                new Point(200, 200),
                nextMap,
                nextScreen,
                new int[][]{{10, 10}, {80, 10}, {200, 200}},
                false,
                true,
                "moving",
                0.9,
                LocalizationMethod.MAP_MATCH
        );
    }
}
