package edu.seminolestate.reservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.seminolestate.reservation.dto.ReservationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ReservationRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void whenGetAllTeeTimes_thenReturnsOk() throws Exception {
        mockMvc.perform(get("/api/reservations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void whenDateRangeIsProvided_thenOnlyReturnsTeeTimesWithinRange() throws Exception {
        LocalDate today = LocalDate.now();
        mockMvc.perform(get("/api/reservations")
                        .param("from", today.toString())
                        .param("to", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].date").value(today.toString()));

        mockMvc.perform(get("/api/reservations")
                        .param("from", today.plusDays(1).toString())
                        .param("to", today.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void whenDateRangeIsProvided_thenIncludesBoundsAndPreservesCreationOrder() throws Exception {
        long laterDateId = createTeeTime("2099-01-02");
        long earlierDateId = createTeeTime("2099-01-01");

        try {
            mockMvc.perform(get("/api/reservations")
                            .param("from", "2099-01-01")
                            .param("to", "2099-01-02"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value(laterDateId))
                    .andExpect(jsonPath("$[1].id").value(earlierDateId));

            mockMvc.perform(get("/api/reservations")
                            .param("from", "2099-01-02")
                            .param("to", "2099-01-02"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(laterDateId));
        } finally {
            mockMvc.perform(delete("/api/reservations/" + laterDateId));
            mockMvc.perform(delete("/api/reservations/" + earlierDateId));
        }
    }

    @Test
    void whenValidBookingRequest_thenReturnsOk() throws Exception {
        ReservationRequest request = new ReservationRequest();
        request.setMemberName("Jane Doe");
        request.setEmail("jane.doe@club.com");

        mockMvc.perform(post("/api/reservations/2/book")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void whenInvalidEmailBooking_thenReturnsBadRequest() throws Exception {
        ReservationRequest request = new ReservationRequest();
        request.setMemberName("Jane Doe");
        request.setEmail("invalid-email-format");

        mockMvc.perform(post("/api/reservations/1/book")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.email").exists());
    }

    @Test
    void whenManagingTeeTime_thenCreatesUpdatesAndDeletesIt() throws Exception {
        String createRequest = """
                {"date":"2099-07-01","timeSlot":"03:30 PM","capacity":3,"bookedCount":1}
                """;
        String response = mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.date").value("2099-07-01"))
                .andExpect(jsonPath("$.timeSlot").value("03:30 PM"))
                .andExpect(jsonPath("$.capacity").value(3))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(put("/api/reservations/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2099-07-02","timeSlot":"04:00 PM","capacity":4,"bookedCount":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2099-07-02"))
                .andExpect(jsonPath("$.timeSlot").value("04:00 PM"))
                .andExpect(jsonPath("$.bookedCount").value(2));

        mockMvc.perform(get("/api/reservations")
                        .param("from", "2099-07-01")
                        .param("to", "2099-07-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/reservations")
                        .param("from", "2099-07-02")
                        .param("to", "2099-07-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id));

        mockMvc.perform(delete("/api/reservations/" + id))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/reservations/" + id))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/reservations")
                        .param("from", "2099-07-02")
                        .param("to", "2099-07-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void whenBookedCountExceedsCapacity_thenRejectsTeeTime() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2026-10-22","timeSlot":"05:00 PM","capacity":2,"bookedCount":3}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void whenDateIsMissing_thenRejectsTeeTime() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"timeSlot":"05:00 PM","capacity":2,"bookedCount":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.date").value("Date is required"));
    }

    private long createTeeTime(String date) throws Exception {
        String response = mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"%s","timeSlot":"08:00 AM","capacity":4,"bookedCount":0}
                                """.formatted(date)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }
}