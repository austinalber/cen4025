# Club Reservation System

A local tee-time reservation application with a browser-based management page and a REST API. The application uses the JSON file at `data/teetimes.json` by default.

## Requirements

- Visual Studio Code
- Java Development Kit (JDK) 21 or later
- Apache Maven 3.6 or later
- A web browser

You can check that Java and Maven are available from a terminal:

```powershell
java -version
mvn -version
```

If either command is not recognized, install the missing tool and make sure it is added to your Windows `PATH`. In VS Code, the Extension Pack for Java is helpful for editing and running Java projects.

## Open and run the project in VS Code

1. Open Visual Studio Code.
2. Select **File > Open Folder...** and choose the project folder.
3. Open **Terminal > New Terminal**. Confirm the terminal is at the project root—the folder containing `pom.xml`.
4. Start the application:

   ```powershell
   mvn spring-boot:run
   ```

   The first start may take a little longer while Maven downloads project dependencies. Wait for Spring Boot to report that Tomcat has started on port `8080`.

5. Open [http://localhost:8080/](http://localhost:8080/) in your browser.

The page displays tee times for today and the following six days. Use the filters and search to find times, and the page controls to add or edit tee times, book or cancel reservations, and delete tee times.

To stop the application, return to the VS Code terminal running Maven and press **Ctrl+C**.

## Data storage

The default configuration is in `src/main/resources/application.properties`:

```properties
server.port=8080
reservation.storage=json
```

JSON data is stored in `data/teetimes.json`. Changes made through the browser page are written to this file. The application loads the file when it starts, so stop and restart the application after making manual edits to the JSON file.

Each tee-time record includes an ID, date (`YYYY-MM-DD`), time, capacity, booked count, and availability. For example:

```json
{
  "id": 1,
  "date": "2026-10-18",
  "timeSlot": "08:00 AM",
  "capacity": 4,
  "bookedCount": 0,
  "available": true
}
```

## API

The browser page uses the REST API at `/api/reservations`. For example, open [http://localhost:8080/api/reservations](http://localhost:8080/api/reservations) to retrieve all tee times. To retrieve a date range, provide both ISO-formatted `from` and `to` query parameters:

```text
http://localhost:8080/api/reservations?from=2026-10-18&to=2026-10-24
```

The browser page requests only its seven-day display window; the API without date parameters returns the full schedule.

## Performance investigation

See [docs/performance-investigation.md](docs/performance-investigation.md) for the measured workload, JFR profile findings, optimization, and before/after results.

## Troubleshooting

- **`mvn` is not recognized:** Install Apache Maven and add its `bin` directory to `PATH`, then open a new VS Code terminal.
- **Java version errors:** Verify `java -version` reports JDK 21 or later. If VS Code uses a different JDK, update its Java configuration and reopen the terminal.
- **Port 8080 is already in use:** Stop the other application using that port, or change `server.port` in `src/main/resources/application.properties` and use the new port in your browser.
- **The browser cannot connect:** Check that the Maven terminal still shows the application running and that you opened `http://localhost:8080/`.
