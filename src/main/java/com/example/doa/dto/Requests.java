package com.example.doa.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public final class Requests {
    public record Signup(@NotBlank @Pattern(regexp="[A-Za-z0-9_]{4,50}") String id,
        @NotBlank @Size(min=8,max=72) String password, @NotBlank @Size(max=50) String name,
        @NotBlank @Email @Size(max=100) String email, @NotBlank @Size(max=30) String phone,
        @NotBlank @Size(max=255) String address) {}
    public record Login(@NotBlank @Size(max=50) String id, @NotBlank @Size(max=72) String password) {}
    public record Cart(@Positive long productId, @NotBlank String color, @NotBlank String size, @Min(1) @Max(99) int quantity) {}
    public record Quantity(@Min(1) @Max(99) int quantity) {}
    public record Outfit(@NotBlank @Size(max=100) String title, @NotBlank @Size(max=2000) String text,
        @NotBlank @Size(max=2800000) String image, @NotNull @Size(max=20) List<@Positive Long> tags) {}
    public record Comment(@NotBlank @Size(max=500) String text, Long parentId) {}
    public record Order(@NotBlank @Size(max=50) String name, @NotBlank @Size(max=255) String address,
        @NotBlank @Size(max=30) String phone, @NotBlank @Email @Size(max=100) String email,
        @Pattern(regexp="CARD_DEMO|BANK_DEMO") @NotNull String payment) {}
    public record EmailRequest(@NotBlank @Email @Size(max=100) String email) {}
    public record Reset(@NotBlank @Size(max=100) String token, @NotBlank @Size(min=8,max=72) String password) {}
}
