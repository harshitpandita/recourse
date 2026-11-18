package com.recourse.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class Requests {
    private Requests() {}

    public record CreateStudent(@NotBlank @Size(max = 80) String name, @NotBlank String stage) {}

    public record ContinueStudent(@NotBlank @Size(max = 80) String name, @NotBlank @Size(max = 20) String code) {}

    public record Answer(@NotBlank String questionId, @NotBlank String optionId) {}

    public record ContestEvidence(@NotBlank String evidenceId, @Size(max = 500) String reason) {}

    public record ContestRecommendation(@NotBlank String careerId, List<String> reasons,
                                        List<String> concerns, @Size(max = 500) String note) {}

    public record Feedback(@NotNull @Min(1) @Max(5) Integer matchesMadeSense,
                           @NotNull @Min(1) @Max(5) Integer explanationHelped,
                           @NotNull @Min(1) @Max(5) Integer pushingBackWorked,
                           @Size(max = 1000) String comment) {}
}
