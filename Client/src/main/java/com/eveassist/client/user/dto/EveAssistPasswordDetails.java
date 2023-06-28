package com.eveassist.client.user.dto;

public record EveAssistPasswordDetails(
        String email,
        String currentPassword,
        String newPassword) {
}
