import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { tokenFromUrl, useSessionStore, withoutToken } from "../src/stores/session";
import * as lti from "../src/api/lti";
import { ApiError } from "../src/api/http";
import { sampleLaunch } from "./helpers";

vi.mock("../src/api/lti", () => ({ tradeToken: vi.fn(), getLaunch: vi.fn() }));

const location = (href) => {
  const url = new URL(href);
  return { href: url.href, search: url.search, hash: url.hash };
};
const history = () => ({ state: null, replaceState: vi.fn() });

describe("token in the URL", () => {
  it("is read from the query or the hash", () => {
    expect(tokenFromUrl(location("https://t/app/?token=abc#/launch"))).toBe("abc");
    expect(tokenFromUrl(location("https://t/app/#/launch?token=def"))).toBe("def");
    expect(tokenFromUrl(location("https://t/app/#/launch"))).toBeNull();
    expect(tokenFromUrl({ search: "", hash: "" })).toBeNull();
  });

  it("is removed from the URL", () => {
    expect(withoutToken(location("https://t/app/?token=abc&x=1#/launch"))).toBe("https://t/app/?x=1#/launch");
  });
});

describe("session store", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.mocked(lti.tradeToken).mockReset();
    vi.mocked(lti.getLaunch).mockReset();
    window.sessionStorage.clear();
  });

  it("trades the one-time token, removes it from the URL and loads the launch", async () => {
    vi.mocked(lti.tradeToken).mockResolvedValue("api-token");
    vi.mocked(lti.getLaunch).mockResolvedValue(sampleLaunch);
    const session = useSessionStore();
    const h = history();

    await session.start(location("https://t/app/?token=one-time#/launch"), h);

    expect(lti.tradeToken).toHaveBeenCalledWith("one-time");
    expect(lti.getLaunch).toHaveBeenCalledWith("api-token");
    expect(h.replaceState).toHaveBeenCalledWith(null, "", "https://t/app/#/launch");
    expect(session.token).toBe("api-token");
    expect(session.launch).toEqual(sampleLaunch);
    expect(session.loading).toBe(false);
    expect(session.error).toBeNull();
    expect(window.sessionStorage.getItem("lti13demo.token")).toBe("api-token");
    expect(session.hasGrades).toBe(true);
    expect(session.hasRoster).toBe(true);
    expect(session.isDeepLinking).toBe(false);
  });

  it("reuses the stored token on reload", async () => {
    window.sessionStorage.setItem("lti13demo.token", "stored-token");
    vi.mocked(lti.getLaunch).mockResolvedValue(sampleLaunch);
    const session = useSessionStore();

    await session.start(location("https://t/app/#/launch"), history());

    expect(lti.tradeToken).not.toHaveBeenCalled();
    expect(lti.getLaunch).toHaveBeenCalledWith("stored-token");
  });

  it("asks for a launch when there's no token", async () => {
    const session = useSessionStore();

    await session.start(location("https://t/app/#/launch"), history());

    expect(session.error).toBe("Launch the tool from your course to start.");
    expect(lti.getLaunch).not.toHaveBeenCalled();
  });

  it("explains an expired launch", async () => {
    vi.mocked(lti.tradeToken).mockResolvedValue("api-token");
    vi.mocked(lti.getLaunch).mockRejectedValue(new ApiError(404, "gone"));
    const session = useSessionStore();

    await session.start(location("https://t/app/?token=x"), history());

    expect(session.error).toBe("This launch has expired. Launch the tool again from your course.");
    expect(session.launch).toBeNull();
  });

  it("explains a rejected token the same way", async () => {
    vi.mocked(lti.tradeToken).mockRejectedValue(new ApiError(401, "no"));
    const session = useSessionStore();

    await session.start(location("https://t/app/?token=x"), history());

    expect(session.error).toBe("This launch has expired. Launch the tool again from your course.");
  });

  it("shows other errors as they are", async () => {
    vi.mocked(lti.tradeToken).mockRejectedValue(new Error("Network down"));
    const session = useSessionStore();

    await session.start(location("https://t/app/?token=x"), history());

    expect(session.error).toBe("Network down");
  });

  it("works without sessionStorage", async () => {
    const getItem = vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => { throw new Error("blocked"); });
    const setItem = vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => { throw new Error("blocked"); });
    vi.mocked(lti.tradeToken).mockResolvedValue("api-token");
    vi.mocked(lti.getLaunch).mockResolvedValue(sampleLaunch);
    const session = useSessionStore();

    await session.start(location("https://t/app/?token=x"), history());
    expect(session.token).toBe("api-token");

    const reloaded = useSessionStore();
    reloaded.token = null;
    await reloaded.start(location("https://t/app/#/launch"), history());
    expect(reloaded.error).toBe("Launch the tool from your course to start.");

    getItem.mockRestore();
    setItem.mockRestore();
  });

  it("ends the session", () => {
    window.sessionStorage.setItem("lti13demo.token", "t");
    const session = useSessionStore();
    session.token = "t";
    session.launch = sampleLaunch;

    session.end();

    expect(session.token).toBeNull();
    expect(session.launch).toBeNull();
    expect(window.sessionStorage.getItem("lti13demo.token")).toBeNull();
  });

  it("recognizes a deep linking launch", () => {
    const session = useSessionStore();
    session.launch = { ...sampleLaunch, messageType: "LtiDeepLinkingRequest", services: {} };

    expect(session.isDeepLinking).toBe(true);
    expect(session.hasGrades).toBe(false);
    expect(session.hasRoster).toBe(false);
  });
});

describe("lti api", async () => {
  const actual = await vi.importActual("../src/api/lti");

  it("calls the trade and launch endpoints", async () => {
    const fetch = vi.fn().mockResolvedValue({ ok: true, status: 200, text: async () => "t" });
    vi.stubGlobal("fetch", fetch);

    await actual.tradeToken("one");
    await actual.getLaunch("api");

    expect(fetch.mock.calls[0][0]).toMatch(/\/api\/oauth\/trade$/);
    expect(fetch.mock.calls[0][1].headers.Authorization).toBe("Bearer one");
    expect(fetch.mock.calls[1][0]).toMatch(/\/api\/launch$/);
    vi.unstubAllGlobals();
  });
});
