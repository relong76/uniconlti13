import { describe, expect, it } from "vitest";
import { formatDateTime, fromLocalInput, toLocalInput } from "../src/utils/dates";

describe("dates", () => {
  it("formats platform dates", () => {
    const iso = "2026-11-01T09:05:00.000Z";

    expect(formatDateTime(iso)).toBe(new Date(iso).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" }));
    expect(formatDateTime(null)).toBe("");
    expect(formatDateTime("someday")).toBe("someday");
  });

  it("converts to and from datetime-local values", () => {
    const local = "2026-03-04T05:06";
    const iso = fromLocalInput(local);

    expect(iso).toBe(new Date(local).toISOString());
    expect(toLocalInput(iso)).toBe(local);
    expect(toLocalInput("")).toBe("");
    expect(toLocalInput("nope")).toBe("");
    expect(fromLocalInput("")).toBeNull();
  });
});
