import { afterEach, describe, expect, it } from "vitest";
import { enableAutoUnmount } from "@vue/test-utils";
import PlatformForm from "../src/components/admin/PlatformForm.vue";
import { mountWithApp } from "./helpers";
import { samplePlatform, settle, textField } from "./admin-helpers";

enableAutoUnmount(afterEach);

async function render(props = {}) {
  const { wrapper } = await mountWithApp(PlatformForm, { route: "/admin", props });
  return { wrapper, form: wrapper.findComponent(PlatformForm) };
}

async function fill(wrapper, values) {
  for (const [label, value] of Object.entries(values)) {
    await textField(wrapper, label).setValue(value);
  }
}

const valid = {
  Issuer: "https://lms.example.com",
  "Client ID": "client-1",
  "Deployment ID": "dep-1",
  "OIDC authorization endpoint": "https://lms.example.com/auth",
  "Public keys (JWKS) URL": "http://lms.example.com/jwks"
};

describe("PlatformForm", () => {
  it("starts empty for a new platform", async () => {
    const { wrapper } = await render();

    expect(wrapper.find("h2").text()).toBe("Add a platform");
    expect(textField(wrapper, "Issuer").element.value).toBe("");
    expect(wrapper.text()).toContain("Add platform");
    expect(wrapper.text()).toContain("as D2L does");
  });

  it("requires the identifiers and endpoints", async () => {
    const { wrapper, form } = await render();

    await wrapper.find("form").trigger("submit");
    await settle();

    const text = wrapper.text();
    expect(text).toContain("Enter the issuer.");
    expect(text).toContain("Enter the client ID.");
    expect(text).toContain("Enter the deployment ID.");
    expect(text).toContain("Enter the OIDC authorization endpoint.");
    expect(text).toContain("Enter the public keys (JWKS) URL.");
    expect(form.emitted("save")).toBeUndefined();
  });

  it("checks the URLs", async () => {
    const { wrapper, form } = await render();

    await fill(wrapper, { ...valid, Issuer: "canvas", "OIDC authorization endpoint": "ftp://lms/auth", "OAuth 2 token URL (optional)": "not a url" });
    await wrapper.find("form").trigger("submit");
    await settle();

    expect(wrapper.findAll(".v-messages__message").filter((m) => m.text() === "Enter a full URL that starts with https://")).toHaveLength(3);
    expect(form.emitted("save")).toBeUndefined();
  });

  it("emits the values when they're valid", async () => {
    const { wrapper, form } = await render();

    await fill(wrapper, { ...valid, "OAuth 2 token URL (optional)": "  ", "OAuth 2 token audience (optional)": "aud" });
    await wrapper.find("form").trigger("submit");
    await settle();

    expect(form.emitted("save")[0][0]).toEqual({
      iss: "https://lms.example.com",
      clientId: "client-1",
      deploymentId: "dep-1",
      oidcEndpoint: "https://lms.example.com/auth",
      jwksEndpoint: "http://lms.example.com/jwks",
      oAuth2TokenUrl: "  ",
      oAuth2TokenAud: "aud"
    });
  });

  it("edits an existing platform", async () => {
    const { wrapper, form } = await render({ platform: samplePlatform });

    expect(wrapper.find("h2").text()).toBe("Edit platform");
    expect(textField(wrapper, "Client ID").element.value).toBe("202570000000000231");
    expect(textField(wrapper, "OAuth 2 token audience (optional)").element.value).toBe("");

    await wrapper.find("form").trigger("submit");
    await settle();

    expect(form.emitted("save")[0][0].iss).toBe("https://canvas.instructure.com");
    expect(wrapper.text()).toContain("Save changes");
  });

  it("shows an error, the saving state, and cancels", async () => {
    const { wrapper, form } = await render({ error: "Already exists", saving: true });

    expect(wrapper.find(".v-alert").text()).toContain("Already exists");
    const cancel = wrapper.findAll("button").find((b) => b.text() === "Cancel");
    expect(cancel.attributes("disabled")).toBeDefined();

    const { wrapper: idle, form: idleForm } = await render();
    await idle.findAll("button").find((b) => b.text() === "Cancel").trigger("click");
    expect(idleForm.emitted("cancel")).toHaveLength(1);
    expect(form.emitted("cancel")).toBeUndefined();
  });
});
