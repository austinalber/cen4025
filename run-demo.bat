@echo off
TITLE Club Reservation System - Concurrency Demo
echo Starting Spring Boot Server and TCP Clients...

:: 1. Start the main Spring Boot app in a new window
start "Club Reservation Server" cmd /k "mvn spring-boot:run"

:: Wait 5 seconds for the server and TCP port 8888 to initialize
timeout /t 5 /nobreak > nul

:: 2. Automatically open two concurrent client windows
start "TCP Client A (User 1)" cmd /k "mvn exec:java -Dexec.mainClass=""edu.seminolestate.reservation.tcp.TcpClient"""
start "TCP Client B (User 2)" cmd /k "mvn exec:java -Dexec.mainClass=""edu.seminolestate.reservation.tcp.TcpClient"""

echo Demo environment launched successfully!
pause