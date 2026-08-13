package com.developer.copilot.user.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;

@Getter
@Setter
@NoArgsConstructor
public class ProjectRequest {

    @NotBlank(message = "Project title is required.")
    @Size(max = 300, message = "Project title must not exceed 300 characters.")
    private String projectTitle;

    @Size(max = 5000, message = "Project description must not exceed 5000 characters.")
    private String projectDescription;

    @URL(message = "Project link must be a valid URL.")
    @Size(max = 500, message = "Project link must not exceed 500 characters.")
    private String projectLink;

}
