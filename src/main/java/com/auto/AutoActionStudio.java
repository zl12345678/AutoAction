package com.auto;

import com.auto.ui.ComposeDesktopLauncher;
import com.auto.window.WindowsDpi;

public final class AutoActionStudio {
    private AutoActionStudio() {
    }

    public static void main(String[] args) {
        WindowsDpi.enable();
        ComposeDesktopLauncher.launch();
    }
}
