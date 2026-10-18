package edu.seminolestate.reservation.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class TeeTimeRequest {

    @NotNull(message = "Date is required")
    private LocalDate date;

    @NotBlank(message = "Time slot is required")
    private String timeSlot;

    @NotNull(message = "Capacity is required")
    @Min(value = 1, message = "Capacity must be at least 1")
    private Integer capacity;

    @NotNull(message = "Booked count is required")
    @Min(value = 0, message = "Booked count cannot be negative")
    private Integer bookedCount;

    @AssertTrue(message = "Booked count cannot exceed capacity")
    public boolean isBookingWithinCapacity() {
        return capacity == null || bookedCount == null || bookedCount <= capacity;
    }

    public String getTimeSlot() {
        return timeSlot;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public void setTimeSlot(String timeSlot) {
        this.timeSlot = timeSlot;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public Integer getBookedCount() {
        return bookedCount;
    }

    public void setBookedCount(Integer bookedCount) {
        this.bookedCount = bookedCount;
    }
}
