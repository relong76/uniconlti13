import { afterEach, describe, expect, it, vi } from "vitest";
import {
  createPlatform,
  deletePlatform,
  encodeCredentials,
  listCourseCopies,
  listPlatforms,
  toPage,
  updatePlatform
} from "../src/api/admin";
import { jsonResponse } from "./helpers";

function stubFetch(body, options) {
  const fetch = vi.fn().mockResolvedValue(jsonResponse(body, options));
  vi.stubGlobal("fetch", fetch);
  return fetch;
}

describe("admin api", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("encodes credentials as UTF-8 base64", () => {
    expect(encodeCredentials("admin", "x")).toBe("YWRtaW46eA==");
    expect(atob(encodeCredentials("admin", "pässword"))).toBe(String.fromCharCode(...new TextEncoder().encode("admin:pässword")));
  });

  it("reads both page shapes and an empty answer", () => {
    expect(toPage({ content: [1, 2], totalElements: 12 })).toEqual({ items: [1, 2], total: 12 });
    expect(toPage({ content: [1], page: { totalElements: 7 } })).toEqual({ items: [1], total: 7 });
    expect(toPage({ content: [1, 2] })).toEqual({ items: [1, 2], total: 2 });
    expect(toPage({})).toEqual({ items: [], total: 0 });
    expect(toPage(null)).toEqual({ items: [], total: 0 });
  });

  it("lists platforms with paging and the admin login", async () => {
    const fetch = stubFetch({ content: [{ keyId: 1 }], totalElements: 1 });

    await expect(listPlatforms("abc", { page: 2, size: 5 })).resolves.toEqual({ items: [{ keyId: 1 }], total: 1 });

    const [url, options] = fetch.mock.calls[0];
    expect(url).toMatch(/\/config\/\?page=2&size=5$/);
    expect(options.headers.Authorization).toBe("Basic abc");
    expect(options.headers["X-Requested-With"]).toBe("XMLHttpRequest");
  });

  it("defaults the paging and treats 204 as empty", async () => {
    const fetch = stubFetch(undefined, { status: 204 });

    await expect(listPlatforms("abc")).resolves.toEqual({ items: [], total: 0 });
    expect(fetch.mock.calls[0][0]).toMatch(/\/config\/\?page=0&size=10$/);
  });

  it("creates a platform with trimmed values and nulls for blanks", async () => {
    const fetch = stubFetch({ keyId: 9 }, { status: 201 });

    await expect(createPlatform("abc", { iss: " https://lms ", clientId: "c", deploymentId: "d", oidcEndpoint: "https://lms/a", jwksEndpoint: "https://lms/j", oAuth2TokenUrl: "", keyId: 4 }))
      .resolves.toEqual({ keyId: 9 });

    const [url, options] = fetch.mock.calls[0];
    expect(url).toMatch(/\/config\/$/);
    expect(options.method).toBe("POST");
    expect(JSON.parse(options.body)).toEqual({
      iss: "https://lms",
      clientId: "c",
      deploymentId: "d",
      oidcEndpoint: "https://lms/a",
      jwksEndpoint: "https://lms/j",
      oAuth2TokenUrl: null,
      oAuth2TokenAud: null
    });
  });

  it("updates and deletes a platform by key id", async () => {
    const fetch = stubFetch({ keyId: 3 });

    await updatePlatform("abc", 3, { iss: "https://lms", oAuth2TokenAud: "aud" });
    expect(fetch.mock.calls[0][0]).toMatch(/\/config\/3$/);
    expect(fetch.mock.calls[0][1].method).toBe("PUT");
    expect(JSON.parse(fetch.mock.calls[0][1].body).oAuth2TokenAud).toBe("aud");

    await deletePlatform("abc", 3);
    expect(fetch.mock.calls[1][0]).toMatch(/\/config\/3$/);
    expect(fetch.mock.calls[1][1].method).toBe("DELETE");
    expect(fetch.mock.calls[1][1].body).toBeUndefined();
  });

  it("lists course copies", async () => {
    const fetch = stubFetch({ content: [{ noticeId: "n" }], totalElements: 30 });

    await expect(listCourseCopies("abc", { page: 1, size: 20 })).resolves.toEqual({ items: [{ noticeId: "n" }], total: 30 });
    expect(fetch.mock.calls[0][0]).toMatch(/\/config\/notices\/context-copy\?page=1&size=20$/);

    await listCourseCopies("abc");
    expect(fetch.mock.calls[1][0]).toMatch(/\?page=0&size=20$/);
  });
});
