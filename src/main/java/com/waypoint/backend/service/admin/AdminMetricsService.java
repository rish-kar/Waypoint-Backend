package com.waypoint.backend.service.admin;

import com.waypoint.backend.model.admin.AdminMetricsResponse;

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

@Service
public class AdminMetricsService {
    private static final double BYTES_PER_MB = 1024.0 * 1024.0;
    private static final double BYTES_PER_GB = 1024.0 * 1024.0 * 1024.0;

    private final MeterRegistry meterRegistry;
    private final String applicationName;

    public AdminMetricsService(
            MeterRegistry meterRegistry,
            @Value("${spring.application.name:Waypoint-Backend}") String applicationName
    ) {
        this.meterRegistry = meterRegistry;
        this.applicationName = applicationName;
    }

    public AdminMetricsResponse snapshot() {
        double heapUsed = sumValue("jvm.memory.used", "area", "heap", false);
        double heapCommitted = sumValue("jvm.memory.committed", "area", "heap", false);
        double heapMax = sumValue("jvm.memory.max", "area", "heap", true);

        double diskFree = value("disk.free");
        double diskTotal = value("disk.total");

        double httpCount = statistic("http.server.requests", Statistic.COUNT);
        double httpTotalTime = statistic("http.server.requests", Statistic.TOTAL_TIME);

        double waypointCount = statistic("waypoint.api.requests", Statistic.COUNT);
        double waypointErrors = statistic("waypoint.api.errors", Statistic.COUNT);
        double waypointDurationCount = statistic("waypoint.api.request.duration", Statistic.COUNT);
        double waypointDurationTotal = statistic("waypoint.api.request.duration", Statistic.TOTAL_TIME);

        Instant generatedAt = Instant.now();
        double uptimeSeconds = value("process.uptime");
        double processStartEpochSeconds = value("process.start.time");
        Instant startedAt = processStartEpochSeconds > 0
                ? Instant.ofEpochMilli(Math.round(processStartEpochSeconds * 1000.0))
                : generatedAt.minusMillis(Math.round(uptimeSeconds * 1000.0));

        return new AdminMetricsResponse(
                applicationName,
                generatedAt,
                new AdminMetricsResponse.MeasurementWindow(
                        "SINCE_APPLICATION_START",
                        startedAt,
                        round(uptimeSeconds, 3),
                        true
                ),
                new AdminMetricsResponse.CurrentSnapshot(
                        new AdminMetricsResponse.CpuMetrics(
                                percent(value("process.cpu.usage")),
                                percent(value("system.cpu.usage")),
                                (int) Math.round(value("system.cpu.count"))
                        ),
                        new AdminMetricsResponse.MemoryMetrics(
                                round(heapUsed / BYTES_PER_MB, 2),
                                round(heapCommitted / BYTES_PER_MB, 2),
                                round(heapMax / BYTES_PER_MB, 2),
                                ratioPercent(heapUsed, heapMax)
                        ),
                        new AdminMetricsResponse.DiskMetrics(
                                round(diskFree / BYTES_PER_GB, 2),
                                round(diskTotal / BYTES_PER_GB, 2),
                                diskTotal > 0 ? round(((diskTotal - diskFree) / diskTotal) * 100.0, 2) : 0.0
                        ),
                        new AdminMetricsResponse.DatabasePoolMetrics(
                                Math.round(value("hikaricp.connections.active")),
                                Math.round(value("hikaricp.connections.idle")),
                                Math.round(value("hikaricp.connections.max")),
                                Math.round(value("hikaricp.connections.pending"))
                        ),
                        new AdminMetricsResponse.ThreadMetrics(
                                Math.round(value("jvm.threads.live")),
                                Math.round(value("jvm.threads.daemon")),
                                Math.round(value("jvm.threads.peak"))
                        )
                ),
                new AdminMetricsResponse.SinceApplicationStart(
                        new AdminMetricsResponse.HttpTrafficMetrics(
                                "ALL_BACKEND_HTTP_REQUESTS",
                                Math.round(httpCount),
                                averageMilliseconds(httpTotalTime, httpCount)
                        ),
                        new AdminMetricsResponse.WaypointApiMetrics(
                                "/api/v1/**",
                                Math.round(waypointCount),
                                Math.round(waypointErrors),
                                ratioPercent(waypointErrors, waypointCount),
                                averageMilliseconds(waypointDurationTotal, waypointDurationCount),
                                countByTag("waypoint.api.requests", "area"),
                                countByTag("waypoint.api.errors", "area"),
                                Map.of(
                                        "auth", "/api/v1/auth/**",
                                        "ai", "/api/v1/ai/**",
                                        "billing", "/api/v1/billing/**",
                                        "webhook", "/api/v1/webhooks/**",
                                        "admin", "/api/v1/admin/**",
                                        "account", "/api/v1/account**",
                                        "entitlement", "/api/v1/entitlements**",
                                        "subscription", "/api/v1/subscriptions**",
                                        "other", "other /api/v1/** routes"
                                ),
                                "/api/v1/admin/metrics"
                        )
                )
        );
    }

    private double value(String name) {
        return statistic(name, Statistic.VALUE);
    }

    private double sumValue(
            String name,
            String tagKey,
            String tagValue,
            boolean positiveOnly
    ) {
        double total = 0.0;
        for (Meter meter : meterRegistry.find(name).meters()) {
            if (!tagValue.equals(meter.getId().getTag(tagKey))) {
                continue;
            }
            for (Measurement measurement : meter.measure()) {
                if (measurement.getStatistic() == Statistic.VALUE
                        && (!positiveOnly || measurement.getValue() > 0)) {
                    total += measurement.getValue();
                }
            }
        }
        return total;
    }

    private double statistic(String name, Statistic statistic) {
        double total = 0.0;
        for (Meter meter : meterRegistry.find(name).meters()) {
            for (Measurement measurement : meter.measure()) {
                if (measurement.getStatistic() == statistic) {
                    total += measurement.getValue();
                }
            }
        }
        return total;
    }

    private Map<String, Long> countByTag(String name, String tagKey) {
        Map<String, Long> counts = new TreeMap<>();
        for (Meter meter : meterRegistry.find(name).meters()) {
            String tag = meter.getId().getTag(tagKey);
            if (tag == null) {
                tag = "other";
            }
            long count = 0L;
            for (Measurement measurement : meter.measure()) {
                if (measurement.getStatistic() == Statistic.COUNT) {
                    count += Math.round(measurement.getValue());
                }
            }
            counts.merge(tag, count, Long::sum);
        }
        return counts;
    }

    private double averageMilliseconds(double totalSeconds, double count) {
        return count > 0 ? round((totalSeconds / count) * 1000.0, 2) : 0.0;
    }

    private double ratioPercent(double used, double max) {
        return max > 0 ? round((used / max) * 100.0, 2) : 0.0;
    }

    private double percent(double ratio) {
        return round(ratio * 100.0, 2);
    }

    private double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
