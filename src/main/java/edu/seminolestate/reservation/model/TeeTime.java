package edu.seminolestate.reservation.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TeeTime {
    private long id;
    private LocalDate date = LocalDate.now();
    private String timeSlot;
    private int capacity;
    private int bookedCount;

    public TeeTime() {}

    public TeeTime(long id, String timeSlot, int capacity, int bookedCount) {
        this(id, LocalDate.now(), timeSlot, capacity, bookedCount);
    }

    public TeeTime(long id, LocalDate date, String timeSlot, int capacity, int bookedCount) {
        this.id = id;
        this.date = date;
        this.timeSlot = timeSlot;
        this.capacity = capacity;
        this.bookedCount = bookedCount;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getTimeSlot() { return timeSlot; }
    public void setTimeSlot(String timeSlot) { this.timeSlot = timeSlot; }

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }

    public int getBookedCount() { return bookedCount; }
    public void setBookedCount(int bookedCount) { this.bookedCount = bookedCount; }

    public boolean isAvailable() {
        return this.bookedCount < this.capacity;
    }

    public synchronized boolean bookSlot() {
        if (isAvailable()) {
            this.bookedCount++;
            return true;
        }
        return false;
    }

    public synchronized boolean cancelSlot() {
        if (this.bookedCount > 0) {
            this.bookedCount--;
            return true;
        }
        return false;
    }
}