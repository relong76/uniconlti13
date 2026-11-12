import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { listPlatforms } from "../src/api/admin";
import { useAdminStore } from "../src/stores/admin";
import { apiError, page } from "./admin-helpers";

vi.mock("../src/api/admin", async (original) => ({ ...(await original()), listPlatforms: vi.fn() }));

describe("admin store", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.mocked(listPlatforms).mockReset();
  });

  it("signs in by checking the login against the platforms list", async () => {
    vi.mocked(listPlatforms).mockResolvedValue(page([]));
    const admin = useAdminStore();

    await expect(admin.signIn("admin", "x")).resolves.toBe(true);

    expect(listPlatforms).toHaveBeenCalledWith("YWRtaW46eA==", { size: 1 });
    expect(admin.signedIn).toBe(true);
    expect(admin.username).toBe("admin");
    expect(admin.signingIn).toBe(false);
    expect(admin.error).toBeNull();
    expect(JSON.stringify(admin.$state)).not.toContain("\"x\"");
  });

  it("explains a wrong login", async () => {
    vi.mocked(listPlatforms).mockRejectedValue(apiError(401));
    const admin = useAdminStore();

    await expect(admin.signIn("admin", "nope")).resolves.toBe(false);

    expect(admin.signedIn).toBe(false);
    expect(admin.credentials).toBeNull();
    expect(admin.error).toBe("That username and password didn't work. Check them and try again.");
  });

  it("reports other sign in failures", async () => {
    vi.mocked(listPlatforms).mockRejectedValue(apiError(500, "Server Error"));
    const admin = useAdminStore();

    await admin.signIn("admin", "x");

    expect(admin.error).toBe("Couldn't sign in: Server Error");
    expect(admin.signingIn).toBe(false);
  });

  it("signs out, with or without a reason", () => {
    const admin = useAdminStore();
    admin.$patch({ credentials: "abc", username: "admin" });

    admin.signOut();
    expect(admin.signedIn).toBe(false);
    expect(admin.username).toBeNull();
    expect(admin.error).toBeNull();

    admin.signOut("Bye");
    expect(admin.error).toBe("Bye");
  });

  it("signs out when a request is no longer authorized", () => {
    const admin = useAdminStore();
    admin.$patch({ credentials: "abc", username: "admin" });

    expect(admin.failed(apiError(500))).toBe(false);
    expect(admin.failed(undefined)).toBe(false);
    expect(admin.signedIn).toBe(true);

    expect(admin.failed(apiError(401))).toBe(true);
    expect(admin.signedIn).toBe(false);
    expect(admin.error).toBe("Your admin login stopped working. Sign in again.");
  });

  it("never stores the login in browser storage", async () => {
    vi.mocked(listPlatforms).mockResolvedValue(page([]));
    const local = vi.spyOn(Storage.prototype, "setItem");

    await useAdminStore().signIn("admin", "x");

    expect(local).not.toHaveBeenCalled();
    local.mockRestore();
  });
});
