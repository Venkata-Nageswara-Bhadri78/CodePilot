package com.developer.copilot.jobs.dto.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Replaces the full skills list. Send an empty array to clear skills.")
public class UpdateSkillsRequest {

    @NotNull(message = "Skills list is required.")
    @ArraySchema(schema = @Schema(maxLength = 255, example = "Java"))
    private List<@Size(max = 255, message = "Each skill cannot exceed 255 characters.") String> skills;
}
