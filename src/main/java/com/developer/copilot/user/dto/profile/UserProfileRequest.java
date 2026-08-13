package com.developer.copilot.user.dto.profile;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserProfileRequest {

    @Size(max = 300, message = "Headline must not exceed 300 characters.")
    private String headline;

    @Size(max = 5000, message = "Summary must not exceed 5000 characters.")
    private String summary;

    @Size(max = 3000, message = "Technical skills must not exceed 3000 characters.")
    private String technicalSkills;

}
