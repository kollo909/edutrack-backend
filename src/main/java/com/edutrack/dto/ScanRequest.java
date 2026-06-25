package com.edutrack.dto;

import jakarta.validation.constraints.NotBlank;

public record ScanRequest(
        @NotBlank String qrCode,
        String scanType   // "ARRIVAL" or "DEPARTURE"
) {}
