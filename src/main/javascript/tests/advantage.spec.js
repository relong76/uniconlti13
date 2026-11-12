import { afterEach, describe, expect, it, vi } from "vitest";
import {
  ACTIVITY_PROGRESS,
  GRADING_PROGRESS,
  createLineItem,
  deleteLineItem,
  getLineItem,
  getRoster,
  listLineItems,
  postScore,
  updateLineItem
} from "../src/api/advantage";
import { jsonResponse } from "./helpers";

function mockFetch(body, options) {
  const fetch = vi.fn().mockResolvedValue(jsonResponse(body, options));
  vi.stubGlobal("fetch", fetch);
  return fetch;
}

function call(fetch) {
  const [url, init] = fetch.mock.calls[0];
  return { path: new URL(url).pathname, method: init.method, auth: init.headers.Authorization, body: init.body && JSON.parse(init.body) };
}

describe("advantage api", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("lists line items", async () => {
    const fetch = mockFetch([{ key: "5" }]);

    expect(await listLineItems("t")).toEqual([{ key: "5" }]);
    expect(call(fetch)).toEqual({ path: "/api/grades/lineitems", method: "GET", auth: "Bearer t", body: undefined });
  });

  it("creates a line item", async () => {
    const fetch = mockFetch({ key: "9" }, { status: 201 });

    expect(await createLineItem("t", { label: "Quiz" })).toEqual({ key: "9" });
    expect(call(fetch)).toMatchObject({ path: "/api/grades/lineitems", method: "POST", body: { label: "Quiz" } });
  });

  it("encodes the line item key into the path", async () => {
    const fetch = mockFetch({ key: "a/b c" });

    await getLineItem("t", "a/b c");

    expect(call(fetch)).toMatchObject({ path: "/api/grades/lineitems/a%2Fb%20c", method: "GET" });
  });

  it("updates, deletes and scores a line item", async () => {
    let fetch = mockFetch({ key: "5" });
    await updateLineItem("t", "5", { label: "New" });
    expect(call(fetch)).toMatchObject({ path: "/api/grades/lineitems/5", method: "PUT", body: { label: "New" } });

    fetch = mockFetch(undefined, { status: 204 });
    expect(await deleteLineItem("t", "5")).toBeNull();
    expect(call(fetch)).toMatchObject({ path: "/api/grades/lineitems/5", method: "DELETE" });

    fetch = mockFetch({ key: "5", results: [] });
    await postScore("t", "5", { userId: "u" });
    expect(call(fetch)).toMatchObject({ path: "/api/grades/lineitems/5/scores", method: "POST", body: { userId: "u" } });
  });

  it("gets the roster", async () => {
    const fetch = mockFetch([{ userId: "u" }]);

    expect(await getRoster("t")).toEqual([{ userId: "u" }]);
    expect(call(fetch)).toMatchObject({ path: "/api/roster", method: "GET", auth: "Bearer t" });
  });

  it("passes on the error message from the server", async () => {
    mockFetch({ status: 502, message: "The platform didn't complete the request." }, { status: 502 });

    await expect(getRoster("t")).rejects.toMatchObject({ status: 502, message: "The platform didn't complete the request." });
  });

  it("lists the AGS progress values", () => {
    expect(ACTIVITY_PROGRESS).toEqual(["Initialized", "Started", "InProgress", "Submitted", "Completed"]);
    expect(GRADING_PROGRESS).toEqual(["FullyGraded", "Pending", "PendingManual", "Failed", "NotReady"]);
  });
});
