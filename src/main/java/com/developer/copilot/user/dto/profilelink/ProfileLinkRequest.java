package com.developer.copilot.user.dto.profilelink;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.developer.copilot.user.validation.HttpOrHttpsUrl;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProfileLinkRequest {

    @NotBlank(message = "URL is required.")
    @HttpOrHttpsUrl
    @Size(max = 500, message = "URL must not exceed 500 characters.")
    private String url;

}
