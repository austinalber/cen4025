package edu.seminolestate.reservation;

import edu.seminolestate.reservation.model.TeeTime;
import edu.seminolestate.reservation.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ConcurrencyTest {

    @Autowired
    private ReservationService reservationService;

    @Test
    void testConcurrentBookingRaceCondition() throws InterruptedException {
        long teeTimeId = 1L;

        int numberOfThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                boolean booked = reservationService.bookTeeTime(teeTimeId);
                if (booked) {
                    successCount.incrementAndGet();
                } else {
                    failCount.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        TeeTime teeTime = reservationService.getTeeTime(teeTimeId).orElseThrow();
        assertTrue(teeTime.getBookedCount() <= teeTime.getCapacity(), "Booked count exceeded capacity!");
        System.out.println("Successful concurrent bookings: " + successCount.get() + " (Capacity: " + teeTime.getCapacity() + ")");
    }
}