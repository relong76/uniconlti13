import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises } from "@vue/test-utils";
import { createPinia } from "pinia";
import App from "../src/App.vue";
import { useSessionStore } from "../src/stores/session";
import { mountWithApp, sampleLaunch } from "./helpers";
import { routes } from "../src/router";
import defaultRouter from "../src/router";

describe("App", () => {
  beforeEach(() => vi.restoreAllMocks());

  it("shows the navigation for the launch's services", async () => {
    const pinia = createPinia();
    const store = useSessionStore(pinia);
    vi.spyOn(store, "start").mockImplementation(async () => {
      store.launch = sampleLaunch;
    });
    const { wrapper } = await mountWithApp(App, { pinia, route: "/launch" });
    await flushPromises();

    const text = wrapper.text();
    expect(store.start).toHaveBeenCalled();
    expect(text).toContain("Launch");
    expect(text).toContain("Grades");
    expect(text).toContain("Roster");
    expect(text).toContain("Admin");
    expect(text).not.toContain("Choose content");
  });

  it("shows loading, then the launch error", async () => {
    const pinia = createPinia();
    const store = useSessionStore(pinia);
    let finish;
    vi.spyOn(store, "start").mockImplementation(() => {
      store.loading = true;
      return new Promise((resolve) => { finish = () => { store.loading = false; store.error = "Launch the tool from your course to start."; resolve(); }; });
    });
    const { wrapper } = await mountWithApp(App, { pinia, route: "/launch" });
    await flushPromises();

    expect(wrapper.text()).toContain("Loading your launch");
    finish();
    await flushPromises();
    expect(wrapper.text()).toContain("Launch the tool from your course to start.");
    expect(wrapper.text()).not.toContain("Grades");
  });

  it("sends a deep linking launch to choosing content", async () => {
    const pinia = createPinia();
    const store = useSessionStore(pinia);
    vi.spyOn(store, "start").mockImplementation(async () => {
      store.launch = { ...sampleLaunch, messageType: "LtiDeepLinkingRequest", services: {} };
    });
    const { wrapper, router } = await mountWithApp(App, { pinia, route: "/launch" });
    await flushPromises();

    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe("/deeplink"));
    expect(wrapper.text()).toContain("Choose content");
  });

  it("stays on the page a deep linking launch reloads to", async () => {
    const pinia = createPinia();
    const store = useSessionStore(pinia);
    vi.spyOn(store, "start").mockImplementation(async () => {
      store.launch = { ...sampleLaunch, messageType: "LtiDeepLinkingRequest", services: {} };
    });
    const { router } = await mountWithApp(App, { pinia, route: "/roster" });
    await flushPromises();

    expect(router.currentRoute.value.path).toBe("/roster");
  });

  it("doesn't need a launch on the admin pages", async () => {
    const pinia = createPinia();
    const store = useSessionStore(pinia);
    const start = vi.spyOn(store, "start");
    const { wrapper } = await mountWithApp(App, { pinia, route: "/admin" });
    await flushPromises();

    expect(start).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("Admin");
  });
});

describe("router", () => {
  it("routes unknown paths and the root to the launch", async () => {
    expect(routes.find((r) => r.path === "/").redirect).toBe("/launch");
    expect(routes.at(-1).redirect).toBe("/launch");
    expect(defaultRouter.options.history).toBeDefined();
  });

  it("lazily loads each screen", async () => {
    for (const route of routes.filter((r) => typeof r.component === "function" && r.component.length === 0)) {
      const module = await route.component();
      expect(module.default).toBeDefined();
    }
  });
});
