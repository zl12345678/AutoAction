package com.auto.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AppConfigLoaderTest {
    private final AppConfigLoader loader = new AppConfigLoader();

    @Test
    public void parsesNewSchema() {
        AppConfig config = loader.loadFromString(validConfig());

        assertTrue(config.system().dryRun());
        assertEquals("Torchlight: Infinite  ", config.vision().windowTitle());
        assertTrue(config.vision().ocr().enabled());
        assertEquals(2, config.vision().ocr().regions().size());
        assertTrue(config.vision().yolo().enabled());
        assertEquals(1, config.uiAutomation().rules().size());
        assertEquals(ClickBackend.WIN32, config.input().clickBackend());
    }

    @Test
    public void parsesInterceptionClickBackend() {
        String json = validConfig().replace(
                "\"uiAutomation\":",
                "\"input\":{\"clickBackend\":\"interception\"},\"uiAutomation\":"
        );
        AppConfig config = loader.loadFromString(json);
        assertEquals(ClickBackend.INTERCEPTION, config.input().clickBackend());
    }

    @Test
    public void rejectsMissingRequiredField() {
        String json = validConfig().replace("\"vision\"", "\"missingVision\"");

        assertThrows(ConfigException.class, () -> loader.loadFromString(json));
    }

    @Test
    public void rejectsUnknownUiAutomationPattern() {
        String json = validConfig().replace("\"pattern\":\"INVOKE\"", "\"pattern\":\"UNKNOWN\"");

        assertThrows(ConfigException.class, () -> loader.loadFromString(json));
    }

    static String validConfig() {
        return """
                {
                  "system":{"dryRun":true},
                  "vision":{
                     "windowTitle":"Torchlight: Infinite  ",
                     "mapImage":"img/sggd/largeMap_2.bmp",
                     "arrowTemplate":"img/arrow_template2.bmp",
                     "miniMapRegion":{"x":0,"y":100,"width":200,"height":200},
                     "target":{"x":779,"y":285},
                     "matchAreaSize":100,
                     "obstacleThreshold":200.0,
                     "moveStep":80,
                     "arriveDistance":10.0,
                     "ocr":{
                       "enabled":true,
                       "language":"eng",
                       "pageSegMode":7,
                       "defaultWhitelist":"0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ:-",
                       "regions":[
                         {
                           "name":"coords",
                           "source":"WINDOW",
                           "region":{"x":1200,"y":24,"width":160,"height":40},
                           "scale":2.0,
                           "threshold":170,
                           "whitelist":"0123456789, "
                         },
                         {
                           "name":"status",
                           "source":"WINDOW",
                           "region":{"x":820,"y":46,"width":180,"height":36},
                           "scale":2.0,
                           "threshold":165
                         }
                       ]
                     },
                     "yolo":{
                       "enabled":true,
                       "modelPath":"models/ui-detector.onnx",
                       "labelsPath":"models/ui-detector.labels.txt",
                       "inputWidth":640,
                       "inputHeight":640,
                       "confidenceThreshold":0.3,
                       "iouThreshold":0.45,
                       "maxDetections":25,
                       "region":{"x":560,"y":260,"width":780,"height":500},
                       "classesOfInterest":["npc","drop","button"]
                     }
                  },
                  "uiAutomation":{
                    "enabled":false,
                    "helperCommand":[
                      "powershell",
                      "-ExecutionPolicy",
                      "Bypass",
                      "-File",
                      "tools/windows-ui-automation-helper.ps1"
                    ],
                    "timeoutMs":8000,
                    "rules":[
                      {
                        "name":"dismiss-confirmation",
                        "windowTitleContains":"Confirm",
                        "actions":[
                          {
                            "controlType":"Button",
                            "name":"OK",
                            "pattern":"INVOKE"
                          }
                        ]
                      }
                    ]
                  }
                }
                """;
    }
}
