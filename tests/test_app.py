import http.client
import json
import tempfile
import threading
import unittest
from datetime import date
from pathlib import Path

from app import ReservationStore, create_server


class ReservationStoreTests(unittest.TestCase):
    def setUp(self):
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.data_path = Path(self.temporary_directory.name) / "reservations.json"
        self.store = ReservationStore(self.data_path)

    def tearDown(self):
        self.temporary_directory.cleanup()

    def test_missing_data_file_is_initialized_and_persisted(self):
        records = self.store.list_reservations()
        self.assertEqual(3, len(records))
        self.assertTrue(self.data_path.exists())
        self.assertNotIn("available", json.loads(self.data_path.read_text())[0])
        self.assertTrue(records[0]["available"])

    def test_booking_and_cancellation_update_persistent_state(self):
        outcome, booked = self.store.book(1)
        self.assertEqual("booked", outcome)
        self.assertEqual(2, booked["bookedCount"])

        reloaded = ReservationStore(self.data_path)
        self.assertEqual(2, reloaded.get_reservation(1)["bookedCount"])

        outcome, cancelled = reloaded.cancel(1)
        self.assertEqual("cancelled", outcome)
        self.assertEqual(1, cancelled["bookedCount"])

    def test_full_and_missing_reservations_are_reported(self):
        outcome, _ = self.store.book(2)
        self.assertEqual("full", outcome)
        outcome, record = self.store.book(999)
        self.assertEqual("not_found", outcome)
        self.assertIsNone(record)

    def test_cancellation_requires_an_active_booking(self):
        outcome, _ = self.store.cancel(3)
        self.assertEqual("empty", outcome)

    def test_date_range_filters_inclusively(self):
        records = self.store.list_reservations(date(2026, 11, 2), date(2026, 11, 2))
        self.assertEqual([1, 2], [record["id"] for record in records])

    def test_invalid_json_shape_fails_explicitly(self):
        self.data_path.write_text('{"id": 1}', encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "JSON array"):
            ReservationStore(self.data_path)


class ReservationHTTPTests(unittest.TestCase):
    def setUp(self):
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.data_path = Path(self.temporary_directory.name) / "reservations.json"
        self.server = create_server("127.0.0.1", 0, self.data_path)
        self.server_thread = threading.Thread(target=self.server.serve_forever)
        self.server_thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.server_thread.join()
        self.temporary_directory.cleanup()

    def request(self, method, path):
        connection = http.client.HTTPConnection(
            "127.0.0.1", self.server.server_port, timeout=2
        )
        connection.request(method, path)
        response = connection.getresponse()
        body = json.loads(response.read().decode("utf-8"))
        connection.close()
        return response.status, body

    def test_list_book_cancel_and_capacity_conflict(self):
        status, records = self.request("GET", "/api/reservations")
        self.assertEqual(200, status)
        self.assertEqual(3, len(records))

        status, full = self.request("POST", "/api/reservations/2/book")
        self.assertEqual(409, status)
        self.assertEqual("Reservation is full.", full["error"])

        status, booked = self.request("POST", "/api/reservations/1/book")
        self.assertEqual(200, status)
        self.assertEqual(2, booked["bookedCount"])

        status, cancelled = self.request("POST", "/api/reservations/1/cancel")
        self.assertEqual(200, status)
        self.assertEqual(1, cancelled["bookedCount"])

    def test_date_range_requires_both_bounds(self):
        status, error = self.request("GET", "/api/reservations?from=2026-11-02")
        self.assertEqual(400, status)
        self.assertIn("both", error["error"])


if __name__ == "__main__":
    unittest.main()
