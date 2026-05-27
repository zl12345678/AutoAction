package com.auto.config;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class AppConfigLoader {
    public static final String DEFAULT_RESOURCE = "autoActionConfig.json";

    public AppConfig loadDefault() {
        Path localConfig = Paths.get(DEFAULT_RESOURCE);
        if (Files.exists(localConfig)) {
            return loadFromPath(localConfig);
        }
        return loadFromResource(DEFAULT_RESOURCE);
    }

    public AppConfig loadFromPath(Path path) {
        try {
            return loadFromString(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ConfigException("Failed to read config file: " + path, e);
        }
    }

    public AppConfig loadFromResource(String resourcePath) {
        try (InputStream inputStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new ConfigException("Config resource not found: " + resourcePath);
            }
            return loadFromString(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ConfigException("Failed to read config resource: " + resourcePath, e);
        }
    }

    public AppConfig loadFromString(String json) {
        try {
            JSONObject root = new JSONObject(json);
            SystemSettings system = parseSystem(requiredObject(root, "system"));
            VisionConfig vision = parseVision(requiredObject(root, "vision"));
            UiAutomationConfig uiAutomation = root.has("uiAutomation")
                    ? parseUiAutomation(root.getJSONObject("uiAutomation"))
                    : UiAutomationConfig.disabled();
            InputSettings input = root.has("input")
                    ? parseInput(root.getJSONObject("input"))
                    : InputSettings.defaults();
            return new AppConfig(system, vision, uiAutomation, input);
        } catch (JSONException | IllegalArgumentException e) {
            if (e instanceof ConfigException configException) {
                throw configException;
            }
            throw new ConfigException("Invalid config: " + e.getMessage(), e);
        }
    }

    private static SystemSettings parseSystem(JSONObject system) {
        return new SystemSettings(system.optBoolean("dryRun", true));
    }

    private static InputSettings parseInput(JSONObject input) {
        String backend = input.optString("clickBackend", ClickBackend.WIN32.configValue());
        String interceptionHome = input.optString("interceptionHome", "");
        return new InputSettings(ClickBackend.fromConfig(backend), interceptionHome);
    }

    private static VisionConfig parseVision(JSONObject vision) {
        RegionConfig region = parseRegion(requiredObject(vision, "miniMapRegion"));
        PointConfig target = parsePoint(requiredObject(vision, "target"));
        return new VisionConfig(
                requiredString(vision, "windowTitle"),
                requiredString(vision, "mapImage"),
                requiredString(vision, "arrowTemplate"),
                region,
                target,
                vision.optInt("matchAreaSize", 100),
                vision.optDouble("obstacleThreshold", 200.0),
                vision.optInt("moveStep", 80),
                vision.optDouble("arriveDistance", 10.0),
                vision.has("ocr") ? parseOcr(vision.getJSONObject("ocr")) : OcrConfig.disabled(),
                vision.has("yolo") ? parseYolo(vision.getJSONObject("yolo")) : YoloConfig.disabled(),
                vision.has("mapPreprocess") ? parseMapPreprocess(vision.getJSONObject("mapPreprocess")) : MapPreprocessConfig.defaults(),
                vision.has("mapClosure") ? parseMapClosure(vision.getJSONObject("mapClosure")) : MapClosureConfig.defaults(),
                vision.has("navigation") ? parseNavigation(vision.getJSONObject("navigation")) : NavigationConfig.defaults()
        );
    }

    private static NavigationConfig parseNavigation(JSONObject navigation) {
        NavigationConfig defaults = NavigationConfig.defaults();
        ScreenCalibrationConfig calibration = navigation.has("screenCalibration")
                ? parseScreenCalibration(navigation.getJSONObject("screenCalibration"))
                : defaults.screenCalibration();
        return new NavigationConfig(
                navigation.optInt("tickIntervalMs", defaults.tickIntervalMs()),
                navigation.optInt("stuckTimeoutMs", defaults.stuckTimeoutMs()),
                navigation.optDouble("stuckDistanceThreshold", defaults.stuckDistanceThreshold()),
                navigation.optDouble("waypointReachDistance", defaults.waypointReachDistance()),
                navigation.optInt("maxStuckRetries", defaults.maxStuckRetries()),
                navigation.optDouble("minLocalizationConfidence", defaults.minLocalizationConfidence()),
                navigation.optDouble("localizationSmoothingAlpha", defaults.localizationSmoothingAlpha()),
                navigation.optInt("localizationMaxPredictFrames", defaults.localizationMaxPredictFrames()),
                navigation.optBoolean("localizationOutlierRejectionEnabled", defaults.localizationOutlierRejectionEnabled()),
                navigation.optDouble("maxLocalizationJumpPx", defaults.maxLocalizationJumpPx()),
                calibration
        );
    }

    private static ScreenCalibrationConfig parseScreenCalibration(JSONObject calibration) {
        List<ScreenCalibrationPointConfig> points = new ArrayList<>();
        JSONArray pointsArray = calibration.optJSONArray("points");
        if (pointsArray != null) {
            for (int i = 0; i < pointsArray.length(); i++) {
                JSONObject point = pointsArray.getJSONObject(i);
                points.add(new ScreenCalibrationPointConfig(
                        point.getDouble("mapX"),
                        point.getDouble("mapY"),
                        point.getDouble("screenX"),
                        point.getDouble("screenY")
                ));
            }
        }
        return new ScreenCalibrationConfig(calibration.optBoolean("enabled", false), points);
    }

    private static MapClosureConfig parseMapClosure(JSONObject mapClosure) {
        MapClosureConfig defaults = MapClosureConfig.defaults();
        return new MapClosureConfig(
                mapClosure.optDouble("walkableThreshold", defaults.walkableThreshold()),
                mapClosure.optBoolean("sealExterior", defaults.sealExterior()),
                mapClosure.optInt("sealBorderWidth", defaults.sealBorderWidth()),
                mapClosure.optInt("morphCloseKernelSize", defaults.morphCloseKernelSize())
        );
    }

    private static MapPreprocessConfig parseMapPreprocess(JSONObject mapPreprocess) {
        MapPreprocessConfig defaults = MapPreprocessConfig.defaults();
        RegionConfig region = mapPreprocess.has("mapRegion")
                ? parseRegion(mapPreprocess.getJSONObject("mapRegion"))
                : defaults.mapRegion();
        return new MapPreprocessConfig(
                region,
                mapPreprocess.optBoolean("binaryEnabled", defaults.binaryEnabled()),
                mapPreprocess.optInt("threshold", defaults.threshold()),
                mapPreprocess.optBoolean("useOtsu", defaults.useOtsu()),
                mapPreprocess.optBoolean("removeOrangeMarkers", defaults.removeOrangeMarkers()),
                mapPreprocess.optInt("orangeHueMin", defaults.orangeHueMin()),
                mapPreprocess.optInt("orangeHueMax", defaults.orangeHueMax()),
                mapPreprocess.optInt("orangeSatMin", defaults.orangeSatMin()),
                mapPreprocess.optInt("orangeValMin", defaults.orangeValMin()),
                mapPreprocess.optDouble("minTemplateScore", defaults.minTemplateScore()),
                mapPreprocess.optDouble("minTemplateScoreGap", defaults.minTemplateScoreGap()),
                mapPreprocess.optBoolean("alignmentUseEdges", defaults.alignmentUseEdges())
        );
    }

    private static OcrConfig parseOcr(JSONObject ocr) {
        JSONArray regionsArray = ocr.optJSONArray("regions");
        List<OcrRegionConfig> regions = new ArrayList<>();
        if (regionsArray != null) {
            for (int i = 0; i < regionsArray.length(); i++) {
                JSONObject item = regionsArray.getJSONObject(i);
                regions.add(new OcrRegionConfig(
                        requiredString(item, "name"),
                        OcrRegionSource.valueOf(item.optString("source", OcrRegionSource.WINDOW.name()).toUpperCase()),
                        parseRegion(requiredObject(item, "region")),
                        item.optDouble("scale", 2.0),
                        item.has("threshold") ? item.getInt("threshold") : null,
                        item.optString("whitelist", "")
                ));
            }
        }
        return new OcrConfig(
                ocr.optBoolean("enabled", false),
                ocr.optString("language", "eng"),
                ocr.optInt("pageSegMode", 7),
                ocr.optString("defaultWhitelist", ""),
                regions
        );
    }

    private static YoloConfig parseYolo(JSONObject yolo) {
        List<String> classesOfInterest = new ArrayList<>();
        JSONArray classesArray = yolo.optJSONArray("classesOfInterest");
        if (classesArray != null) {
            for (int i = 0; i < classesArray.length(); i++) {
                classesOfInterest.add(classesArray.getString(i));
            }
        }
        return new YoloConfig(
                yolo.optBoolean("enabled", false),
                yolo.optString("modelPath", ""),
                yolo.optString("labelsPath", ""),
                yolo.optInt("inputWidth", 640),
                yolo.optInt("inputHeight", 640),
                yolo.optDouble("confidenceThreshold", 0.25),
                yolo.optDouble("iouThreshold", 0.45),
                yolo.optInt("maxDetections", 50),
                yolo.has("region") ? parseRegion(yolo.getJSONObject("region")) : null,
                classesOfInterest
        );
    }

    private static UiAutomationConfig parseUiAutomation(JSONObject config) {
        List<String> helperCommand = new ArrayList<>();
        JSONArray helperCommandArray = requiredArray(config, "helperCommand");
        for (int i = 0; i < helperCommandArray.length(); i++) {
            helperCommand.add(helperCommandArray.getString(i));
        }

        List<UiAutomationRuleConfig> rules = new ArrayList<>();
        JSONArray rulesArray = config.optJSONArray("rules");
        if (rulesArray != null) {
            for (int i = 0; i < rulesArray.length(); i++) {
                JSONObject rule = rulesArray.getJSONObject(i);
                JSONArray actionsArray = requiredArray(rule, "actions");
                List<UiAutomationActionConfig> actions = new ArrayList<>();
                for (int j = 0; j < actionsArray.length(); j++) {
                    JSONObject action = actionsArray.getJSONObject(j);
                    actions.add(new UiAutomationActionConfig(
                            action.optString("controlType", ""),
                            action.optString("name", ""),
                            action.optString("automationId", ""),
                            UiAutomationPattern.valueOf(requiredString(action, "pattern").toUpperCase()),
                            action.optString("value", "")
                    ));
                }
                rules.add(new UiAutomationRuleConfig(
                        requiredString(rule, "name"),
                        requiredString(rule, "windowTitleContains"),
                        actions
                ));
            }
        }

        return new UiAutomationConfig(
                config.optBoolean("enabled", false),
                helperCommand,
                config.optInt("timeoutMs", 10_000),
                rules
        );
    }

    private static RegionConfig parseRegion(JSONObject object) {
        return new RegionConfig(
                object.getInt("x"),
                object.getInt("y"),
                object.getInt("width"),
                object.getInt("height")
        );
    }

    private static PointConfig parsePoint(JSONObject object) {
        return new PointConfig(object.getInt("x"), object.getInt("y"));
    }

    private static JSONObject requiredObject(JSONObject object, String key) {
        if (!object.has(key)) {
            throw new ConfigException("Missing object: " + key);
        }
        return object.getJSONObject(key);
    }

    private static JSONArray requiredArray(JSONObject object, String key) {
        if (!object.has(key)) {
            throw new ConfigException("Missing array: " + key);
        }
        return object.getJSONArray(key);
    }

    private static String requiredString(JSONObject object, String key) {
        if (!object.has(key)) {
            throw new ConfigException("Missing string: " + key);
        }
        String value = object.getString(key);
        if (value == null || value.isBlank()) {
            throw new ConfigException("Blank string: " + key);
        }
        return value;
    }
}
