import http from "k6/http";
import { check } from "k6";

const baseUrl = __ENV.BASE_URL || "http://localhost:18080";
const fromDate = "2026-10-18";
const toDate = "2026-10-24";
const expectedRecordCount = 259;
const vuCount = Number(__ENV.VUS || 1);

if (!Number.isInteger(vuCount) || vuCount < 1) {
  throw new Error("VUS must be a positive integer.");
}

export const options = {
  vus: vuCount,
  duration: __ENV.DURATION || "20s",
  thresholds: {
    checks: ["rate==1"],
    http_req_failed: ["rate==0"],
  },
};

const endpoint = `${baseUrl}/api/reservations?from=${fromDate}&to=${toDate}`;

export function setup() {
  const response = http.get(endpoint);
  const records = response.json();

  if (response.status !== 200 || !Array.isArray(records) || records.length !== expectedRecordCount) {
    throw new Error(
      `Expected HTTP 200 and ${expectedRecordCount} records; received HTTP ${response.status} and ${Array.isArray(records) ? records.length : "non-array"}.`,
    );
  }
}

export default function () {
  const response = http.get(endpoint);
  check(response, {
    "GET date range returns HTTP 200": (result) => result.status === 200,
  });
}
