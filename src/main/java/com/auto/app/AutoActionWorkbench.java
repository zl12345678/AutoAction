package com.auto.app;

import com.auto.config.AppConfig;
import com.auto.config.AppConfigLoader;
import com.auto.config.ClickBackend;
import com.auto.input.interception.InterceptionBootstrap;
import com.auto.config.PointConfig;
import com.auto.config.RegionConfig;
import com.auto.config.VisionConfig;
import com.auto.input.InputController;
import com.auto.input.RobotInputController;
import com.auto.opencv.utils.ImageProcessor;
import com.auto.vision.NavigationAnalysis;
import com.auto.vision.NavigationPreflightResult;
import com.auto.vision.NavigationPreflightService;
import com.auto.vision.PreflightItem;
import com.auto.vision.OpenCvLoader;
import com.auto.vision.OpenCvNavigationAnalyzer;
import com.auto.vision.OpenCvNavigationPipeline;
import com.auto.vision.VisionNavigationService;
import com.auto.window.AwtJnaWindowService;
import com.auto.window.WindowRef;
import com.auto.window.WindowService;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public final class AutoActionWorkbench extends JFrame {
    private static final String MODE_WINDOW = "窗口截图/示例图";
    private static final String MODE_MINIMAP = "小地图图片";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final AppConfigLoader configLoader = new AppConfigLoader();
    private final WindowService windowService = new AwtJnaWindowService();
    private final InputController inputController = new RobotInputController();
    private final OpenCvNavigationAnalyzer analyzer = new OpenCvNavigationAnalyzer();
    private final VisionNavigationService visionNavigationService = new VisionNavigationService(
            windowService,
            inputController,
            new OpenCvNavigationPipeline(windowService)
    );
    private final NavigationPreflightService navigationPreflightService = new NavigationPreflightService(windowService);

    private final JTextField windowTitleField = new JTextField();
    private final JComboBox<String> sourceModeCombo = new JComboBox<>(new String[]{MODE_WINDOW, MODE_MINIMAP});
    private final JCheckBox dryRunBox = new JCheckBox("Dry Run");
    private final JCheckBox binaryBox = new JCheckBox("处理时启用二值化", true);
    private final JSlider thresholdSlider = new JSlider(0, 255, 127);
    private final JLabel thresholdValueLabel = new JLabel("127");
    private final JSpinner miniMapXSpinner = spinner(0, -10_000, 10_000, 1);
    private final JSpinner miniMapYSpinner = spinner(100, -10_000, 10_000, 1);
    private final JSpinner miniMapWidthSpinner = spinner(200, 1, 10_000, 1);
    private final JSpinner miniMapHeightSpinner = spinner(200, 1, 10_000, 1);
    private final JSpinner targetXSpinner = spinner(779, -100_000, 100_000, 1);
    private final JSpinner targetYSpinner = spinner(285, -100_000, 100_000, 1);
    private final JSpinner matchAreaSpinner = spinner(100, 1, 5_000, 1);
    private final JSpinner moveStepSpinner = spinner(80, 1, 10_000, 1);
    private final JSpinner obstacleThresholdSpinner = spinner(200.0, 0.0, 255.0, 1.0);
    private final JSpinner arriveDistanceSpinner = spinner(10.0, 1.0, 500.0, 1.0);

    private final ImagePreviewPanel sourcePanel = new ImagePreviewPanel();
    private final ImagePreviewPanel processedPanel = new ImagePreviewPanel();
    private final ImagePreviewPanel miniMapAnalysisPanel = new ImagePreviewPanel();
    private final ImagePreviewPanel mapPanel = new ImagePreviewPanel();
    private final ImagePreviewPanel clickPreviewPanel = new ImagePreviewPanel();
    private final JTextArea logArea = new JTextArea();
    private final JLabel statusLabel = new JLabel("就绪");
    private final JTabbedPane previewTabs = new JTabbedPane();

    private AppConfig loadedConfig;
    private BufferedImage currentSourceImage;
    private Rectangle currentSourceBounds;
    private String currentSourceName = "未加载图像";
    private NavigationAnalysis lastAnalysis;

    public AutoActionWorkbench() {
        super("AutoAction Studio");
        OpenCvLoader.load();
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1480, 900));
        setLayout(new BorderLayout());
        add(buildMainSplitPane(), BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
        loadConfig();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                visionNavigationService.close();
            }
        });
    }

    private JSplitPane buildMainSplitPane() {
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildControlScrollPane(), buildPreviewTabs());
        splitPane.setResizeWeight(0.26);
        splitPane.setDividerLocation(380);
        return splitPane;
    }

    private JScrollPane buildControlScrollPane() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        content.add(buildProjectSection());
        content.add(Box.createVerticalStrut(10));
        content.add(buildVisionConfigSection());
        content.add(Box.createVerticalStrut(10));
        content.add(buildSourceSection());
        content.add(Box.createVerticalStrut(10));
        content.add(buildProcessingSection());
        content.add(Box.createVerticalStrut(10));
        content.add(buildActionSection());
        content.add(Box.createVerticalGlue());

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }

    private JTabbedPane buildPreviewTabs() {
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        previewTabs.addTab("源图", sourcePanel);
        previewTabs.addTab("处理结果", processedPanel);
        previewTabs.addTab("小地图分析", miniMapAnalysisPanel);
        previewTabs.addTab("地图路径", mapPanel);
        previewTabs.addTab("点击预览", clickPreviewPanel);
        previewTabs.addTab("日志", new JScrollPane(logArea));
        return previewTabs;
    }

    private JPanel buildProjectSection() {
        JPanel panel = sectionPanel("项目总览");
        panel.setLayout(new BorderLayout(0, 8));

        JTextArea overview = new JTextArea("""
                AutoAction Studio 负责把游戏窗口截图、小地图处理、路径匹配、点击预览和真实执行串成一条完整链路。
                你可以直接加载仓库示例，验证识别和寻路；也可以绑定真实游戏窗口，先预览点击点，再执行单步或连续导航。
                """);
        overview.setEditable(false);
        overview.setOpaque(false);
        overview.setLineWrap(true);
        overview.setWrapStyleWord(true);
        overview.setBorder(BorderFactory.createEmptyBorder());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton reloadButton = new JButton("重新加载配置");
        reloadButton.addActionListener(e -> loadConfig());
        JButton runExampleButton = new JButton("运行示例");
        runExampleButton.addActionListener(e -> runSampleAnalysis());
        buttons.add(reloadButton);
        buttons.add(runExampleButton);

        panel.add(overview, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildVisionConfigSection() {
        JPanel panel = sectionPanel("视觉配置");
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = defaultGbc();

        addRow(panel, gbc, 0, "窗口标题", windowTitleField);
        addRow(panel, gbc, 1, "图像类型", sourceModeCombo);
        addRow(panel, gbc, 2, "小地图 X", miniMapXSpinner);
        addRow(panel, gbc, 3, "小地图 Y", miniMapYSpinner);
        addRow(panel, gbc, 4, "小地图宽", miniMapWidthSpinner);
        addRow(panel, gbc, 5, "小地图高", miniMapHeightSpinner);
        addRow(panel, gbc, 6, "目标 X", targetXSpinner);
        addRow(panel, gbc, 7, "目标 Y", targetYSpinner);
        addRow(panel, gbc, 8, "匹配框", matchAreaSpinner);
        addRow(panel, gbc, 9, "移动步长", moveStepSpinner);
        addRow(panel, gbc, 10, "障碍阈值", obstacleThresholdSpinner);
        addRow(panel, gbc, 11, "到达距离", arriveDistanceSpinner);

        gbc.gridx = 0;
        gbc.gridy = 12;
        gbc.gridwidth = 2;
        panel.add(dryRunBox, gbc);
        return panel;
    }

    private JPanel buildSourceSection() {
        JPanel panel = sectionPanel("截图与素材");
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = defaultGbc();

        JButton captureWindowButton = new JButton("捕获游戏窗口");
        captureWindowButton.addActionListener(e -> captureGameWindow());
        JButton loadLocalButton = new JButton("加载本地图像");
        loadLocalButton.addActionListener(e -> loadLocalImage());
        JButton loadSampleButton = new JButton("加载示例截图");
        loadSampleButton.addActionListener(e -> loadSampleImageOnly());

        addFullWidth(panel, gbc, 0, captureWindowButton);
        addFullWidth(panel, gbc, 1, loadLocalButton);
        addFullWidth(panel, gbc, 2, loadSampleButton);
        return panel;
    }

    private JPanel buildProcessingSection() {
        JPanel panel = sectionPanel("图片处理");
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = defaultGbc();

        addFullWidth(panel, gbc, 0, binaryBox);

        JPanel sliderPanel = new JPanel(new BorderLayout(8, 0));
        sliderPanel.add(thresholdSlider, BorderLayout.CENTER);
        sliderPanel.add(thresholdValueLabel, BorderLayout.EAST);
        ChangeListener updateThreshold = e -> thresholdValueLabel.setText(String.valueOf(thresholdSlider.getValue()));
        thresholdSlider.addChangeListener(updateThreshold);
        updateThreshold.stateChanged(null);

        addRow(panel, gbc, 1, "二值阈值", sliderPanel);

        JButton processButton = new JButton("处理当前图像");
        processButton.addActionListener(e -> processCurrentImage());
        addFullWidth(panel, gbc, 2, processButton);
        return panel;
    }

    private JPanel buildActionSection() {
        JPanel panel = sectionPanel("分析与执行");
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = defaultGbc();

        JButton preflightButton = new JButton("一键预检");
        preflightButton.addActionListener(e -> runNavigationPreflight());
        JButton analyzeButton = new JButton("分析当前图像");
        analyzeButton.addActionListener(e -> analyzeCurrentImage(false));
        JButton previewClickButton = new JButton("点击预览");
        previewClickButton.addActionListener(e -> analyzeCurrentImage(true));
        JButton executeStepButton = new JButton("执行单步");
        executeStepButton.addActionListener(e -> executeSingleStep());
        JButton startLoopButton = new JButton("开始连续导航");
        startLoopButton.addActionListener(e -> startContinuousNavigation());
        JButton stopButton = new JButton("停止导航");
        stopButton.addActionListener(e -> stopNavigation());

        addFullWidth(panel, gbc, 0, preflightButton);
        addFullWidth(panel, gbc, 1, analyzeButton);
        addFullWidth(panel, gbc, 2, previewClickButton);
        addFullWidth(panel, gbc, 3, executeStepButton);
        addFullWidth(panel, gbc, 4, startLoopButton);
        addFullWidth(panel, gbc, 5, stopButton);
        return panel;
    }

    private void loadConfig() {
        try {
            loadedConfig = configLoader.loadDefault();
            applyVisionConfig(loadedConfig.vision());
            dryRunBox.setSelected(loadedConfig.system().dryRun());
            applyInputSettings(loadedConfig);
            appendLog(
                    "配置加载成功: " + AppConfigLoader.DEFAULT_RESOURCE
                            + "，点击后端=" + loadedConfig.input().clickBackend().configValue()
            );
            setStatus("配置加载完成");
        } catch (RuntimeException e) {
            appendLog("配置加载失败: " + e.getMessage());
            setStatus("配置加载失败");
            showError("配置加载失败", e.getMessage());
        }
    }

    private void applyInputSettings(AppConfig config) {
        InterceptionBootstrap.configureHome(config.input().interceptionHome());
        visionNavigationService.setClickBackend(config.input().clickBackend());
    }

    private void applyVisionConfig(VisionConfig vision) {
        windowTitleField.setText(vision.windowTitle());
        miniMapXSpinner.setValue(vision.miniMapRegion().x());
        miniMapYSpinner.setValue(vision.miniMapRegion().y());
        miniMapWidthSpinner.setValue(vision.miniMapRegion().width());
        miniMapHeightSpinner.setValue(vision.miniMapRegion().height());
        targetXSpinner.setValue(vision.target().x());
        targetYSpinner.setValue(vision.target().y());
        matchAreaSpinner.setValue(vision.matchAreaSize());
        moveStepSpinner.setValue(vision.moveStep());
        obstacleThresholdSpinner.setValue(vision.obstacleThreshold());
        arriveDistanceSpinner.setValue(vision.arriveDistance());
    }

    private void loadSampleImageOnly() {
        try {
            BufferedImage sample = ImageProcessor.loadResourceBufferedImage("img/gamePic.bmp");
            setSourceImage(sample, new Rectangle(0, 0, sample.getWidth(), sample.getHeight()), "resource:img/gamePic.bmp");
            sourceModeCombo.setSelectedItem(MODE_WINDOW);
            appendLog("已加载示例截图");
            setStatus("示例截图已加载");
        } catch (RuntimeException e) {
            appendLog("示例截图加载失败: " + e.getMessage());
            setStatus("示例截图加载失败");
        }
    }

    private void runSampleAnalysis() {
        try {
            VisionConfig config = buildVisionConfig();
            NavigationAnalysis analysis = analyzer.analyzeSample(config);
            if (!analysis.success()) {
                VisionConfig demoConfig = new VisionConfig(
                        config.windowTitle(),
                        config.mapImage(),
                        config.arrowTemplate(),
                        config.miniMapRegion(),
                        new PointConfig(500, 260),
                        config.matchAreaSize(),
                        config.obstacleThreshold(),
                        config.moveStep(),
                        config.arriveDistance()
                );
                analysis = analyzer.analyzeSample(demoConfig);
                appendLog("当前目标点在示例图上不可达，示例已切换到演示目标点 (500, 260)。");
            }
            sourceModeCombo.setSelectedItem(MODE_WINDOW);
            currentSourceBounds = new Rectangle(0, 0, analysis.sourceImage().getWidth(), analysis.sourceImage().getHeight());
            applyAnalysis(analysis);
            previewTabs.setSelectedComponent(clickPreviewPanel);
        } catch (RuntimeException e) {
            appendLog("示例分析失败: " + e.getMessage());
            setStatus("示例分析失败");
            showError("示例分析失败", e.getMessage());
        }
    }

    private void captureGameWindow() {
        VisionConfig config = buildVisionConfig();
        Optional<WindowRef> window = windowService.findWindow(config.windowTitle());
        if (window.isEmpty()) {
            String message = "未找到窗口: " + config.windowTitle();
            appendLog(message);
            setStatus(message);
            return;
        }

        WindowRef windowRef = window.get();
        BufferedImage capture = windowService.capture(windowRef.bounds());
        setSourceImage(capture, windowRef.bounds(), "window:" + windowRef.title());
        sourceModeCombo.setSelectedItem(MODE_WINDOW);
        appendLog("已捕获窗口: " + windowRef.title() + " " + rectText(windowRef.bounds()));
        setStatus("游戏窗口截图完成");
    }

    private void loadLocalImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "bmp"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File selectedFile = chooser.getSelectedFile();
        try {
            BufferedImage image = ImageIO.read(selectedFile);
            if (image == null) {
                throw new IOException("不支持的图片格式");
            }
            setSourceImage(image, new Rectangle(0, 0, image.getWidth(), image.getHeight()), selectedFile.getAbsolutePath());
            appendLog("已加载本地图像: " + selectedFile.getAbsolutePath());
            setStatus("本地图像已加载");
        } catch (IOException e) {
            appendLog("本地图像加载失败: " + e.getMessage());
            setStatus("本地图像加载失败");
            showError("图像加载失败", e.getMessage());
        }
    }

    private void processCurrentImage() {
        if (currentSourceImage == null) {
            showInfo("先准备图像", "请先加载示例、捕获窗口或选择本地图像。");
            return;
        }

        BufferedImage imageToProcess = resolveProcessingSource();
        Mat sourceMat = ImageProcessor.bufferedImageToMat(imageToProcess);
        Mat gray = new Mat();
        Imgproc.cvtColor(sourceMat, gray, Imgproc.COLOR_BGR2GRAY);
        Mat output = gray;
        String title = "灰度处理";
        if (binaryBox.isSelected()) {
            Mat binary = new Mat();
            Imgproc.threshold(gray, binary, thresholdSlider.getValue(), 255, Imgproc.THRESH_BINARY);
            output = binary;
            title = "二值化处理";
        }

        processedPanel.setImage(
                ImageProcessor.matToBufferedImage(output),
                title + " - " + currentSourceName + " / 阈值 " + thresholdSlider.getValue()
        );
        previewTabs.setSelectedComponent(processedPanel);
        appendLog("已处理图像: " + title);
        setStatus("图片处理完成");
    }

    private BufferedImage resolveProcessingSource() {
        if (MODE_MINIMAP.equals(sourceModeCombo.getSelectedItem())) {
            return currentSourceImage;
        }

        VisionConfig config = buildVisionConfig();
        Mat sourceMat = ImageProcessor.bufferedImageToMat(currentSourceImage);
        RegionConfig region = config.miniMapRegion();
        Mat miniMap = ImageProcessor.extractRegion(
                sourceMat,
                new org.opencv.core.Rect(region.x(), region.y(), region.width(), region.height())
        );
        return ImageProcessor.matToBufferedImage(miniMap);
    }

    private void analyzeCurrentImage(boolean focusClickPreview) {
        if (currentSourceImage == null) {
            showInfo("先准备图像", "请先加载示例、捕获窗口或选择本地图像。");
            return;
        }

        VisionConfig config = buildVisionConfig();
        NavigationAnalysis analysis;
        if (MODE_MINIMAP.equals(sourceModeCombo.getSelectedItem())) {
            analysis = analyzer.analyzeMiniMap(config, currentSourceImage, currentSourceName);
        } else {
            analysis = analyzer.analyzeWindowCapture(config, currentSourceImage, currentSourceBounds, currentSourceName);
        }

        applyAnalysis(analysis);
        if (focusClickPreview) {
            previewTabs.setSelectedComponent(clickPreviewPanel);
        } else {
            previewTabs.setSelectedComponent(mapPanel);
        }
    }

    private void applyAnalysis(NavigationAnalysis analysis) {
        lastAnalysis = analysis;
        if (analysis.sourceImage() != null) {
            currentSourceImage = analysis.sourceImage();
            currentSourceName = analysis.sourceName();
            if (currentSourceBounds == null && currentSourceImage != null) {
                currentSourceBounds = new Rectangle(0, 0, currentSourceImage.getWidth(), currentSourceImage.getHeight());
            }
            sourcePanel.setImage(analysis.sourceImage(), "源图 - " + analysis.sourceName());
        }

        miniMapAnalysisPanel.setImage(analysis.miniMapPreviewImage(), "小地图分析 - " + analysis.message());
        mapPanel.setImage(analysis.mapPreviewImage(), "地图路径 - " + analysis.message());
        clickPreviewPanel.setImage(analysis.clickPreviewImage(), "点击预览 - " + analysis.message());

        appendLog(analysis.debugSummary());
        setStatus(analysis.message());
    }

    private void runNavigationPreflight() {
        try {
            VisionConfig config = buildVisionConfig();
            ClickBackend clickBackend = loadedConfig == null
                    ? ClickBackend.WIN32
                    : loadedConfig.input().clickBackend();
            String interceptionHome = loadedConfig == null ? "" : loadedConfig.input().interceptionHome();
            NavigationPreflightResult result = navigationPreflightService.run(
                    config,
                    dryRunBox.isSelected(),
                    clickBackend,
                    interceptionHome
            );
            appendLog("=== 导航预检 ===");
            appendLog(result.summary());
            for (PreflightItem item : result.items()) {
                appendLog("[" + item.level().label() + "] " + item.title() + ": " + item.detail());
            }
            setStatus(result.ready() ? "预检通过，可开始连续导航" : "预检未通过，请查看日志");
            if (!result.ready()) {
                showError("导航预检未通过", result.summary());
            }
        } catch (RuntimeException e) {
            appendLog("导航预检失败: " + e.getMessage());
            setStatus("导航预检失败");
            showError("导航预检失败", e.getMessage());
        }
    }

    private void executeSingleStep() {
        try {
            VisionConfig config = buildVisionConfig();
            ClickBackend clickBackend = loadedConfig == null
                    ? com.auto.config.ClickBackend.WIN32
                    : loadedConfig.input().clickBackend();
            boolean result = visionNavigationService.runOnceNow(config, dryRunBox.isSelected(), clickBackend);
            appendLog("执行单步导航: result=" + result + ", dryRun=" + dryRunBox.isSelected());
            setStatus(result ? "单步导航执行完成" : "单步导航未生成有效点击");
        } catch (RuntimeException e) {
            appendLog("执行单步失败: " + e.getMessage());
            setStatus("执行单步失败");
            showError("执行单步失败", e.getMessage());
        }
    }

    private void startContinuousNavigation() {
        try {
            VisionConfig config = buildVisionConfig();
            ClickBackend clickBackend = loadedConfig == null
                    ? com.auto.config.ClickBackend.WIN32
                    : loadedConfig.input().clickBackend();
            visionNavigationService.setClickBackend(clickBackend);
            visionNavigationService.start(this::buildVisionConfig, dryRunBox.isSelected());
            appendLog("已启动连续导航，dryRun=" + dryRunBox.isSelected());
            setStatus("连续导航已启动");
        } catch (RuntimeException e) {
            appendLog("启动连续导航失败: " + e.getMessage());
            setStatus("连续导航启动失败");
            showError("启动连续导航失败", e.getMessage());
        }
    }

    private void stopNavigation() {
        visionNavigationService.stop();
        appendLog("已停止连续导航");
        setStatus("导航已停止");
    }

    private VisionConfig buildVisionConfig() {
        return new VisionConfig(
                windowTitleField.getText().trim(),
                loadedConfig != null ? loadedConfig.vision().mapImage() : "img/sggd/largeMap_2.bmp",
                loadedConfig != null ? loadedConfig.vision().arrowTemplate() : "img/arrow_template2.bmp",
                new RegionConfig(
                        ((Number) miniMapXSpinner.getValue()).intValue(),
                        ((Number) miniMapYSpinner.getValue()).intValue(),
                        ((Number) miniMapWidthSpinner.getValue()).intValue(),
                        ((Number) miniMapHeightSpinner.getValue()).intValue()
                ),
                new PointConfig(
                        ((Number) targetXSpinner.getValue()).intValue(),
                        ((Number) targetYSpinner.getValue()).intValue()
                ),
                ((Number) matchAreaSpinner.getValue()).intValue(),
                ((Number) obstacleThresholdSpinner.getValue()).doubleValue(),
                ((Number) moveStepSpinner.getValue()).intValue(),
                ((Number) arriveDistanceSpinner.getValue()).doubleValue()
        );
    }

    private void setSourceImage(BufferedImage image, Rectangle bounds, String name) {
        currentSourceImage = image;
        currentSourceBounds = bounds;
        currentSourceName = name;
        sourcePanel.setImage(image, "源图 - " + name);
    }

    private void appendLog(String message) {
        logArea.append("[" + LocalTime.now().format(TIME_FORMAT) + "] " + message + System.lineSeparator());
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    private void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    private void showInfo(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    private static JPanel sectionPanel(String title) {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return panel;
    }

    private static GridBagConstraints defaultGbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 4, 4, 4);
        return gbc;
    }

    private static void addRow(JPanel panel, GridBagConstraints gbc, int row, String labelText, java.awt.Component component) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.weightx = 0;
        panel.add(new JLabel(labelText), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(component, gbc);
    }

    private static void addFullWidth(JPanel panel, GridBagConstraints gbc, int row, java.awt.Component component) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        panel.add(component, gbc);
        gbc.gridwidth = 1;
    }

    private static JSpinner spinner(int value, int min, int max, int step) {
        return new JSpinner(new SpinnerNumberModel(value, min, max, step));
    }

    private static JSpinner spinner(double value, double min, double max, double step) {
        return new JSpinner(new SpinnerNumberModel(value, min, max, step));
    }

    private static String rectText(Rectangle rect) {
        return "(" + rect.x + ", " + rect.y + ", " + rect.width + ", " + rect.height + ")";
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            AutoActionWorkbench workbench = new AutoActionWorkbench();
            workbench.setLocationRelativeTo(null);
            workbench.setVisible(true);
        });
    }
}
