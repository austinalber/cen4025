package edu.seminolestate.reservation.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.seminolestate.reservation.model.TeeTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "reservation.storage", havingValue = "json", matchIfMissing = true)
public class JsonReservationRepository implements ReservationRepository {

    @org.springframework.beans.factory.annotation.Value("${reservation.storage.file:data/teetimes.json}")
    private File storageFile;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private List<TeeTime> teeTimes = new ArrayList<>();

    @PostConstruct
    public void init() {
        loadData(); // Loads the dataset into memory exactly once upon application startup
    }

    private synchronized void loadData() {
        try {
            if (storageFile.exists()) {
                teeTimes = objectMapper.readValue(storageFile, new TypeReference<List<TeeTime>>() {});
            } else {
                File parent = storageFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    if (!parent.mkdirs() && !parent.exists()) {
                        throw new IOException("Could not create data directory: " + parent);
                    }
                }
                LocalDate today = LocalDate.now();
                teeTimes.add(new TeeTime(1L, today, "08:00 AM", 4, 0));
                teeTimes.add(new TeeTime(2L, today, "09:00 AM", 4, 1));
                teeTimes.add(new TeeTime(3L, today, "10:00 AM", 2, 2));
                saveData();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load tee times from " + storageFile, e);
        }
    }

    private synchronized void saveData() {
        try {
            File parent = storageFile.getParentFile();
            if (parent != null && !parent.exists()) {
                if (!parent.mkdirs() && !parent.exists()) {
                    throw new IOException("Could not create data directory: " + parent);
                }
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storageFile, teeTimes);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save tee times to " + storageFile, e);
        }
    }

    @Override
    public synchronized List<TeeTime> findAll() {
        // OPTIMIZATION: Removed loadData() here! 
        // Reads are now served instantly from RAM instead of hitting disk and parsing JSON.
        return new ArrayList<>(teeTimes);
    }

    @Override
    public synchronized List<TeeTime> findByDateRange(LocalDate from, LocalDate to) {
        List<TeeTime> matchingTeeTimes = new ArrayList<>();
        for (TeeTime teeTime : teeTimes) {
            if (!teeTime.getDate().isBefore(from) && !teeTime.getDate().isAfter(to)) {
                matchingTeeTimes.add(teeTime);
            }
        }
        return matchingTeeTimes;
    }

    @Override
    public synchronized Optional<TeeTime> findById(long id) {
        // OPTIMIZATION: Removed loadData() here!
        return teeTimes.stream().filter(t -> t.getId() == id).findFirst();
    }

    @Override
    public synchronized void create(TeeTime teeTime) {
        if (teeTimes.stream().anyMatch(existing -> existing.getId() == teeTime.getId())) {
            throw new IllegalArgumentException("A tee time with this ID already exists.");
        }
        teeTimes.add(teeTime);
        saveData();
    }

    @Override
    public synchronized void save(TeeTime teeTime) {
        // Removed redundant loadData() here; we already hold the latest state in memory.
        for (int i = 0; i < teeTimes.size(); i++) {
            if (teeTimes.get(i).getId() == teeTime.getId()) {
                teeTimes.set(i, teeTime);
                saveData(); // Persist changes to disk only when a modification occurs
                return;
            }
        }
    }

    @Override
    public synchronized boolean deleteById(long id) {
        boolean removed = teeTimes.removeIf(teeTime -> teeTime.getId() == id);
        if (removed) {
            saveData();
        }
        return removed;
    }
}