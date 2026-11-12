import { flushPromises } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { VBtn, VCheckbox, VSelect, VTextField, VTextarea } from "vuetify/components";
import { useSessionStore } from "../src/stores/session";
import { mountWithApp, sampleLaunch } from "./helpers";

export const LINEITEMS = "https://lms/api/lti/courses/1/line_items";

export function sampleLineItem(overrides = {}) {
  return {
    key: "5",
    id: `${LINEITEMS}/5`,
    label: "Quiz 1",
    scoreMaximum: "10",
    tag: "quiz",
    resourceId: "res-1",
    resourceLinkId: "rl-1",
    startDateTime: "2026-11-01T09:00:00.000Z",
    endDateTime: null,
    results: [],
    ...overrides
  };
}

export const sampleMembers = [
  {
    userId: "user-1",
    name: "Ada Lovelace",
    email: "ada@example.edu",
    roles: ["http://purl.imsglobal.org/vocab/lis/v2/membership#Instructor"],
    status: "Active"
  },
  {
    userId: "user-2",
    name: "Grace Hopper",
    email: null,
    roles: ["http://purl.imsglobal.org/vocab/lis/v2/membership#Learner"],
    status: "Inactive"
  }
];

// mounts a view with a session; token null means no launch
export async function mountView(component, { token = "api-token", launch = sampleLaunch, route, props } = {}) {
  const pinia = createPinia();
  setActivePinia(pinia);
  const session = useSessionStore();
  session.token = token;
  session.launch = launch;

  const mounted = await mountWithApp(component, { pinia, route, props });
  await flushPromises();

  return { ...mounted, session };
}

export function button(wrapper, text) {
  return wrapper.findAllComponents(VBtn).find((item) => item.text().trim() === text || item.attributes("aria-label") === text);
}

// a VSelect renders a VTextField with the same label, so look for selects first
export function field(wrapper, label) {
  return [VSelect, VCheckbox, VTextarea, VTextField]
    .flatMap((type) => wrapper.findAllComponents(type))
    .find((item) => item.props("label") === label);
}

export async function type(wrapper, label, value) {
  await field(wrapper, label).find("input, textarea").setValue(value);
}

export async function choose(wrapper, label, value) {
  field(wrapper, label).vm.$emit("update:modelValue", value);
  await flushPromises();
}

export async function click(wrapper, text) {
  await button(wrapper, text).trigger("click");
  await flushPromises();
}

// v-form validation and submission settle over a few ticks
export async function submit(wrapper, text) {
  await button(wrapper, text).trigger("submit");
  await flushPromises();
  await new Promise((resolve) => setTimeout(resolve, 0));
  await flushPromises();
}

export function bodyText() {
  return document.body.textContent;
}

export function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((ok, fail) => {
    resolve = ok;
    reject = fail;
  });

  return { promise, resolve, reject };
}
