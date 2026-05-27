package com.auto.input;

import com.auto.domain.MouseButton;

import java.awt.AWTException;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;

public final class RobotInputController implements InputController {
    private final Robot robot;

    public RobotInputController() {
        try {
            this.robot = new Robot();
        } catch (AWTException e) {
            throw new IllegalStateException("Failed to initialize Robot", e);
        }
    }

    @Override
    public void mouseMove(int x, int y) {
        robot.mouseMove(x, y);
    }

    @Override
    public void mousePress(MouseButton button) {
        robot.mousePress(toMask(button));
    }

    @Override
    public void mouseRelease(MouseButton button) {
        robot.mouseRelease(toMask(button));
    }

    @Override
    public void keyPress(int keyCode) {
        robot.keyPress(keyCode);
    }

    @Override
    public void keyRelease(int keyCode) {
        robot.keyRelease(keyCode);
    }

    @Override
    public void delay(int milliseconds) {
        robot.delay(milliseconds);
    }

    @Override
    public Point currentMousePosition() {
        return MouseInfo.getPointerInfo().getLocation();
    }

    private static int toMask(MouseButton button) {
        return switch (button) {
            case LEFT -> InputEvent.BUTTON1_DOWN_MASK;
            case MIDDLE -> InputEvent.BUTTON2_DOWN_MASK;
            case RIGHT -> InputEvent.BUTTON3_DOWN_MASK;
            case NONE -> throw new IllegalArgumentException("Mouse button is required");
        };
    }
}
