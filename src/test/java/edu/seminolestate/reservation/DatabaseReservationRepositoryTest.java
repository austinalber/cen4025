package edu.seminolestate.reservation;

import edu.seminolestate.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "reservation.storage=database")
class DatabaseReservationRepositoryTest {

    @Autowired
    private ReservationRepository repository;

    @Test
    void findByDateRangeReturnsInclusiveMatchesInIdOrder() {
        LocalDate today = LocalDate.now();

        var results = repository.findByDateRange(today, today);

        assertEquals(3, results.size());
        assertTrue(results.stream().allMatch(teeTime -> teeTime.getDate().equals(today)));
        assertEquals(
                results.stream().map(teeTime -> teeTime.getId()).sorted().toList(),
                results.stream().map(teeTime -> teeTime.getId()).toList());
        assertTrue(repository.findByDateRange(today.plusDays(1), today.plusDays(1)).isEmpty());
    }
}
