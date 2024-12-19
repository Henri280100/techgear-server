package com.v01.techgear_server.dto;

import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO for resetting password")
public class ResetPasswordDTO {
    @Schema(description = "Token for password reset", example = "abc123")
    private String token;

    @Schema(description = "New password", example = "newPassword123")
    private String newPassword;
}
