package edu.seminolestate.reservation.repository;

import edu.seminolestate.reservation.model.TeeTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "reservation.storage", havingValue = "database")
public class DatabaseReservationRepository implements ReservationRepository {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseReservationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initTable() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS tee_times (" +
                "id BIGINT PRIMARY KEY, " +
                "tee_date DATE, " +
                "time_slot VARCHAR(50), " +
                "capacity INT, " +
                "booked_count INT)");
        jdbcTemplate.execute("ALTER TABLE tee_times ADD COLUMN IF NOT EXISTS tee_date DATE DEFAULT CURRENT_DATE");

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tee_times", Integer.class);
        if (count != null && count == 0) {
            LocalDate today = LocalDate.now();
            jdbcTemplate.update(
                    "INSERT INTO tee_times (id, tee_date, time_slot, capacity, booked_count) VALUES (1, ?, '08:00 AM', 4, 0)",
                    Date.valueOf(today));
            jdbcTemplate.update(
                    "INSERT INTO tee_times (id, tee_date, time_slot, capacity, booked_count) VALUES (2, ?, '09:00 AM', 4, 1)",
                    Date.valueOf(today));
            jdbcTemplate.update(
                    "INSERT INTO tee_times (id, tee_date, time_slot, capacity, booked_count) VALUES (3, ?, '10:00 AM', 2, 2)",
                    Date.valueOf(today));
        }
    }

    private final RowMapper<TeeTime> rowMapper = (rs, rowNum) -> new TeeTime(
            rs.getLong("id"),
            rs.getDate("tee_date").toLocalDate(),
            rs.getString("time_slot"),
            rs.getInt("capacity"),
            rs.getInt("booked_count")
    );

    @Override
    public List<TeeTime> findAll() {
        return jdbcTemplate.query("SELECT * FROM tee_times", rowMapper);
    }

    @Override
    public List<TeeTime> findByDateRange(LocalDate from, LocalDate to) {
        return jdbcTemplate.query(
                "SELECT * FROM tee_times WHERE tee_date >= ? AND tee_date <= ? ORDER BY id",
                rowMapper, Date.valueOf(from), Date.valueOf(to));
    }

    @Override
    public Optional<TeeTime> findById(long id) {
        List<TeeTime> results = jdbcTemplate.query("SELECT * FROM tee_times WHERE id = ?", rowMapper, id);
        return results.stream().findFirst();
    }

    @Override
    public void create(TeeTime teeTime) {
        jdbcTemplate.update(
                "INSERT INTO tee_times (id, tee_date, time_slot, capacity, booked_count) VALUES (?, ?, ?, ?, ?)",
                teeTime.getId(), Date.valueOf(teeTime.getDate()), teeTime.getTimeSlot(),
                teeTime.getCapacity(), teeTime.getBookedCount()
        );
    }

    @Override
    public void save(TeeTime teeTime) {
        jdbcTemplate.update(
                "UPDATE tee_times SET tee_date = ?, time_slot = ?, capacity = ?, booked_count = ? WHERE id = ?",
                Date.valueOf(teeTime.getDate()), teeTime.getTimeSlot(),
                teeTime.getCapacity(), teeTime.getBookedCount(), teeTime.getId()
        );
    }

    @Override
    public boolean deleteById(long id) {
        return jdbcTemplate.update("DELETE FROM tee_times WHERE id = ?", id) > 0;
    }
}