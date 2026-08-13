package com.oopsw.authservice.dto;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Builder
public class TokenResponse {
    private String accessToken;
    private String refreshToken;
}
