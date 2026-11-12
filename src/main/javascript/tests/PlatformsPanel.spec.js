import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { enableAutoUnmount } from "@vue/test-utils";
import PlatformsPanel from "../src/components/admin/PlatformsPanel.vue";
import PlatformForm from "../src/components/admin/PlatformForm.vue";
import { createPlatform, deletePlatform, listPlatforms, updatePlatform } from "../src/api/admin";
import { apiError, buttonIn, mountAdmin, page, samplePlatform, settle, textField } from "./admin-helpers";

vi.mock("../src/api/admin", async (original) => ({
  ...(await original()),
  listPlatforms: vi.fn(),
  createPlatform: vi.fn(),
  updatePlatform: vi.fn(),
  deletePlatform: vi.fn()
}));

enableAutoUnmount(afterEach);

const second = { ...samplePlatform, keyId: 4, clientId: "other", deploymentId: null, oAuth2TokenUrl: null, oAuth2TokenAud: "https://aud" };

async function render(props) {
  return mountAdmin(PlatformsPanel, { props });
}

function rowButton(wrapper, label) {
  return wrapper.find(`button[aria-label^="${label}"]`);
}

async function openDelete(wrapper) {
  await rowButton(wrapper, "Delete").trigger("click");
  await settle();
  return document.querySelector("[role=alertdialog]");
}

async function submitForm(wrapper, values = {}) {
  const form = wrapper.findComponent(PlatformForm);
  for (const [label, value] of Object.entries(values)) {
    await textField(form, label).setValue(value);
  }
  await form.find("form").trigger("submit");
  await settle();
}

const newValues = {
  Issuer: "https://lms.example.com",
  "Client ID": "client-1",
  "Deployment ID": "dep-1",
  "OIDC authorization endpoint": "https://lms.example.com/auth",
  "Public keys (JWKS) URL": "https://lms.example.com/jwks"
};

describe("PlatformsPanel", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("lists the platforms with their endpoints", async () => {
    vi.mocked(listPlatforms).mockResolvedValue(page([samplePlatform, second]));
    const { wrapper } = await render();

    expect(listPlatforms).toHaveBeenCalledWith("Ym9zczpzM2NyZXQ=", { page: 0, size: 10 });
    expect(wrapper.find("h2").text()).toBe("Platforms");
    expect(wrapper.findAll("th[scope=col]").map((th) => th.text())).toEqual(["Issuer", "Client ID", "Deployment ID", "Endpoints", "Actions"]);
    expect(wrapper.findAll("th[scope=row]")).toHaveLength(2);
    const text = wrapper.text();
    expect(text).toContain("https://canvas.instructure.com");
    expect(text).toContain("Key ID 3");
    expect(text).toContain("284:abc");
    expect(text).toContain("https://sso.canvaslms.com/api/lti/security/jwks");
    expect(text).toContain("Token: Not set");
    expect(text).toContain("Token audience: https://aud");
    expect(text).toContain("Showing 1 to 2 of 2");
    expect(rowButton(wrapper, "Edit").attributes("aria-label")).toBe("Edit https://canvas.instructure.com, client ID 202570000000000231, deployment ID 284:abc");
    expect(wrapper.find(`button[aria-label$="deployment ID not set"]`).exists()).toBe(true);
    expect(wrapper.find(".v-pagination").exists()).toBe(false);
  });

  it("says when there are no platforms", async () => {
    vi.mocked(listPlatforms).mockResolvedValue(page([]));
    const { wrapper } = await render();

    expect(wrapper.text()).toContain("No platforms yet. Add one to accept launches from an LMS.");
    expect(wrapper.find("table").exists()).toBe(false);
  });

  it("shows a loading bar while it loads", async () => {
    let finish;
    vi.mocked(listPlatforms).mockReturnValue(new Promise((resolve) => { finish = resolve; }));
    const { wrapper } = await render();

    expect(wrapper.find(".v-progress-linear").exists()).toBe(true);
    expect(wrapper.text()).not.toContain("No platforms yet");

    finish(page([samplePlatform]));
    await settle();
    expect(wrapper.find(".v-progress-linear").exists()).toBe(false);
  });

  it("shows a load error", async () => {
    vi.mocked(listPlatforms).mockRejectedValue(apiError(500, "Server Error"));
    const { wrapper } = await render();

    expect(wrapper.find(".v-alert").text()).toContain("Couldn't load the platforms: Server Error");
    expect(wrapper.text()).not.toContain("No platforms yet");
  });

  it("signs out when the login stops working", async () => {
    vi.mocked(listPlatforms).mockRejectedValue(apiError(401));
    const { wrapper, admin } = await render();

    expect(admin.signedIn).toBe(false);
    expect(wrapper.text()).not.toContain("Couldn't load");
  });

  it("pages through the platforms", async () => {
    vi.mocked(listPlatforms).mockResolvedValue(page([samplePlatform], 3));
    const { wrapper } = await render({ size: 1 });

    expect(wrapper.find(".v-pagination").exists()).toBe(true);
    await wrapper.find('button[aria-label="Next page"]').trigger("click");
    await settle();

    expect(listPlatforms).toHaveBeenLastCalledWith("Ym9zczpzM2NyZXQ=", { page: 1, size: 1 });
    expect(wrapper.text()).toContain("Showing 2 to 2 of 3");
  });

  it("adds a platform", async () => {
    vi.mocked(listPlatforms).mockResolvedValue(page([]));
    vi.mocked(createPlatform).mockResolvedValue({ keyId: 9 });
    const { wrapper } = await render();

    await wrapper.findAll("button").find((b) => b.text() === "Add platform").trigger("click");
    await settle();
    expect(wrapper.findComponent(PlatformForm).props("platform")).toBeNull();

    vi.mocked(listPlatforms).mockResolvedValue(page([samplePlatform]));
    await submitForm(wrapper, newValues);

    expect(createPlatform).toHaveBeenCalledWith("Ym9zczpzM2NyZXQ=", expect.objectContaining({ iss: "https://lms.example.com", clientId: "client-1" }));
    expect(wrapper.findComponent(PlatformForm).exists()).toBe(false);
    expect(wrapper.text()).toContain("Added the platform.");
    expect(wrapper.text()).toContain("https://canvas.instructure.com");

    await wrapper.find(".v-alert .v-alert__close button").trigger("click");
    expect(wrapper.text()).not.toContain("Added the platform.");
  });

  it("edits a platform", async () => {
    vi.mocked(listPlatforms).mockResolvedValue(page([samplePlatform]));
    vi.mocked(updatePlatform).mockResolvedValue(samplePlatform);
    const { wrapper } = await render();

    await rowButton(wrapper, "Edit").trigger("click");
    await settle();
    expect(wrapper.findComponent(PlatformForm).props("platform")).toEqual(samplePlatform);

    await submitForm(wrapper, { "Client ID": "changed" });

    expect(updatePlatform).toHaveBeenCalledWith("Ym9zczpzM2NyZXQ=", 3, expect.objectContaining({ clientId: "changed" }));
    expect(wrapper.text()).toContain("Saved the platform.");
  });

  it("explains a duplicate or failed save and keeps the dialog open", async () => {
    vi.mocked(listPlatforms).mockResolvedValue(page([]));
    vi.mocked(createPlatform).mockRejectedValueOnce(apiError(409, "Unable to create."));
    vi.mocked(createPlatform).mockRejectedValueOnce(apiError(500, "Server Error"));
    const { wrapper } = await render();

    await wrapper.findAll("button").find((b) => b.text() === "Add platform").trigger("click");
    await settle();
    await submitForm(wrapper, newValues);
    expect(wrapper.findComponent(PlatformForm).props("error")).toBe("A platform with this issuer, client ID and deployment ID already exists.");

    await submitForm(wrapper);
    expect(wrapper.findComponent(PlatformForm).props("error")).toBe("Couldn't save the platform: Server Error");
    expect(wrapper.findComponent(PlatformForm).props("saving")).toBe(false);

    await wrapper.findComponent(PlatformForm).vm.$emit("cancel");
    await settle();
    expect(wrapper.findComponent(PlatformForm).exists()).toBe(false);
  });

  it("signs out when a save is no longer authorized", async () => {
    vi.mocked(listPlatforms).mockResolvedValue(page([samplePlatform]));
    vi.mocked(updatePlatform).mockRejectedValue(apiError(401));
    const { wrapper, admin } = await render();

    await rowButton(wrapper, "Edit").trigger("click");
    await settle();
    await submitForm(wrapper);

    expect(admin.signedIn).toBe(false);
    expect(wrapper.findComponent(PlatformForm).props("error")).toBeNull();
  });

  it("confirms before deleting", async () => {
    vi.mocked(listPlatforms).mockResolvedValue(page([samplePlatform]));
    const { wrapper } = await render();

    const dialog = await openDelete(wrapper);
    expect(dialog.querySelector("h2").textContent).toBe("Delete this platform?");
    expect(dialog.textContent).toContain("stop accepting launches from https://canvas.instructure.com, client ID 202570000000000231, deployment ID 284:abc");
    expect(dialog.textContent).toContain("You can't undo this.");

    buttonIn(dialog, "Cancel").click();
    await settle();
    expect(deletePlatform).not.toHaveBeenCalled();
  });

  it("deletes a platform and steps back from an emptied page", async () => {
    vi.mocked(listPlatforms).mockResolvedValue(page([samplePlatform], 2));
    vi.mocked(deletePlatform).mockResolvedValue(null);
    const { wrapper } = await render({ size: 1 });
    await wrapper.find('button[aria-label="Next page"]').trigger("click");
    await settle();

    const dialog = await openDelete(wrapper);
    vi.mocked(listPlatforms).mockReset();
    vi.mocked(listPlatforms).mockResolvedValueOnce(page([])).mockResolvedValueOnce(page([second], 1));
    buttonIn(dialog, "Delete platform").click();
    await settle();

    expect(deletePlatform).toHaveBeenCalledWith("Ym9zczpzM2NyZXQ=", 3);
    expect(listPlatforms.mock.calls.map((call) => call[1].page)).toEqual([1, 0]);
    expect(wrapper.text()).toContain("Deleted the platform.");
    expect(wrapper.text()).toContain("Showing 1 to 1 of 1");
  });

  it("explains why a platform can't be deleted", async () => {
    vi.mocked(listPlatforms).mockResolvedValue(page([samplePlatform]));
    vi.mocked(deletePlatform)
      .mockRejectedValueOnce(apiError(409, "Unable to delete. Platform deployment 3 is still used by 2 courses."))
      .mockRejectedValueOnce(apiError(500, "Server Error"));
    const { wrapper } = await render();

    const dialog = await openDelete(wrapper);
    buttonIn(dialog, "Delete platform").click();
    await settle();
    expect(dialog.textContent).toContain("Unable to delete. Platform deployment 3 is still used by 2 courses.");

    buttonIn(dialog, "Delete platform").click();
    await settle();
    expect(dialog.textContent).toContain("Couldn't delete the platform: Server Error");
    expect(wrapper.text()).not.toContain("Deleted the platform.");
  });

  it("signs out when a delete is no longer authorized", async () => {
    vi.mocked(listPlatforms).mockResolvedValue(page([samplePlatform]));
    vi.mocked(deletePlatform).mockRejectedValue(apiError(401));
    const { wrapper, admin } = await render();

    const dialog = await openDelete(wrapper);
    buttonIn(dialog, "Delete platform").click();
    await settle();

    expect(admin.signedIn).toBe(false);
    expect(dialog.textContent).not.toContain("Couldn't delete");
  });

  it("closes either dialog from outside, such as with Escape", async () => {
    vi.mocked(listPlatforms).mockResolvedValue(page([samplePlatform]));
    const { wrapper } = await render();

    await rowButton(wrapper, "Edit").trigger("click");
    await settle();
    const [editDialog, deleteDialog] = wrapper.findAllComponents({ name: "VDialog" });
    editDialog.vm.$emit("update:modelValue", false);
    await settle();
    expect(wrapper.findComponent(PlatformForm).exists()).toBe(false);

    await openDelete(wrapper);
    deleteDialog.vm.$emit("update:modelValue", false);
    await settle();
    expect(deleteDialog.props("modelValue")).toBe(false);
  });
});
