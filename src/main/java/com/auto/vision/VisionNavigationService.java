package com.auto.vision;

import com.auto.config.ClickBackend;
import com.auto.config.VisionConfig;
import com.auto.input.InputController;
import com.auto.window.WindowRef;
import com.auto.window.WindowService;
import org.opencv.core.Point;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class VisionNavigationService implements AutoCloseable {
    private final WindowService windowService;
    private final InputController inputController;
    private final NavigationTickClient tickClient;
    private final ScheduledExecutorService executorService;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile ScheduledFuture<?> scheduledTask;
    private volatile NavigationController controller;
    private volatile Consumer<NavigationRuntimeStatus> statusListener;
    private volatile ClickBackend clickBackend = ClickBackend.WIN32;
    private volatile Supplier<VisionConfig> configSupplier;
    private volatile boolean dryRun;

    public VisionNavigationService(
            WindowService windowService,
            InputController inputController,
            NavigationPipeline pipeline
    ) {
        this(windowService, inputController, resolveTickClient(windowService, pipeline));
    }

    VisionNavigationService(
            WindowService windowService,
            InputController inputController,
            NavigationTickClient tickClient
    ) {
        this.windowService = windowService;
        this.inputController = inputController;
        this.tickClient = tickClient;
        this.executorService = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("vision-navigation"));
    }

    private static NavigationTickClient resolveTickClient(WindowService windowService, NavigationPipeline pipeline) {
        if (pipeline instanceof NavigationTickClient tickClient) {
            return tickClient;
        }
        if (pipeline instanceof OpenCvNavigationPipeline openCvPipeline) {
            return openCvPipeline;
        }
        return new OpenCvNavigationPipeline(windowService);
    }

    public void setStatusListener(Consumer<NavigationRuntimeStatus> statusListener) {
        this.statusListener = statusListener;
    }

    public void setClickBackend(ClickBackend clickBackend) {
        this.clickBackend = clickBackend == null ? ClickBackend.WIN32 : clickBackend;
    }

    public boolean isRunning() {
        return running.get();
    }

    public void clearAnalyzerMapCaches() {
        if (tickClient instanceof OpenCvNavigationPipeline openCvPipeline) {
            openCvPipeline.analyzer().clearMapCaches();
        }
    }

    public void toggle(Supplier<VisionConfig> configSupplier, boolean dryRun) {
        if (running.get()) {
            stop();
        } else {
            start(configSupplier, dryRun);
        }
    }

    public void start(VisionConfig config, boolean dryRun) {
        start(() -> config, dryRun);
    }

    public void start(Supplier<VisionConfig> configSupplier, boolean dryRun) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        this.configSupplier = configSupplier;
        this.dryRun = dryRun;
        VisionConfig initial = this.configSupplier.get();
        controller = new NavigationController(initial.navigation());
        if (tickClient instanceof OpenCvNavigationPipeline openCvPipeline) {
            openCvPipeline.analyzer().resetLocalizationState();
        }
        System.out.println(
                "Vision navigation started. dryRun=" + dryRun
                        + " window=" + initial.windowTitle()
                        + " target=(" + initial.target().x() + "," + initial.target().y() + ")"
        );
        cancelScheduledTask();
        scheduledTask = executorService.scheduleWithFixedDelay(
                this::runSafely,
                0,
                initial.navigation().tickIntervalMs(),
                TimeUnit.MILLISECONDS
        );
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            System.out.println("Vision navigation stopped");
        }
        cancelScheduledTask();
        controller = null;
    }

    public boolean runOnceNow(VisionConfig config, boolean dryRun) {
        return runOnce(() -> config, dryRun);
    }

    public boolean runOnceNow(VisionConfig config, boolean dryRun, ClickBackend clickBackend) {
        ClickBackend previous = this.clickBackend;
        this.clickBackend = clickBackend == null ? ClickBackend.WIN32 : clickBackend;
        try {
            return runOnce(() -> config, dryRun);
        } finally {
            this.clickBackend = previous;
        }
    }

    boolean runOnce(Supplier<VisionConfig> configSupplier, boolean dryRun) {
        VisionConfig config = configSupplier.get();
        NavigationController activeController = controller != null
                ? controller
                : new NavigationController(config.navigation());
        Optional<WindowRef> window = windowService.findWindow(config.windowTitle());
        if (window.isEmpty()) {
            System.out.println("Vision: window not found: " + config.windowTitle());
            return false;
        }

        Optional<NavigationAnalysis> analysisOptional = tickClient.analyzeNext(
                config,
                window.get(),
                activeController.waypointIndex()
        );
        NavigationAnalysis analysis = analysisOptional.orElse(null);
        NavigationDecision decision = activeController.decide(analysis, System.currentTimeMillis());
        publishStatus(activeController, analysis, decision);

        if (decision.action() == NavigationAction.STOP_ARRIVED) {
            System.out.println("Vision: target reached");
            return true;
        }
        if (decision.action() == NavigationAction.STOP_FAILED) {
            System.out.println("Vision: navigation failed - " + decision.message());
            return false;
        }
        if (decision.action() == NavigationAction.SKIP_CLICK) {
            if (dryRun) {
                System.out.println("Vision dry-run skip: " + decision.message());
            }
            return true;
        }

        Point screenPoint = resolveClickPoint(decision, analysis, window.get().bounds());
        if (screenPoint == null) {
            System.out.println("Vision: missing screen click point");
            return false;
        }

        if (dryRun) {
            System.out.println("Vision dry-run click: " + decision.message() + " -> " + screenPoint);
            return true;
        }

        com.auto.window.GameWindowClickResult clickResult = com.auto.window.GameWindowClickHelper.click(
                windowService,
                inputController,
                window.get(),
                new java.awt.Point((int) Math.round(screenPoint.x), (int) Math.round(screenPoint.y)),
                clickBackend
        );
        System.out.println("Vision navigation click: " + clickResult.summary());
        return clickResult.focusSucceeded();
    }

    private void runSafely() {
        if (!running.get() || controller == null) {
            return;
        }
        try {
            if (configSupplier == null) {
                stop();
                return;
            }
            VisionConfig config = configSupplier.get();
            boolean shouldStop = executeContinuousTick(config, dryRun, controller);
            if (shouldStop) {
                stop();
            }
        } catch (Exception exception) {
            System.err.println("Vision navigation tick failed: " + exception.getMessage());
            exception.printStackTrace(System.err);
            stop();
        }
    }

    private boolean executeContinuousTick(
            VisionConfig config,
            boolean dryRun,
            NavigationController activeController
    ) {
        Optional<WindowRef> window = windowService.findWindow(config.windowTitle());
        if (window.isEmpty()) {
            System.out.println("Vision: window not found: " + config.windowTitle());
            publishStatus(activeController, null, NavigationDecision.stopFailed("未找到游戏窗口", activeController.waypointIndex()));
            return true;
        }

        Optional<NavigationAnalysis> analysisOptional = tickClient.analyzeNext(
                config,
                window.get(),
                activeController.waypointIndex()
        );
        NavigationAnalysis analysis = analysisOptional.orElse(null);
        NavigationDecision decision = activeController.decide(analysis, System.currentTimeMillis());
        publishStatus(activeController, analysis, decision);

        if (decision.action() == NavigationAction.STOP_ARRIVED) {
            System.out.println("Vision: target reached — " + decision.message());
            return true;
        }
        if (decision.action() == NavigationAction.STOP_FAILED) {
            System.out.println("Vision: navigation failed — " + decision.message());
            return true;
        }
        if (decision.action() == NavigationAction.SKIP_CLICK) {
            if (dryRun) {
                System.out.println("Vision dry-run skip: " + decision.message());
            } else {
                System.out.println("Vision skip: " + decision.message());
            }
            return false;
        }

        Point screenPoint = resolveClickPoint(decision, analysis, window.get().bounds());
        if (screenPoint == null) {
            System.out.println("Vision: missing screen click point");
            return false;
        }

        if (dryRun) {
            System.out.println("Vision dry-run click: " + decision.message() + " -> " + screenPoint);
            return false;
        }

        com.auto.window.GameWindowClickResult clickResult = com.auto.window.GameWindowClickHelper.click(
                windowService,
                inputController,
                window.get(),
                new java.awt.Point((int) Math.round(screenPoint.x), (int) Math.round(screenPoint.y)),
                clickBackend
        );
        System.out.println("Vision navigation click: " + clickResult.summary());
        return false;
    }

    private Point resolveClickPoint(NavigationDecision decision, NavigationAnalysis analysis, java.awt.Rectangle bounds) {
        Point screenPoint = decision.clickScreenPoint();
        if (screenPoint == null && analysis != null) {
            screenPoint = analysis.nextScreenPoint();
        }
        if (screenPoint == null) {
            return null;
        }
        if (decision.clickAngleOffsetRadians() == 0.0) {
            return screenPoint;
        }
        Point windowCenter = new Point(bounds.getCenterX(), bounds.getCenterY());
        return ScreenMapper.applyAngleOffset(windowCenter, screenPoint, decision.clickAngleOffsetRadians());
    }

    private void publishStatus(
            NavigationController activeController,
            NavigationAnalysis analysis,
            NavigationDecision decision
    ) {
        Consumer<NavigationRuntimeStatus> listener = statusListener;
        if (listener == null) {
            return;
        }
        listener.accept(new NavigationRuntimeStatus(
                decision.state(),
                decision.action(),
                decision.message(),
                activeController.waypointIndex(),
                analysis == null ? 0.0 : analysis.localizationConfidence(),
                analysis == null ? LocalizationMethod.NONE : analysis.localizationMethod(),
                analysis == null ? null : analysis.mapPreviewImage(),
                analysis == null ? null : analysis.localizationRawPoint(),
                analysis == null ? null : analysis.currentMapPoint(),
                analysis != null && analysis.localizationOutlierRejected(),
                analysis == null ? "" : analysis.localizationDetail()
        ));
    }

    @Override
    public void close() {
        stop();
        executorService.shutdownNow();
    }

    private void cancelScheduledTask() {
        ScheduledFuture<?> task = scheduledTask;
        if (task != null) {
            task.cancel(true);
            scheduledTask = null;
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String name;

        private NamedThreadFactory(String name) {
            this.name = name;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        }
    }
}
