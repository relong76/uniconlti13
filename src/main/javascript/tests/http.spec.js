import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, apiUrl, request } from "../src/api/http";
import { jsonResponse } from "./helpers";

describe("apiUrl", () => {
  it("resolves API paths one level above the app", () => {
    expect(apiUrl("api/launch", { href: "https://tool.example.com/app/#/launch" })).toBe("https://tool.example.com/api/launch");
    expect(apiUrl("/api/launch", { href: "https://tool.example.com/app/index.html" })).toBe("https://tool.example.com/api/launch");
  });

  it("keeps a context path", () => {
    expect(apiUrl("api/launch", { href: "https://host.example.com/tool/app/" })).toBe("https://host.example.com/tool/api/launch");
  });
});

describe("request", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("sends a Bearer token and parses JSON", async () => {
    const fetch = vi.fn().mockResolvedValue(jsonResponse({ ok: 1 }));
    vi.stubGlobal("fetch", fetch);

    await expect(request("api/launch", { token: "abc" })).resolves.toEqual({ ok: 1 });

    const [, options] = fetch.mock.calls[0];
    expect(options.method).toBe("GET");
    expect(options.headers.Authorization).toBe("Bearer abc");
    expect(options.headers["X-Requested-With"]).toBeUndefined();
    expect(options.body).toBeUndefined();
  });

  it("sends basic credentials as an XHR, and a JSON body", async () => {
    const fetch = vi.fn().mockResolvedValue(jsonResponse(undefined, { status: 204 }));
    vi.stubGlobal("fetch", fetch);

    await expect(request("config/", { method: "POST", basic: "YWRtaW46eA==", body: { iss: "x" } })).resolves.toBeNull();

    const [, options] = fetch.mock.calls[0];
    expect(options.headers.Authorization).toBe("Basic YWRtaW46eA==");
    expect(options.headers["X-Requested-With"]).toBe("XMLHttpRequest");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(options.body).toBe('{"iss":"x"}');
  });

  it("returns plain text responses as text", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse("eyJ.token.sig")));

    await expect(request("api/oauth/trade", { method: "POST", token: "one-time" })).resolves.toBe("eyJ.token.sig");
  });

  it("uses the error message from a JSON body", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse({ message: "Expired" }, { status: 404, statusText: "Not Found" })));

    const error = await request("api/launch").catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(404);
    expect(error.message).toBe("Expired");
    expect(error.body).toEqual({ message: "Expired" });
  });

  it("uses the detail of a Spring ProblemDetail", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(
      { type: "about:blank", title: "Forbidden", status: 403, detail: "This launch isn't for choosing content." },
      { status: 403, statusText: "Forbidden" }
    )));

    await expect(request("api/deeplink/items")).rejects.toThrow("This launch isn't for choosing content.");
  });

  it("uses a plain text error body", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse("Token passed was not valid", { status: 401, statusText: "Unauthorized" })));

    await expect(request("api/oauth/trade")).rejects.toThrow("Token passed was not valid");
  });

  it("falls back to the status text, then the status", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse({ error: "x" }, { status: 500, statusText: "Server Error" })));
    await expect(request("api/launch")).rejects.toThrow("Server Error");

    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(undefined, { status: 502, statusText: "" })));
    await expect(request("api/launch")).rejects.toThrow("Request failed with status 502");
  });
});
