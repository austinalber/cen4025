package edu.seminolestate.reservation.tcp;

import edu.seminolestate.reservation.service.ReservationService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@ConditionalOnProperty(name = "tcp.server.enabled", havingValue = "true", matchIfMissing = true)
public class TcpServer implements CommandLineRunner {

    private final ReservationService service;
    private final int PORT = 8888;
    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    public TcpServer(ReservationService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) {
        Thread serverThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                System.out.println("[TCP Server] Started and listening on port " + PORT);

                while (!serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("[TCP Server] New client connected: " + clientSocket.getRemoteSocketAddress());
                    threadPool.submit(new ClientHandler(clientSocket, service));
                }
            } catch (IOException e) {
                System.err.println("[TCP Server] Exception encountered: " + e.getMessage());
            }
        });
        
        serverThread.setDaemon(true);
        serverThread.start();
    }
}