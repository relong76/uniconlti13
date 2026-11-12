import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { enableAutoUnmount } from "@vue/test-utils";
import AdminView from "../src/views/AdminView.vue";
import AdminSignIn from "../src/components/admin/AdminSignIn.vue";
import CourseCopiesPanel from "../src/components/admin/CourseCopiesPanel.vue";
import PlatformsPanel from "../src/components/admin/PlatformsPanel.vue";
import { listCourseCopies, listPlatforms } from "../src/api/admin";
import { mountAdmin, page, samplePlatform, settle } from "./admin-helpers";

vi.mock("../src/api/admin", async (original) => ({
  ...(await original()),
  listPlatforms: vi.fn(),
  listCourseCopies: vi.fn()
}));

enableAutoUnmount(afterEach);

describe("AdminView", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(listPlatforms).mockResolvedValue(page([samplePlatform]));
    vi.mocked(listCourseCopies).mockResolvedValue(page([]));
  });

  it("asks an admin to sign in first", async () => {
    const { wrapper } = await mountAdmin(AdminView, { signedIn: false });

    expect(wrapper.find("h1").text()).toBe("Admin");
    expect(wrapper.findComponent(AdminSignIn).exists()).toBe(true);
    expect(wrapper.find(".v-tabs").exists()).toBe(false);
    expect(wrapper.text()).not.toContain("Sign out");
    expect(listPlatforms).not.toHaveBeenCalled();
  });

  it("shows the platforms tab once signed in, and course copies on request", async () => {
    const { wrapper } = await mountAdmin(AdminView);

    expect(wrapper.text()).toContain("Signed in as boss");
    expect(wrapper.findAll(".v-tab").map((t) => t.text())).toEqual(["Platforms", "Course copies"]);
    expect(wrapper.findComponent(PlatformsPanel).exists()).toBe(true);
    expect(listCourseCopies).not.toHaveBeenCalled();

    await wrapper.findAll(".v-tab").find((t) => t.text() === "Course copies").trigger("click");
    await settle();

    expect(wrapper.findComponent(CourseCopiesPanel).exists()).toBe(true);
    expect(listCourseCopies).toHaveBeenCalled();
  });

  it("signs out", async () => {
    const { wrapper, admin } = await mountAdmin(AdminView);

    await wrapper.findAll("button").find((b) => b.text() === "Sign out").trigger("click");
    await settle();

    expect(admin.signedIn).toBe(false);
    expect(admin.error).toBeNull();
    expect(wrapper.findComponent(AdminSignIn).exists()).toBe(true);
  });

  it("follows the window when it changes on its own, such as by swiping", async () => {
    const { wrapper } = await mountAdmin(AdminView);

    wrapper.findComponent({ name: "VWindow" }).vm.$emit("update:modelValue", "copies");
    await settle();

    expect(wrapper.findComponent({ name: "VTabs" }).props("modelValue")).toBe("copies");
  });
});
