package com.auto.input;

import com.auto.domain.MouseButton;

import java.awt.Point;

public interface InputController {
    void mouseMove(int x, int y);

    void mousePress(MouseButton button);

    void mouseRelease(MouseButton button);

    default void mouseClick(MouseButton button) {
        mousePress(button);
        mouseRelease(button);
    }

    void keyPress(int keyCode);

    void keyRelease(int keyCode);

    void delay(int milliseconds);

    Point currentMousePosition();
}
