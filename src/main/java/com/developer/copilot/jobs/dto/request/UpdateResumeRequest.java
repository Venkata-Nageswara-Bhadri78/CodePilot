package com.developer.copilot.jobs.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Binds this job to one of the caller's active resumes. "
        + "The resume must belong to the current user. Switching a resume recalculates resumeToJobScore.")
public class UpdateResumeRequest {

    @NotNull(message = "Resume is required.")
    @Schema(description = "Unique identifier of an active resume owned by the current user", example = "12")
    private Long resume;
}
