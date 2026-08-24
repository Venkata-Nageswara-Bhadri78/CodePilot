package com.developer.copilot.user.dto.education;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EducationRequest {

    @NotBlank(message = "Institution name is required.")
    @Size(max = 300, message = "Institution name must not exceed 300 characters.")
    private String institutionName;

    @NotBlank(message = "Field of study is required.")
    @Size(max = 300, message = "Field must not exceed 300 characters.")
    private String field;

    @NotNull(message = "Start year is required.")
    @Min(value = 1900, message = "Start year must be 1900 or later.")
    @Max(value = 2100, message = "Start year must not exceed 2100.")
    private Integer startYear;

    @Min(value = 1900, message = "End year must be 1900 or later.")
    @Max(value = 2100, message = "End year must not exceed 2100.")
    private Integer endYear;

    @Size(max = 50, message = "Score or grade must not exceed 50 characters.")
    private String scoreOrGrade;

    @AssertTrue(message = "End year must be greater than or equal to start year.")
    public boolean isEndYearValid() {
        if (endYear == null || startYear == null) {
            return true;
        }
        return endYear >= startYear;
    }

}
