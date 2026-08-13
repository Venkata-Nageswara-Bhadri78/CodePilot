package com.developer.copilot.user.dto.additionalinfo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;

@Getter
@Setter
@NoArgsConstructor
public class AdditionalProfileInformationRequest {

    @NotBlank(message = "Type is required.")
    @Size(max = 100, message = "Type must not exceed 100 characters.")
    private String type;

    @Size(max = 5000, message = "Description must not exceed 5000 characters.")
    private String description;

    @URL(message = "Link must be a valid URL.")
    @Size(max = 500, message = "Link must not exceed 500 characters.")
    private String link;

}
