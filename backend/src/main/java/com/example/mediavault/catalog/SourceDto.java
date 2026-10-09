package com.example.mediavault.catalog;

import java.time.Instant;

public record SourceDto(String id, String name, String type, String reportedStatus,
                        Instant statusReportedAt, String note, long version) {}
