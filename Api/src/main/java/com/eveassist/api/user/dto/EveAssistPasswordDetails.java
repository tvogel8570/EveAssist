package com.eveassist.api.user.dto;

public record EveAssistPasswordDetails(
        String email,
        String currentPassword,
        String newPassword) {
}
