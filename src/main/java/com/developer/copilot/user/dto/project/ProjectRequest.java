package com.developer.copilot.user.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.developer.copilot.user.validation.HttpOrHttpsUrl;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProjectRequest {

    @NotBlank(message = "Project title is required.")
    @Size(max = 300, message = "Project title must not exceed 300 characters.")
    private String projectTitle;

    @Size(max = 5000, message = "Project description must not exceed 5000 characters.")
    private String projectDescription;

    @HttpOrHttpsUrl
    @Size(max = 500, message = "Project link must not exceed 500 characters.")
    private String projectLink;

}
