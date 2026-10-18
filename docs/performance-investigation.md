# Performance Investigation

## Where each step is documented

- Baseline workload and measurements: this report, [Baseline](#baseline).
- Profiler evidence and analysis: this report, [Profile](#profile).
- Optimization and tradeoffs: this report, [Optimization](#optimization).
- Before/after evaluation and verification: this report, [Results](#results) and [Correctness](#correctness).
- Source changes: `src/main/java/edu/seminolestate/reservation/`.
- Regression tests: `src/test/java/edu/seminolestate/reservation/`.

## Baseline

### Workload and conditions

- Operation: sequential `GET /api/reservations?from=2026-10-18&to=2026-10-24` requests.
- Storage mode: JSON, with a separate copy of the checked-in dataset so the benchmark could not modify user data.
- Dataset: 13,505 tee times, 2,001,142 bytes, 365 distinct dates from 2026-10-18 through 2027-10-17. The requested seven-day range returned 259 records.
- Runtime: Windows 11 x64, Oracle JDK 24.0.2, Maven 3.9.16, Spring Boot 3.5.6; server on port 18080. Application startup was excluded from request timings.
- Each version received 430 warmup requests followed by three measured batches of 8,000 sequential requests. One .NET `HttpClient` was reused; each request body was read, and an elapsed-time stopwatch captured client-observed latency.
- JFR `profile` recording was enabled during the measured batches. The same machine and endpoint were used for both versions.

### Baseline results

| Run | Elapsed | Throughput | Mean latency | p50 | p95 |
|---|---:|---:|---:|---:|---:|
| 1 | 7.65 s | 1,045.13 req/s | 0.946 ms | 0.859 ms | 1.560 ms |
| 2 | 5.82 s | 1,373.71 req/s | 0.721 ms | 0.650 ms | 1.186 ms |
| 3 | 5.46 s | 1,465.63 req/s | 0.676 ms | 0.626 ms | 1.036 ms |

There was substantial run-to-run variability, consistent with JVM warmup and local scheduling/GC noise. The median of the three runs is used for the comparison, rather than relying on the fastest result.

## Profile

JFR sampling of the repeated baseline workload recorded 293 `jdk.ExecutionSample` events. Its hot-method view showed:

- `LocalDate.isBefore`: 109 samples (37.20%).
- `ReferencePipeline$2$1.accept`: 35 samples (11.95%).
- The allocation-by-site view attributed 39.58% of sampled allocation pressure to `Arrays.copyOf(Object[], int)`.

These are observed profile results. Source inspection explains the likely causes: the range path called `findAll()`, which copied the complete in-memory `ArrayList`, then created a stream to check each tee time's date. The profile alone does not prove which source expression caused each allocation, but the copy and stream are present in that request path.

The recording was collected with the JDK's built-in Java Flight Recorder. For a repeat, start a recording after the application is warm and run the workload while it is active:

```powershell
jcmd <pid> JFR.start name=date-range settings=profile
# Run the warmup and measured HTTP requests.
jcmd <pid> JFR.stop name=date-range filename=date-range.jfr
jfr view hot-methods date-range.jfr
jfr view allocation-by-site date-range.jfr
```

## Optimization

Added a date-range lookup to `ReservationRepository` and routed `ReservationService.getTeeTimes` directly through it:

- JSON storage filters the in-memory list in a loop and returns only matching entries. This avoids copying the entire schedule and avoids stream-pipeline allocation while preserving the original record order and inclusive range behavior.
- Database storage applies the inclusive date predicates in SQL and orders by ID.
- Unfiltered `findAll()` and the API response shape remain unchanged.

The post-change JFR hot-method view reports `JsonReservationRepository.findByDateRange` as the leading sampled method (124 samples, 42.91%). The JSON implementation still scans the schedule, so the profile shows that full-list traversal remains the main CPU opportunity. An additional date index would require extra memory and synchronized index maintenance. A single 8,000-request index experiment measured 1,189 req/s versus 1,258 req/s in the preceding direct-loop run; given that result and the observed run-to-run variation, the index was not retained.

The allocation-by-site share for `Arrays.copyOf(Object[], int)` fell from 39.58% to 6.65%. These are shares of sampled allocation pressure, not absolute allocated bytes.

## Results

| Metric (median of three runs) | Baseline | Optimized | Change |
|---|---:|---:|---:|
| Elapsed for 8,000 requests | 5.82 s | 5.25 s | 9.8% lower |
| Throughput | 1,373.71 req/s | 1,524.79 req/s | 11.0% higher |
| Mean latency | 0.721 ms | 0.650 ms | 9.8% lower |
| p50 latency | 0.650 ms | 0.601 ms | 7.5% lower |
| p95 latency | 1.186 ms | 1.032 ms | 13.0% lower |

| Optimized run | Elapsed | Throughput | Mean latency | p50 | p95 |
|---|---:|---:|---:|---:|---:|
| 1 | 7.65 s | 1,045.91 req/s | 0.945 ms | 0.885 ms | 1.491 ms |
| 2 | 5.25 s | 1,524.79 req/s | 0.650 ms | 0.601 ms | 1.032 ms |
| 3 | 4.96 s | 1,613.74 req/s | 0.613 ms | 0.571 ms | 0.923 ms |

The comparison is indicative rather than a hardware-independent guarantee: timings varied notably between batches, although both versions used the same warmup and three-batch procedure. The final implementation reduces the defensive-copy allocation and improves the median request measurements. It does not eliminate the JSON repository's O(n) scan. The returned matching list and JSON serialization still allocate; avoiding those would require a larger behavioral or representation change.

JFR recordings and generated benchmark data were temporary session-local files, excluded from the repository. The profile findings and measurements needed to understand this investigation are recorded above.

## Correctness

- Added REST coverage for inclusive ranges, empty ranges, preserving creation order across different dates, and range-index consistency after update and delete.
- Added database repository coverage for inclusive range queries and ID order.
- `mvn verify`: passed; 10 tests, 0 failures, 0 errors.
- The benchmark exercised the optimized HTTP endpoint and confirmed 259 returned records per request.
