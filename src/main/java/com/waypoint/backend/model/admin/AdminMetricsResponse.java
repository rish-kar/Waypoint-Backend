package com.waypoint.backend.model.admin;

import java.time.Instant;
import java.util.Map;

public record AdminMetricsResponse(
        String application,
        Instant generatedAt,
        MeasurementWindow measurementWindow,
        CurrentSnapshot currentSnapshot,
        SinceApplicationStart sinceApplicationStart
) {
    public record MeasurementWindow(
            String type,
            Instant startedAt,
            double durationSeconds,
            boolean resetsOnBackendRestart
    ) {}

    public record CurrentSnapshot(
            CpuMetrics cpu,
            MemoryMetrics jvmMemory,
            DiskMetrics disk,
            DatabasePoolMetrics databasePool,
            ThreadMetrics jvmThreads
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

    public record DatabasePoolMetrics(
            long activeConnections,
            long idleConnections,
            long maxConnections,
            long pendingThreads
    ) {}

    public record ThreadMetrics(
            long liveThreads,
            long daemonThreads,
            long peakThreads
    ) {}

    public record SinceApplicationStart(
            HttpTrafficMetrics httpTraffic,
            WaypointApiMetrics waypointApi
    ) {}

    public record HttpTrafficMetrics(
            String scope,
            long requestCount,
            double averageResponseTimeMs
    ) {}

    public record WaypointApiMetrics(
            String scope,
            long requestCount,
            long errorCount,
            double errorRatePercent,
            double averageResponseTimeMs,
            Map<String, Long> requestsByArea,
            Map<String, Long> errorsByArea,
            Map<String, String> areaScopes,
            String excludedEndpoint
    ) {}
}
