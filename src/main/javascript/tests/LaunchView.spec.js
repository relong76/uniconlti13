import { describe, expect, it } from "vitest";
import { createPinia } from "pinia";
import LaunchView from "../src/views/LaunchView.vue";
import { useSessionStore } from "../src/stores/session";
import { mountWithApp, sampleLaunch } from "./helpers";

async function render(launch) {
  const pinia = createPinia();
  const { wrapper } = await mountWithApp(LaunchView, { pinia });
  useSessionStore().launch = launch;
  await wrapper.vm.$nextTick();
  return wrapper;
}

describe("LaunchView", () => {
  it("shows the user, course, roles, platform and services", async () => {
    const text = (await render(sampleLaunch)).text();

    expect(text).toContain("Ada Lovelace");
    expect(text).toContain("ada@example.edu");
    expect(text).toContain("AL");
    expect(text).toContain("LtiResourceLinkRequest");
    expect(text).toContain("Biology 101");
    expect(text).toContain("BIO101");
    expect(text).toContain("Instructor");
    expect(text).toContain("(membership)");
    expect(text).toContain("Week 1");
    expect(text).toContain("Intro");
    expect(text).toContain("Canvas · canvas · cloud");
    expect(text).toContain("202570000000000231");
    expect(text).toContain("Grades");
    expect(text).toContain("Roster");
    expect(text).toContain("canvas_course_id");
    expect(text).toContain("The launch didn't ask for demo content");
  });

  it("shows requested content", async () => {
    const text = (await render({
      ...sampleLaunch,
      content: { toolLinkId: "1234", title: "Quiz", description: "A quiz", assignment: true, maxGrade: 10 }
    })).text();

    expect(text).toContain("Quiz");
    expect(text).toContain("A quiz");
    expect(text).toContain("Assignment, 10 points");
    expect(text).toContain("1234");
  });

  it("handles a sparse launch", async () => {
    const wrapper = await render({
      ...sampleLaunch,
      user: { ...sampleLaunch.user, name: null, email: null, picture: "https://lms/me.png", roles: [] },
      context: null,
      resourceLink: { id: "rl", title: null },
      platform: null,
      custom: null,
      services: { assignmentsAndGrades: null, namesAndRoles: null, platformNotifications: { url: "x" } },
      content: { toolLinkId: "1", title: "Page", description: "", assignment: false, maxGrade: null }
    });
    const text = wrapper.text();

    expect(text).toContain("Unknown user");
    expect(text).toContain("The launch didn't include a course.");
    expect(text).toContain("No roles were sent.");
    expect(text).toContain("Untitled");
    expect(text).toContain("Not offered on this launch");
    expect(text).not.toContain("Custom parameters");
    expect(text).not.toContain("points");
    expect(wrapper.find("img").exists() || wrapper.html().includes("me.png")).toBe(true);
  });

  it("explains a launch without a resource link and with an empty name", async () => {
    const text = (await render({
      ...sampleLaunch,
      user: { ...sampleLaunch.user, name: "   " },
      resourceLink: null,
      platform: {}
    })).text();

    expect(text).toContain("?");
    expect(text).toContain("This launch isn't for a resource link.");
  });

  it("renders nothing without a launch", async () => {
    const wrapper = await render(null);
    expect(wrapper.text()).toBe("");
  });
});
