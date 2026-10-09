package com.example.mediavault.catalog;

import java.time.Instant;

public record LocationDto(String id, String sourceId, String sourceName, String sourceType,
                          String reportedStatus, Instant statusReportedAt, String locatorType,
                          String locator, String note) {}
