package com.team_E_commerce.core.claim.dto;

import java.time.LocalDate;

public record ClaimSearchCondition(
        LocalDate startDate,
        LocalDate endDate,
        String claimType,
        String claimStatus
) {}