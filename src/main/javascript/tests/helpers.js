import { mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory } from "vue-router";
import { createVuetify } from "vuetify";
import * as components from "vuetify/components";
import * as directives from "vuetify/directives";
import { createAppRouter } from "../src/router";

export function createTestVuetify() {
  return createVuetify({ components, directives });
}

// mounts a component with the app's plugins; Vuetify's overlays need a v-app around them
export async function mountWithApp(component, { route = "/launch", pinia, props, stubs } = {}) {
  const activePinia = pinia || createPinia();
  setActivePinia(activePinia);
  const router = createAppRouter(createMemoryHistory());
  await router.push(route);
  await router.isReady();

  const wrapper = mount(
    { components: { Target: component }, template: "<v-app><Target v-bind=\"$attrs\" /></v-app>" },
    {
      attrs: props,
      global: { plugins: [createTestVuetify(), activePinia, router], stubs }
    }
  );

  return { wrapper, router, pinia: activePinia };
}

export function jsonResponse(body, { status = 200, statusText = "OK" } = {}) {
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText,
    text: async () => (body === undefined ? "" : typeof body === "string" ? body : JSON.stringify(body))
  };
}

export const sampleLaunch = {
  messageType: "LtiResourceLinkRequest",
  version: "1.3.0",
  issuer: "https://canvas.instructure.com",
  clientId: "202570000000000231",
  deploymentId: "284:abc",
  targetLinkUri: "https://tool.example.com/lti3",
  launchedAt: "2026-11-12T17:00:00Z",
  user: {
    id: "user-1",
    name: "Ada Lovelace",
    givenName: "Ada",
    familyName: "Lovelace",
    email: "ada@example.edu",
    picture: null,
    locale: "en",
    roles: ["http://purl.imsglobal.org/vocab/lis/v2/membership#Instructor"]
  },
  context: { id: "ctx-1", label: "BIO101", title: "Biology 101" },
  resourceLink: { id: "rl-1", title: "Week 1", description: "Intro" },
  platform: { name: "Canvas", product_family_code: "canvas", version: "cloud" },
  launchPresentation: { document_target: "iframe" },
  custom: { canvas_course_id: "409" },
  services: {
    assignmentsAndGrades: { lineitems: "https://lms/lineitems", scope: [] },
    namesAndRoles: { context_memberships_url: "https://lms/memberships" },
    platformNotifications: null
  },
  deepLinking: null,
  content: null,
  claims: { iss: "https://canvas.instructure.com", sub: "user-1" }
};
