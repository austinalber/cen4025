package edu.seminolestate.reservation.tcp;

import edu.seminolestate.reservation.model.TeeTime;
import edu.seminolestate.reservation.service.ReservationService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final ReservationService service;

    public ClientHandler(Socket clientSocket, ReservationService service) {
        this.clientSocket = clientSocket;
        this.service = service;
    }

    @Override
    public void run() {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            writer.println("=== Welcome to the Club Tee-Time Reservation System ===");
            writer.println("Commands: LIST | BOOK <id> | CANCEL <id> | QUIT");

            String inputLine;
            while ((inputLine = reader.readLine()) != null) {
                String command = inputLine.trim();
                String[] parts = command.split("\\s+");
                String action = parts[0].toUpperCase();

                switch (action) {
                    case "LIST":
                        List<TeeTime> times = service.getAllTeeTimes();
                        writer.println("--- Current Tee Times ---");
                        for (TeeTime t : times) {
                            writer.println(String.format("ID: %d | Time: %s | Capacity: %d/%d | Available: %b",
                                    t.getId(), t.getTimeSlot(), t.getBookedCount(), t.getCapacity(), t.isAvailable()));
                        }
                        writer.println("-------------------------");
                        break;

                    case "BOOK":
                        if (parts.length < 2) {
                            writer.println("ERROR: Missing tee time ID. Usage: BOOK <id>");
                            break;
                        }
                        try {
                            long id = Long.parseLong(parts[1]);
                            boolean success = service.bookTeeTime(id);
                            if (success) {
                                writer.println("SUCCESS: Tee time booked successfully.");
                            } else {
                                writer.println("FAIL: Tee time is fully booked or does not exist.");
                            }
                        } catch (NumberFormatException e) {
                            writer.println("ERROR: Invalid ID format.");
                        }
                        break;

                    case "CANCEL":
                        if (parts.length < 2) {
                            writer.println("ERROR: Missing tee time ID. Usage: CANCEL <id>");
                            break;
                        }
                        try {
                            long id = Long.parseLong(parts[1]);
                            boolean success = service.cancelReservation(id);
                            if (success) {
                                writer.println("SUCCESS: Reservation canceled successfully.");
                            } else {
                                writer.println("FAIL: No active reservations found for this slot or invalid ID.");
                            }
                        } catch (NumberFormatException e) {
                            writer.println("ERROR: Invalid ID format.");
                        }
                        break;

                    case "QUIT":
                        writer.println("Goodbye!");
                        return;

                    default:
                        writer.println("UNKNOWN COMMAND. Use LIST, BOOK <id>, CANCEL <id>, or QUIT.");
                }
            }
        } catch (IOException e) {
            System.err.println("Client connection error: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                // Ignored
            }
        }
    }
}