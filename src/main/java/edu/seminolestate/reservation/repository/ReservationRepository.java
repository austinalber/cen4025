package edu.seminolestate.reservation.repository;

import edu.seminolestate.reservation.model.TeeTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository {
    List<TeeTime> findAll();
    List<TeeTime> findByDateRange(LocalDate from, LocalDate to);
    Optional<TeeTime> findById(long id);
    void create(TeeTime teeTime);
    void save(TeeTime teeTime);
    boolean deleteById(long id);
}