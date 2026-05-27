package com.auto.window;



import java.awt.Point;

import java.awt.Rectangle;



public record GameWindowClickResult(

        String windowTitle,

        Point requestedPoint,

        Point absoluteScreenPoint,

        Point preparedPoint,

        boolean foregroundBefore,

        boolean foregroundAfterFocus,

        boolean foregroundAfterClicks,

        int clickCount,

        boolean usedClientTopActivation,

        boolean usedPrimingClick,

        Point mouseBefore,

        Point mouseAfter,

        Rectangle windowBoundsOnScreen,

        ClickDeliveryMethod deliveryMethod

) {

    public boolean focusSucceeded() {

        return foregroundAfterClicks;

    }



    public boolean mouseReachedTarget(int tolerancePx) {

        double dx = mouseAfter.getX() - preparedPoint.getX();

        double dy = mouseAfter.getY() - preparedPoint.getY();

        return Math.hypot(dx, dy) <= tolerancePx;

    }



    public String summary() {

        StringBuilder builder = new StringBuilder();

        builder.append("窗口「").append(windowTitle).append("」");

        builder.append(foregroundAfterClicks ? "已聚焦" : "未聚焦");

        builder.append("；点击 ").append(clickCount).append(" 次");

        builder.append("；方式 ").append(deliveryMethod == null ? ClickDeliveryMethod.PHYSICAL.label() : deliveryMethod.label());

        builder.append("；输入 (").append(requestedPoint.x).append(", ").append(requestedPoint.y).append(")");

        builder.append(" → 绝对 (").append(absoluteScreenPoint.x).append(", ").append(absoluteScreenPoint.y).append(")");

        if (!absoluteScreenPoint.equals(preparedPoint)) {

            builder.append(" → 校正 (").append(preparedPoint.x).append(", ").append(preparedPoint.y).append(")");

        }

        if (windowBoundsOnScreen != null) {

            builder.append("；窗口 @ (")

                    .append(windowBoundsOnScreen.x).append(",").append(windowBoundsOnScreen.y)

                    .append(")");

        }

        if (usedClientTopActivation) {

            builder.append("；曾点击客户区顶部激活");

        }

        if (usedPrimingClick) {

            builder.append("；含激活用预点击");

        }

        if (deliveryMethod == ClickDeliveryMethod.INTERCEPTION) {

            builder.append("；Interception 驱动级（罗技等 USB 鼠标通用）");

        } else if (deliveryMethod == ClickDeliveryMethod.LAYERED) {

            builder.append("；分层注入（Unreal 单窗时仅 SendInput）");

        } else if (deliveryMethod == ClickDeliveryMethod.SEND_INPUT) {

            builder.append("；SendInput 绝对坐标（光标可能未移动）");

        } else {

            builder.append("；鼠标 (")

                    .append(Math.round(mouseBefore.getX())).append(",").append(Math.round(mouseBefore.getY()))

                    .append(") → (")

                    .append(Math.round(mouseAfter.getX())).append(",").append(Math.round(mouseAfter.getY()))

                    .append(")");

            double mouseErrorPx = Math.hypot(

                    mouseAfter.getX() - preparedPoint.getX(),

                    mouseAfter.getY() - preparedPoint.getY()

            );

            if (!mouseReachedTarget(16)) {

                builder.append("；鼠标偏差 ").append(Math.round(mouseErrorPx)).append("px");

            }

        }

        return builder.toString();

    }

}

