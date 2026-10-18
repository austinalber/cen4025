package edu.seminolestate.reservation.tcp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class TcpClient {
    public static void main(String[] args) {
        String host = "localhost";
        int port = 8888;

        try (Socket socket = new Socket(host, port);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("=========================================");
            System.out.println("   CLUB RESERVATION INTERACTIVE CLIENT   ");
            System.out.println("=========================================");
            System.out.println("Connected to TCP server on port " + port);
            System.out.println("Commands:");
            System.out.println("  • LIST");
            System.out.println("  • BOOK <id>   (e.g., BOOK 1)");
            System.out.println("  • CANCEL <id> (e.g., CANCEL 1)");
            System.out.println("  • QUIT");
            System.out.println("-----------------------------------------");

            while (true) {
                System.out.print("\nClient> ");
                String input = scanner.nextLine();
                if (input == null || input.trim().isEmpty()) continue;

                writer.println(input);

                if ("QUIT".equalsIgnoreCase(input.trim())) {
                    System.out.println("Closing connection. Goodbye!");
                    break;
                }

                // Read response from server
                String response = reader.readLine();
                if (response != null) {
                    System.out.println("Server> " + response);
                } else {
                    System.out.println("Server disconnected.");
                    break;
                }
            }

        } catch (Exception e) {
            System.out.println("[Client Error] Could not connect to server: " + e.getMessage());
        }
    }
}