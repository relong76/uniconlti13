import { flushPromises } from "@vue/test-utils";
import { createPinia } from "pinia";
import { ApiError } from "../src/api/http";
import { useAdminStore } from "../src/stores/admin";
import { mountWithApp } from "./helpers";

export const samplePlatform = {
  keyId: 3,
  iss: "https://canvas.instructure.com",
  clientId: "202570000000000231",
  deploymentId: "284:abc",
  oidcEndpoint: "https://sso.canvaslms.com/api/lti/authorize_redirect",
  jwksEndpoint: "https://sso.canvaslms.com/api/lti/security/jwks",
  oAuth2TokenUrl: "https://sso.canvaslms.com/login/oauth2/token",
  oAuth2TokenAud: null,
  createdAt: "2026-11-01T10:00:00Z",
  updatedAt: "2026-11-01T10:00:00Z",
  version: 0
};

export const sampleNotice = {
  noticeId: "notice-1",
  keyId: 3,
  deploymentId: "284:abc",
  noticeTimestamp: "2026-11-12T16:59:00Z",
  contextKey: "ctx-new",
  contextTitle: "Biology 101 (Spring)",
  originContextKeys: "ctx-1, ctx-2",
  createdAt: "2026-11-12T17:00:00Z"
};

export function apiError(status, message = "Failed") {
  return new ApiError(status, message, message);
}

export function page(items, total = items.length) {
  return { items, total };
}

// mounts with a signed-in admin store unless told otherwise
export async function mountAdmin(component, { signedIn = true, props, setup } = {}) {
  const pinia = createPinia();
  const admin = useAdminStore(pinia);

  if (signedIn) {
    admin.credentials = "Ym9zczpzM2NyZXQ=";
    admin.username = "boss";
  }

  setup?.(admin);

  const result = await mountWithApp(component, { pinia, route: "/admin", props });
  await flushPromises();

  return { ...result, admin };
}

export function textField(wrapper, label) {
  const field = wrapper.findAllComponents({ name: "VTextField" }).find((c) => c.props("label") === label);

  if (!field) {
    throw new Error(`No field labelled ${label}`);
  }

  return field.find("input");
}

export function buttonIn(root, text) {
  const button = [...root.querySelectorAll("button")].find((b) => b.textContent.trim() === text);

  if (!button) {
    throw new Error(`No button ${text}`);
  }

  return button;
}

// Vuetify's form validation and dialogs take a few ticks
export async function settle() {
  for (let i = 0; i < 5; i++) {
    await flushPromises();
  }
}
