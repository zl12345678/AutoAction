package com.auto.input;

import com.auto.input.interception.InterceptionBootstrap;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class InterceptionBootstrapTest {
    @Test
    public void resolvesDllFromConfiguredHome() {
        InterceptionBootstrap.configureHome("D:\\Desktop\\Interception");
        Path dll = InterceptionBootstrap.resolveDll();
        assertTrue(Files.isRegularFile(dll));
        assertTrue(dll.toString().replace('\\', '/').endsWith("library/x64/interception.dll"));
    }
}
