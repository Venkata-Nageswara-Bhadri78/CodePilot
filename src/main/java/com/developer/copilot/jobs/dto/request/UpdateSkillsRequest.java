package com.developer.copilot.jobs.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateSkillsRequest {

    @NotEmpty(message = "Skills list cannot be empty.")
    private List<String> skills;
}
