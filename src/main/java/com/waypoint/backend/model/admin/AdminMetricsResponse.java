package com.waypoint.backend.model.admin;

import java.time.Instant;
import java.util.Map;

public record AdminMetricsResponse(
        String application,
        MeasurementWindow measurementWindow,
        CpuMetrics cpu,
        MemoryMetrics memory,
        DiskMetrics disk,
        DatabaseMetrics database,
        ThreadMetrics threads,
        HttpMetrics http,
        WaypointApiMetrics waypointApi
) {
    public record MeasurementWindow(
            String type,
            Instant startedAt,
            Instant generatedAt,
            double durationSeconds,
            String resetBehavior
    ) {}

    public record CpuMetrics(
            double processUsagePercent,
            double systemUsagePercent,
            int availableProcessors
    ) {}

    public record MemoryMetrics(
            double heapUsedMb,
            double heapCommittedMb,
            double heapMaxMb,
            double heapUsedPercent
    ) {}

    public record DiskMetrics(
            double freeGb,
            double totalGb,
            double usedPercent
    ) {}

    public record DatabaseMetrics(
            long activeConnections,
            long idleConnections,
            long maxConnections,
            long pendingThreads
    ) {}

    public record ThreadMetrics(
            long live,
            long daemon,
            long peak
    ) {}

    public record HttpMetrics(
            String scope,
            long requestsSinceStartup,
            double averageResponseTimeMsSinceStartup
    ) {}

    public record WaypointApiMetrics(
            String scope,
            long requestsSinceStartup,
            long errorsSinceStartup,
            double errorRatePercentSinceStartup,
            double averageResponseTimeMsSinceStartup,
            Map<String, Long> requestsByAreaSinceStartup,
            Map<String, Long> errorsByAreaSinceStartup,
            Map<String, String> areaScopes
    ) {}
}
