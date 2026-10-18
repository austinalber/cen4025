package edu.seminolestate.reservation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ReservationRequest {

    @NotBlank(message = "Member name is required")
    @Size(max = 100, message = "Member name must not exceed 100 characters")
    private String memberName;

    @NotBlank(message = "Email is required")
    @Email(message = "Must provide a valid email address")
    private String email;

    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}