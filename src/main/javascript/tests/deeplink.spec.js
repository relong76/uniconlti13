import { afterEach, describe, expect, it, vi } from "vitest";
import { createResponse, getItems, isSafeReturnUrl, postToPlatform } from "../src/api/deeplink";
import { jsonResponse } from "./helpers";

describe("deeplink api", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    document.body.querySelectorAll("form").forEach((form) => form.remove());
  });

  it("lists the content with the API token", async () => {
    const fetch = vi.fn().mockResolvedValue(jsonResponse([{ toolLinkId: "quiz" }]));
    vi.stubGlobal("fetch", fetch);

    await expect(getItems("api-token")).resolves.toEqual([{ toolLinkId: "quiz" }]);

    const [url, options] = fetch.mock.calls[0];
    expect(url).toMatch(/\/api\/deeplink\/items$/);
    expect(options.method).toBe("GET");
    expect(options.headers.Authorization).toBe("Bearer api-token");
  });

  it("asks for a signed response for the chosen items", async () => {
    const fetch = vi.fn().mockResolvedValue(jsonResponse({ returnUrl: "https://lms/return", jwt: "signed" }));
    vi.stubGlobal("fetch", fetch);

    await expect(createResponse("api-token", ["quiz", "page"])).resolves.toEqual({ returnUrl: "https://lms/return", jwt: "signed" });

    const [url, options] = fetch.mock.calls[0];
    expect(url).toMatch(/\/api\/deeplink\/response$/);
    expect(options.method).toBe("POST");
    expect(options.headers.Authorization).toBe("Bearer api-token");
    expect(JSON.parse(options.body)).toEqual({ selectedIds: ["quiz", "page"] });
  });

  it("posts the JWT to the platform in a hidden form", () => {
    const submit = vi.spyOn(HTMLFormElement.prototype, "submit").mockImplementation(() => {});

    const form = postToPlatform("https://lms.example.com/deep_links", "signed-jwt");

    expect(submit).toHaveBeenCalledTimes(1);
    expect(submit.mock.instances[0]).toBe(form);
    expect(form.parentNode).toBe(document.body);
    expect(form.method).toBe("post");
    expect(form.action).toBe("https://lms.example.com/deep_links");
    expect(form.hidden).toBe(true);
    const input = form.querySelector("input");
    expect(input.type).toBe("hidden");
    expect(input.name).toBe("JWT");
    expect(input.value).toBe("signed-jwt");
  });

  it("only posts to an https return URL, or http on this machine", () => {
    const submit = vi.spyOn(HTMLFormElement.prototype, "submit").mockImplementation(() => {});

    for (const url of ["javascript:alert(1)", "data:text/html,hi", "http://lms.example.com/deep_links", "not a url", "", null]) {
      expect(() => postToPlatform(url, "signed-jwt"), String(url)).toThrow("isn't an https URL");
    }
    expect(submit).not.toHaveBeenCalled();
    expect(document.body.querySelector("form")).toBeNull();

    expect(isSafeReturnUrl("https://lms.example.com/deep_links")).toBe(true);
    expect(isSafeReturnUrl("http://localhost:3000/deep_links")).toBe(true);
    expect(isSafeReturnUrl("http://127.0.0.1/deep_links")).toBe(true);
    expect(isSafeReturnUrl("http://[::1]:8080/deep_links")).toBe(true);
  });
});
