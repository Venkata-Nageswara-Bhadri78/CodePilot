package com.developer.copilot.user.dto.profile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "PUT replaces these three fields. JSON null (or an omitted key) clears the stored value. Children are not part of this body.")
public class UserProfileRequest {

    @Size(max = 300, message = "Headline must not exceed 300 characters.")
    @Schema(maxLength = 300)
    private String headline;

    @Size(max = 5000, message = "Summary must not exceed 5000 characters.")
    @Schema(maxLength = 5000)
    private String summary;

    @Size(max = 3000, message = "Technical skills must not exceed 3000 characters.")
    @Schema(maxLength = 3000)
    private String technicalSkills;

}
