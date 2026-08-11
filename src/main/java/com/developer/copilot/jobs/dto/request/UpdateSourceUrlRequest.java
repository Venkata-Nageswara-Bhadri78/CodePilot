package com.developer.copilot.jobs.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateSourceUrlRequest {

    @NotBlank(message = "Source URL cannot be blank.")
    @Size(max = 2000, message = "Source URL cannot exceed 2000 characters.")
    private String sourceUrl;
}
