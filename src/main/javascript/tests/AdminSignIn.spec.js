import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { enableAutoUnmount } from "@vue/test-utils";
import AdminSignIn from "../src/components/admin/AdminSignIn.vue";
import { listPlatforms } from "../src/api/admin";
import { apiError, mountAdmin, page, settle, textField } from "./admin-helpers";

vi.mock("../src/api/admin", async (original) => ({ ...(await original()), listPlatforms: vi.fn() }));

enableAutoUnmount(afterEach);

async function signIn(wrapper, username, password) {
  await textField(wrapper, "Username").setValue(username);
  await textField(wrapper, "Password").setValue(password);
  await wrapper.find("form").trigger("submit");
  await settle();
}

describe("AdminSignIn", () => {
  beforeEach(() => {
    vi.mocked(listPlatforms).mockReset();
  });

  it("has a labelled username and a password field", async () => {
    const { wrapper } = await mountAdmin(AdminSignIn, { signedIn: false });

    expect(wrapper.find("h2").text()).toBe("Sign in");
    expect(textField(wrapper, "Username").attributes("autocomplete")).toBe("username");
    expect(textField(wrapper, "Password").attributes("type")).toBe("password");
    expect(wrapper.find("label").exists()).toBe(true);
  });

  it("asks for both fields before calling the server", async () => {
    const { wrapper } = await mountAdmin(AdminSignIn, { signedIn: false });

    await signIn(wrapper, "  ", "");

    expect(listPlatforms).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("Enter the username.");
    expect(wrapper.text()).toContain("Enter the password.");
  });

  it("signs in and forgets the typed password", async () => {
    vi.mocked(listPlatforms).mockResolvedValue(page([]));
    const { wrapper, admin } = await mountAdmin(AdminSignIn, { signedIn: false });

    await signIn(wrapper, " admin ", "x");

    expect(listPlatforms).toHaveBeenCalledWith("YWRtaW46eA==", { size: 1 });
    expect(admin.signedIn).toBe(true);
    expect(admin.username).toBe("admin");
    expect(textField(wrapper, "Password").element.value).toBe("");
  });

  it("shows why signing in failed, without the password", async () => {
    vi.mocked(listPlatforms).mockRejectedValue(apiError(401));
    const { wrapper, admin } = await mountAdmin(AdminSignIn, { signedIn: false });

    await signIn(wrapper, "admin", "secret-value");

    expect(admin.signedIn).toBe(false);
    expect(wrapper.find(".v-alert").text()).toContain("That username and password didn't work.");
    expect(wrapper.html()).not.toContain("secret-value");
  });

  it("fills in the last username", async () => {
    const { wrapper } = await mountAdmin(AdminSignIn, { signedIn: false, setup: (admin) => { admin.username = "boss"; } });

    expect(textField(wrapper, "Username").element.value).toBe("boss");
  });
});
