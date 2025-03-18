package com.auto.entity;

import java.util.List;

/**
 * Action类用于封装用户行为，包括鼠标和键盘事件。
 * 它提供了一种统一的方式来处理和访问这些事件的细节。
 */
public class Action {
    // 行为名称
    private String name;
    // 行为描述
    private String description;
    // 触发行为的热键
    private String hotkey;
    // 行为列表
    private List<Behavior> behaviors;

    // 构造函数
    public Action(String name, String description, String hotkey, List<Behavior> behaviors) {
        this.name = name;
        this.description = description;
        this.hotkey = hotkey;
        this.behaviors = behaviors;
    }

    // Getter 和 Setter 方法
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getHotkey() {
        return hotkey;
    }

    public void setHotkey(String hotkey) {
        this.hotkey = hotkey;
    }

    public List<Behavior> getBehaviors() {
        return behaviors;
    }

    public void setBehaviors(List<Behavior> behaviors) {
        this.behaviors = behaviors;
    }
}


