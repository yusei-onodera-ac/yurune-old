package app.yurune.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.constant.AuditEventSchema;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * 監視の定義（deploy/monitoring）の整合。本物の Cloud Monitoring に作れたかどうかは、deploy/verify.ps1 が確かめる。
 * ここでは、設定ファイルが JSON として読めること、メトリクスの絞り込みが参照する
 * event と項目名が、実在する種類・許可された項目名であること、アラートとダッシュボードが参照するメトリクス
 * （またはアップタイムの確認）が、定義されていることまでを確かめる。
 */
class MonitoringConfigPolicyTest {

    private static JsonObject readJson(String file) throws IOException {
        Path path = Path.of(file);
        Assumptions.assumeTrue(Files.exists(path), file + " がないので飛ばす");
        String text = Files.readString(path, StandardCharsets.UTF_8);
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void logMetricsOnlyReferenceKnownEventsAndAllowedFieldNames() throws IOException {
        JsonObject config = readJson("deploy/monitoring/log-metrics.json");

        for (JsonElement element : config.getAsJsonArray("metrics")) {
            JsonObject metric = element.getAsJsonObject();
            String name = metric.get("name").getAsString();
            String event = metric.get("event").getAsString();
            assertTrue(AuditEventSchema.isKnownEvent(event), name + " が参照する種類が未知: " + event);

            Set<String> allowed = AuditEventSchema.allowedFields(event);
            for (JsonElement fieldElement : metric.getAsJsonArray("fields")) {
                String field = fieldElement.getAsString();
                assertTrue(allowed.contains(field), name + " の " + event + " に、許可されていない項目: " + field);
            }
        }
    }

    @Test
    void everyMetricNameIsUnique() throws IOException {
        JsonObject config = readJson("deploy/monitoring/log-metrics.json");

        List<String> names = new ArrayList<>();
        for (JsonElement element : config.getAsJsonArray("metrics")) {
            names.add(element.getAsJsonObject().get("name").getAsString());
        }
        assertEquals(new TreeSet<>(names).size(), names.size(), "メトリクスの名前が重複している: " + names);
    }

    @Test
    void alertPoliciesOnlyReferenceDefinedMetricsOrUptimeChecks() throws IOException {
        Set<String> metricNames = metricNames();
        JsonObject alertConfig = readJson("deploy/monitoring/alert-policies.json");

        Set<String> uptimeCheckNames = new TreeSet<>();
        for (JsonElement element : alertConfig.getAsJsonArray("uptimeChecks")) {
            uptimeCheckNames.add(element.getAsJsonObject().get("name").getAsString());
        }

        for (JsonElement element : alertConfig.getAsJsonArray("policies")) {
            JsonObject policy = element.getAsJsonObject();
            String name = policy.get("name").getAsString();
            if (policy.has("metric")) {
                String metric = policy.get("metric").getAsString();
                assertTrue(metricNames.contains(metric), name + " が参照するメトリクスが未定義: " + metric);
            } else if (policy.has("uptimeCheck")) {
                String uptimeCheck = policy.get("uptimeCheck").getAsString();
                assertTrue(uptimeCheckNames.contains(uptimeCheck), name + " が参照するアップタイムの確認が未定義: " + uptimeCheck);
            } else {
                throw new AssertionError(name + " は、metric も uptimeCheck も持たない");
            }
        }
    }

    @Test
    void alertPoliciesLeaveNotificationChannelsEmptyForTheUserToDecide() throws IOException {
        JsonObject alertConfig = readJson("deploy/monitoring/alert-policies.json");

        assertTrue(alertConfig.getAsJsonArray("notificationChannels").isEmpty(),
                "通知先は、ユーザーが決める（ここでは作らない）");
    }

    @Test
    void dashboardWidgetsOnlyReferenceDefinedMetrics() throws IOException {
        Set<String> metricNames = metricNames();
        JsonObject dashboardConfig = readJson("deploy/monitoring/dashboard.json");

        for (JsonElement element : dashboardConfig.getAsJsonArray("widgets")) {
            JsonObject widget = element.getAsJsonObject();
            String metric = widget.get("metric").getAsString();
            assertTrue(metricNames.contains(metric), widget.get("title").getAsString() + " が参照するメトリクスが未定義: " + metric);
        }
    }

    private static Set<String> metricNames() throws IOException {
        JsonObject config = readJson("deploy/monitoring/log-metrics.json");
        Set<String> names = new TreeSet<>();
        for (JsonElement element : config.getAsJsonArray("metrics")) {
            names.add(element.getAsJsonObject().get("name").getAsString());
        }
        return names;
    }

    @Test
    void applyMonitoringScriptIsAsciiAndReadsEveryConfigFile() throws IOException {
        Path script = Path.of("deploy/apply-monitoring.ps1");
        Assumptions.assumeTrue(Files.exists(script), "deploy/apply-monitoring.ps1 がないので飛ばす");
        String content = Files.readString(script, StandardCharsets.UTF_8);

        for (byte value : Files.readAllBytes(script)) {
            assertTrue(value >= 0, "ASCII 以外の文字がある（PowerShell 5.1 が壊す）");
        }
        assertTrue(content.contains("log-metrics.json"));
        assertTrue(content.contains("alert-policies.json"));
        assertTrue(content.contains("dashboard.json"));
    }
}
