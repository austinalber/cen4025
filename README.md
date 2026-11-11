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

## VirtualBox/Vagrant Python API assignment

The root-level `app.py` is a separate, dependency-free Python implementation for the virtualization assignment. It does not replace or change the Java/Spring Boot application. It exposes:

- `GET /api/reservations` to list all tee times; optional `from` and `to` parameters filter by inclusive ISO dates.
- `GET /api/reservations/{id}` to retrieve one tee time.
- `POST /api/reservations/{id}/book` and `POST /api/reservations/{id}/cancel` to change its booking count.

The API persists changes in `data/reservations.json`. The Vagrant VM uses the pinned `ubuntu/jammy64` box (`20241002.0.0`), 2 virtual CPUs, 2 GB of memory, and the box's default virtual disk. Vagrant forwards host port `18080` to guest port `8080`, avoiding a conflict with the Java app's usual host port. Inside the guest, the API binds to `0.0.0.0:8080`; from Windows, use `http://localhost:18080/api/reservations`.

### Host prerequisites and startup

Install VirtualBox and Vagrant on the Windows host. Ensure the host has enough free memory for the guest and that host port `18080` is available. From a PowerShell terminal in the project root:

```powershell
vagrant validate
vagrant up --provider=virtualbox
```

The first `vagrant up` downloads the Ubuntu box, creates the VM, installs Python 3, configures the API service, and starts it. Confirm access from the host:

```powershell
Invoke-RestMethod http://localhost:18080/api/reservations
Invoke-RestMethod -Method Post http://localhost:18080/api/reservations/1/book
Invoke-RestMethod -Method Post http://localhost:18080/api/reservations/1/cancel
```

The VM allows up to 15 minutes for a guest boot to respond because the first boot on this host exceeded Vagrant's default wait. If Vagrant reports a boot timeout, let the guest finish booting and then run `vagrant reload --provision`; this recovery completed provisioning during verification. The project is mounted at `/vagrant`; provisioning verifies that the application user can read `app.py` and write the JSON data file before enabling the service. Guest boot/reload times were inconsistent on this Windows/VirtualBox host. The VirtualBox log reported `VT-x is not available` and a fallback to the Windows hypervisor (NEM); this host configuration caused later clean boots to exceed even the 15-minute timeout. For reproducible startup, verify the host's hardware virtualization and VirtualBox acceleration setup. Disabling Hyper-V or Windows virtualization-based security can affect WSL2/Docker and should not be done without checking those dependencies.

The app and `data/reservations.json` are in the Vagrant synced folder. The systemd service starts at guest boot. Inspect it with `vagrant ssh -c "systemctl status reservation-api"` or inspect logs with `vagrant ssh -c "journalctl -u reservation-api --no-pager"`. Run the app tests with:

```powershell
vagrant ssh -c "cd /vagrant && python3 -m unittest discover -s tests -v"
```

To stop the guest without deleting it, run `vagrant halt`. To reproduce the environment from a clean VM, `vagrant destroy` removes the VM (but not the project files or reservation JSON); then `vagrant up --provider=virtualbox` creates and provisions a new guest. Verify that the host can again retrieve, book, and cancel a reservation. The first boot requires internet access to download the Vagrant box and Ubuntu packages.

### Configuration investigations

1. **Guest bind address:** The working service uses `APP_BIND_ADDRESS=0.0.0.0`. Change it temporarily to `127.0.0.1` in `/etc/default/reservation-api`, restart the service, and observe that the host's forwarded request to `localhost:18080` no longer reaches the API. Restore `0.0.0.0`, restart the service, and verify host access. The loopback-only address accepts guest-local connections but not connections arriving through the VM's forwarded interface.
2. **Host port forwarding:** The normal mapping is host `18080` to guest `8080`. Change the Vagrant host port to `18081`, run `vagrant reload`, and observe that the API is reachable at `http://localhost:18081/api/reservations` while the old URL is no longer mapped. Restore `18080` and reload. This illustrates that the host port is a separate access setting from the API's guest listening port.

Record the actual command output and observed results in `docs/Environment-Overview.docx`; do not present expected behavior as measured evidence until each experiment has been run.

## Load testing

The `load-tests` directory contains a k6 script and a PowerShell runner for repeatable, read-only tests of the seven-day reservation query. The script checks the fixture before the test and expects 259 records for `2026-10-18` through `2026-10-24`; it does not mutate reservation data.

Install k6 using the instructions at [grafana.com/docs/k6](https://grafana.com/docs/k6/latest/set-up/install-k6/). In one PowerShell terminal, start the application from the repository root using the checked-in schedule:

```powershell
$env:SERVER_PORT = "18080"
$env:RESERVATION_STORAGE = "json"
$env:RESERVATION_STORAGE_FILE = "data/teetimes.json"
mvn spring-boot:run
```

In another terminal, run the workload matrix:

```powershell
.\load-tests\run-matrix.ps1 -Label improved
```

The default matrix uses 1, 5, 10, and 20 virtual users, three 20-second measurements at each level, and a 20-second one-user warmup. It writes k6 summary JSON and console output, plus sampled application CPU and working-set observations, to `load-tests/results`. Use the same host, JDK, fixture, server settings, and runner options for every version. k6 and the application share this local machine, so CPU and throughput results describe this test environment rather than an independent production load generator.

After the results are present, regenerate the consolidated Word report with:

```powershell
.\load-tests\build-report.ps1
```

### Reproducing the pre-optimization baseline

The repository's initial commit already contains the original date-range optimization. The baseline patch in `load-tests/baseline-date-range.patch` reconstructs the earlier service path documented in the original performance investigation: copy all records with `findAll()`, then filter them with a stream. It changes only that lookup path. Apply it in a separate worktree so the current source and data remain untouched:

```powershell
$baseline = Join-Path $env:TEMP "club-reservation-baseline"
git worktree add --detach $baseline e0fb7d9
git -C $baseline apply "$PWD\load-tests\baseline-date-range.patch"
```

For three-way comparisons, the unpatched `e0fb7d9` worktree is the existing direct-loop implementation before the concurrency change, and the current checkout contains the concurrent-read improvement. Start each version on port 18080 in turn and run `run-matrix.ps1` from the main worktree with the labels `baseline`, `optimized`, and `improved`. The generated report is `load-tests/results/Club-Reservation-Load-Test-Report.docx`; raw summaries and per-run metrics are stored next to it. Keep the result sets and compare repeated-run medians. The baseline is reconstructed by the patch, not a retained pre-optimization source revision. Do not use historical measurements as substitutes for fresh runs.

### Investigation result

The existing direct-loop implementation held the JSON repository monitor while scanning the full schedule. In the measured 20-VU workload, its median throughput was 11,132.54 requests/second and p95 latency was 3.510 ms. The current implementation uses `CopyOnWriteArrayList` so range reads iterate a stable snapshot without holding that monitor; its corresponding results were 21,095.91 requests/second (+89.5%) and 1.719 ms p95 (-51.0%). No measured requests failed. This read-only test does not measure the extra copy-on-write cost on create, update, and delete operations.


## Troubleshooting

- **`mvn` is not recognized:** Install Apache Maven and add its `bin` directory to `PATH`, then open a new VS Code terminal.
- **Java version errors:** Verify `java -version` reports JDK 21 or later. If VS Code uses a different JDK, update its Java configuration and reopen the terminal.
- **Port 8080 is already in use:** Stop the other application using that port, or change `server.port` in `src/main/resources/application.properties` and use the new port in your browser.
- **The browser cannot connect:** Check that the Maven terminal still shows the application running and that you opened `http://localhost:8080/`.
