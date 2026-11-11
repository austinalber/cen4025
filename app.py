#!/usr/bin/env python3
"""Small, dependency-free reservation API for the Vagrant assignment VM."""

import json
import os
import re
import tempfile
from datetime import date
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import RLock
from urllib.parse import parse_qs, urlparse


PROJECT_DIR = Path(__file__).resolve().parent
DEFAULT_DATA_FILE = PROJECT_DIR / "data" / "reservations.json"
DEFAULT_RESERVATIONS = [
    {
        "id": 1,
        "date": "2026-11-02",
        "timeSlot": "08:00 AM",
        "capacity": 4,
        "bookedCount": 1,
    },
    {
        "id": 2,
        "date": "2026-11-02",
        "timeSlot": "09:00 AM",
        "capacity": 2,
        "bookedCount": 2,
    },
    {
        "id": 3,
        "date": "2026-11-03",
        "timeSlot": "10:00 AM",
        "capacity": 4,
        "bookedCount": 0,
    },
]


class ReservationStore:
    """Thread-safe JSON-backed reservation records."""

    def __init__(self, path):
        self.path = Path(path)
        self.lock = RLock()
        self.path.parent.mkdir(parents=True, exist_ok=True)
        if not self.path.exists():
            self._write_records(DEFAULT_RESERVATIONS)
        self.records = self._read_records()

    def _read_records(self):
        with self.path.open("r", encoding="utf-8") as data_file:
            records = json.load(data_file)
        if not isinstance(records, list):
            raise ValueError("Reservation data must be a JSON array.")

        seen_ids = set()
        for record in records:
            if not isinstance(record, dict):
                raise ValueError("Each reservation must be a JSON object.")
            if type(record.get("id")) is not int or record["id"] <= 0:
                raise ValueError("Each reservation must have a positive integer id.")
            if record["id"] in seen_ids:
                raise ValueError("Reservation ids must be unique.")
            seen_ids.add(record["id"])
            try:
                date.fromisoformat(record["date"])
            except (KeyError, TypeError, ValueError) as error:
                raise ValueError("Each reservation must have an ISO date.") from error
            if not isinstance(record.get("timeSlot"), str) or not record["timeSlot"]:
                raise ValueError("Each reservation must have a timeSlot.")
            if type(record.get("capacity")) is not int or record["capacity"] < 1:
                raise ValueError("Each reservation must have a positive capacity.")
            if type(record.get("bookedCount")) is not int or not (
                0 <= record["bookedCount"] <= record["capacity"]
            ):
                raise ValueError("Each reservation must have a valid bookedCount.")
        return records

    def _write_records(self, records):
        temporary_path = None
        try:
            with tempfile.NamedTemporaryFile(
                mode="w",
                encoding="utf-8",
                dir=self.path.parent,
                prefix=f".{self.path.name}.",
                suffix=".tmp",
                delete=False,
            ) as data_file:
                temporary_path = Path(data_file.name)
                json.dump(records, data_file, indent=2)
                data_file.write("\n")
                data_file.flush()
                os.fsync(data_file.fileno())
            os.replace(temporary_path, self.path)
        finally:
            if temporary_path is not None and temporary_path.exists():
                temporary_path.unlink()

    @staticmethod
    def _public_record(record):
        result = dict(record)
        result["available"] = result["bookedCount"] < result["capacity"]
        return result

    def list_reservations(self, start_date=None, end_date=None):
        with self.lock:
            records = self.records
            if start_date is not None:
                records = [
                    record
                    for record in records
                    if start_date <= date.fromisoformat(record["date"]) <= end_date
                ]
            return [self._public_record(record) for record in records]

    def get_reservation(self, reservation_id):
        with self.lock:
            record = next(
                (item for item in self.records if item["id"] == reservation_id), None
            )
            return self._public_record(record) if record is not None else None

    def book(self, reservation_id):
        with self.lock:
            updated_records = [dict(record) for record in self.records]
            record = next(
                (item for item in updated_records if item["id"] == reservation_id),
                None,
            )
            if record is None:
                return "not_found", None
            if record["bookedCount"] >= record["capacity"]:
                return "full", self._public_record(record)
            record["bookedCount"] += 1
            self._write_records(updated_records)
            self.records = updated_records
            return "booked", self._public_record(record)

    def cancel(self, reservation_id):
        with self.lock:
            updated_records = [dict(record) for record in self.records]
            record = next(
                (item for item in updated_records if item["id"] == reservation_id),
                None,
            )
            if record is None:
                return "not_found", None
            if record["bookedCount"] == 0:
                return "empty", self._public_record(record)
            record["bookedCount"] -= 1
            self._write_records(updated_records)
            self.records = updated_records
            return "cancelled", self._public_record(record)


class ReservationRequestHandler(BaseHTTPRequestHandler):
    server_version = "ReservationAPI/1.0"

    def _send_json(self, status, payload):
        body = json.dumps(payload, indent=2).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_error_json(self, status, message):
        self._send_json(status, {"error": message})

    def do_GET(self):
        request = urlparse(self.path)
        if request.path == "/api/reservations":
            query = parse_qs(request.query, keep_blank_values=True)
            has_from = "from" in query
            has_to = "to" in query
            if has_from != has_to:
                self._send_error_json(
                    400, "Provide both 'from' and 'to' date query parameters."
                )
                return
            if has_from:
                try:
                    start_date = date.fromisoformat(query["from"][0])
                    end_date = date.fromisoformat(query["to"][0])
                except ValueError:
                    self._send_error_json(400, "Dates must use YYYY-MM-DD format.")
                    return
                if start_date > end_date:
                    self._send_error_json(400, "'from' date must not be after 'to'.")
                    return
                self._send_json(
                    200,
                    self.server.store.list_reservations(start_date, end_date),
                )
                return
            self._send_json(200, self.server.store.list_reservations())
            return

        match = re.fullmatch(r"/api/reservations/(\d+)", request.path)
        if match:
            record = self.server.store.get_reservation(int(match.group(1)))
            if record is None:
                self._send_error_json(404, "Reservation not found.")
            else:
                self._send_json(200, record)
            return

        self._send_error_json(404, "Route not found.")

    def do_POST(self):
        request = urlparse(self.path)
        match = re.fullmatch(r"/api/reservations/(\d+)/(book|cancel)", request.path)
        if not match:
            self._send_error_json(404, "Route not found.")
            return

        reservation_id = int(match.group(1))
        action = match.group(2)
        if action == "book":
            outcome, record = self.server.store.book(reservation_id)
            if outcome == "not_found":
                self._send_error_json(404, "Reservation not found.")
            elif outcome == "full":
                self._send_json(409, {"error": "Reservation is full.", "reservation": record})
            else:
                self._send_json(200, record)
            return

        outcome, record = self.server.store.cancel(reservation_id)
        if outcome == "not_found":
            self._send_error_json(404, "Reservation not found.")
        elif outcome == "empty":
            self._send_json(
                409,
                {"error": "Reservation has no active bookings.", "reservation": record},
            )
        else:
            self._send_json(200, record)

    def log_message(self, format_string, *args):
        print(f"{self.address_string()} - {format_string % args}")


class ReservationHTTPServer(ThreadingHTTPServer):
    daemon_threads = True


def create_server(host, port, data_path):
    server = ReservationHTTPServer((host, port), ReservationRequestHandler)
    server.store = ReservationStore(data_path)
    return server


def main():
    host = os.environ.get("APP_BIND_ADDRESS", "0.0.0.0")
    port = int(os.environ.get("APP_PORT", "8080"))
    data_path = os.environ.get("RESERVATION_DATA_FILE", str(DEFAULT_DATA_FILE))
    with create_server(host, port, data_path) as server:
        print(f"Reservation API listening on {host}:{port}")
        print(f"Reservation data file: {Path(data_path).resolve()}")
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            print("\nReservation API stopped.")


if __name__ == "__main__":
    main()
