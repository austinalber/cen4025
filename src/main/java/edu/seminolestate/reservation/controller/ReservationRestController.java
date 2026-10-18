package edu.seminolestate.reservation.controller;

import edu.seminolestate.reservation.dto.ReservationRequest;
import edu.seminolestate.reservation.dto.TeeTimeRequest;
import edu.seminolestate.reservation.model.TeeTime;
import edu.seminolestate.reservation.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reservations")
public class ReservationRestController {

    private final ReservationService service;

    public ReservationRestController(ReservationService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<TeeTime>> getAllTeeTimes(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        if ((from == null) != (to == null) || (from != null && from.isAfter(to))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide a valid from/to date range.");
        }
        if (from != null) {
            return ResponseEntity.ok(service.getTeeTimes(from, to));
        }
        return ResponseEntity.ok(service.getAllTeeTimes());
    }

    @PostMapping
    public ResponseEntity<TeeTime> createTeeTime(@Valid @RequestBody TeeTimeRequest request) {
        TeeTime teeTime = service.createTeeTime(
                request.getDate(), request.getTimeSlot(), request.getCapacity(), request.getBookedCount());
        return ResponseEntity.status(HttpStatus.CREATED).body(teeTime);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TeeTime> updateTeeTime(
            @PathVariable long id, @Valid @RequestBody TeeTimeRequest request) {
        return service.updateTeeTime(
                        id, request.getDate(), request.getTimeSlot(),
                        request.getCapacity(), request.getBookedCount())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTeeTime(@PathVariable long id) {
        return service.deleteTeeTime(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TeeTime> getTeeTime(@PathVariable long id) {
        return service.getTeeTime(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PostMapping("/{id}/book")
    public ResponseEntity<String> bookTeeTime(
            @PathVariable long id, 
            @Valid @RequestBody ReservationRequest request) {
        boolean success = service.bookTeeTime(id);
        if (success) {
            return ResponseEntity.ok("Successfully booked tee time for " + request.getMemberName());
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body("Tee time is fully booked or does not exist.");
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<String> cancelTeeTime(@PathVariable long id) {
        boolean success = service.cancelReservation(id);
        if (success) {
            return ResponseEntity.ok("Reservation successfully canceled.");
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("No active reservation to cancel.");
    }
}