package com.developer.copilot.user.dto.profilelink;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;

@Getter
@Setter
@NoArgsConstructor
public class ProfileLinkRequest {

    @NotBlank(message = "URL is required.")
    @URL(message = "Must be a valid URL.")
    @Size(max = 500, message = "URL must not exceed 500 characters.")
    private String url;

}
