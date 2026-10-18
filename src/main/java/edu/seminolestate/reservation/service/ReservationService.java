package edu.seminolestate.reservation.service;

import edu.seminolestate.reservation.model.TeeTime;
import edu.seminolestate.reservation.repository.ReservationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class ReservationService {

    private final ReservationRepository repository;
    private final ReentrantLock lock = new ReentrantLock();

    public ReservationService(ReservationRepository repository) {
        this.repository = repository;
    }

    public List<TeeTime> getAllTeeTimes() {
        return repository.findAll();
    }

    public List<TeeTime> getTeeTimes(LocalDate from, LocalDate to) {
        return repository.findByDateRange(from, to);
    }

    public Optional<TeeTime> getTeeTime(long id) {
        return repository.findById(id);
    }

    public TeeTime createTeeTime(LocalDate date, String timeSlot, int capacity, int bookedCount) {
        lock.lock();
        try {
            long nextId = repository.findAll().stream()
                    .mapToLong(TeeTime::getId)
                    .max()
                    .orElse(0L) + 1;
            TeeTime teeTime = new TeeTime(nextId, date, timeSlot, capacity, bookedCount);
            repository.create(teeTime);
            return teeTime;
        } finally {
            lock.unlock();
        }
    }

    public Optional<TeeTime> updateTeeTime(
            long id, LocalDate date, String timeSlot, int capacity, int bookedCount) {
        lock.lock();
        try {
            return repository.findById(id).map(existing -> {
                existing.setDate(date);
                existing.setTimeSlot(timeSlot);
                existing.setCapacity(capacity);
                existing.setBookedCount(bookedCount);
                repository.save(existing);
                return existing;
            });
        } finally {
            lock.unlock();
        }
    }

    public boolean deleteTeeTime(long id) {
        lock.lock();
        try {
            return repository.deleteById(id);
        } finally {
            lock.unlock();
        }
    }

    public boolean bookTeeTime(long id) {
        lock.lock();
        try {
            Optional<TeeTime> optionalTeeTime = repository.findById(id);
            if (optionalTeeTime.isPresent()) {
                TeeTime teeTime = optionalTeeTime.get();
                boolean success = teeTime.bookSlot();
                if (success) {
                    repository.save(teeTime);
                    sendAsyncConfirmation(teeTime);
                }
                return success;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public boolean cancelReservation(long id) {
        lock.lock();
        try {
            Optional<TeeTime> optionalTeeTime = repository.findById(id);
            if (optionalTeeTime.isPresent()) {
                TeeTime teeTime = optionalTeeTime.get();
                boolean success = teeTime.cancelSlot();
                if (success) {
                    repository.save(teeTime);
                }
                return success;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    private CompletableFuture<Void> sendAsyncConfirmation(TeeTime teeTime) {
        return CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(100);
                System.out.println("[Async Notification] Confirmation logged for slot: " + teeTime.getTimeSlot());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
}