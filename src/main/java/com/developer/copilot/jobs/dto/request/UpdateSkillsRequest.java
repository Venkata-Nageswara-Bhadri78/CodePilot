package com.developer.copilot.jobs.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Replaces the full skills list for a job")
public class UpdateSkillsRequest {

    @NotEmpty(message = "Skills list cannot be empty.")
    private List<@Size(max = 255, message = "Each skill cannot exceed 255 characters.") String> skills;
}
