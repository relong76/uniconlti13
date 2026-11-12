import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises } from "@vue/test-utils";
import LineItemView from "../src/views/LineItemView.vue";
import { getLineItem, getRoster, postScore } from "../src/api/advantage";
import { formatDateTime } from "../src/utils/dates";
import { sampleLaunch } from "./helpers";
import { choose, click, deferred, field, mountView, sampleLineItem, sampleMembers, submit, type } from "./advantage-helpers";

vi.mock("../src/api/advantage", async (original) => ({
  ...(await original()),
  getLineItem: vi.fn(),
  getRoster: vi.fn(),
  postScore: vi.fn()
}));

const results = [
  { id: "r1", userId: "user-1", resultScore: "8", resultMaximum: "10", comment: "Good work" },
  { id: null, userId: "stranger", resultScore: "3", resultMaximum: null, comment: null },
  { id: "r3", userId: "user-2", resultScore: null, resultMaximum: "10", comment: "" }
];
const withoutRoster = { ...sampleLaunch, services: { ...sampleLaunch.services, namesAndRoles: null } };

function render(options = {}) {
  return mountView(LineItemView, { route: "/grades/5", props: { id: "5" }, ...options });
}

describe("LineItemView", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    getLineItem.mockResolvedValue(sampleLineItem({ results }));
    getRoster.mockResolvedValue(sampleMembers);
  });

  it("asks for a launch without a token", async () => {
    const { wrapper } = await render({ token: null, launch: null });

    expect(wrapper.text()).toContain("Launch the tool from your course to see its grades.");
    expect(getLineItem).not.toHaveBeenCalled();
  });

  it("shows loading, then the details and results", async () => {
    const pending = deferred();
    getLineItem.mockReturnValue(pending.promise);
    const { wrapper } = await render();

    expect(wrapper.text()).toContain("Loading the line item");

    pending.resolve(sampleLineItem({ results }));
    await flushPromises();
    const text = wrapper.text();

    expect(getLineItem).toHaveBeenCalledWith("api-token", "5");
    expect(getRoster).toHaveBeenCalledWith("api-token");
    expect(wrapper.find("h1").text()).toBe("Quiz 1");
    expect(text).toContain("https://lms/api/lti/courses/1/line_items/5");
    expect(text).toContain(formatDateTime("2026-11-01T09:00:00.000Z"));
    expect(text).toContain("Not set");
    expect(text).toContain("Ada Lovelace");
    expect(text).toContain("8 of 10");
    expect(text).toContain("Good work");
    expect(text).toContain("stranger");
    expect(text).toContain("3");
    expect(text).toContain("No score");
    expect(wrapper.findAll("thead th").every((th) => th.attributes("scope") === "col")).toBe(true);
    expect(field(wrapper, "User").props("items")).toEqual([
      { value: "user-1", title: "Ada Lovelace (Instructor)" },
      { value: "user-2", title: "Grace Hopper (Learner)" }
    ]);
    expect(field(wrapper, "Out of").props("modelValue")).toBe("10");
    expect(field(wrapper, "Activity progress").props("items").map((item) => item.title)).toEqual(
      ["Initialized", "Started", "In progress", "Submitted", "Completed"]
    );
    expect(field(wrapper, "Grading progress").props("items").map((item) => item.value)).toEqual(
      ["FullyGraded", "Pending", "PendingManual", "Failed", "NotReady"]
    );
  });

  it("explains a line item without results", async () => {
    getLineItem.mockResolvedValue(sampleLineItem({ label: null, scoreMaximum: null }));
    getRoster.mockResolvedValue([{ userId: "u9", name: null, roles: [], status: "Active" }]);
    const { wrapper } = await render();

    expect(wrapper.find("h1").text()).toBe("Untitled line item");
    expect(wrapper.text()).toContain("No scores have been posted to this line item yet.");
    expect(field(wrapper, "User").props("items")).toEqual([{ value: "u9", title: "u9 (no role)" }]);
    expect(field(wrapper, "Out of").props("modelValue")).toBe("");
  });

  it("posts a score for a member of the roster", async () => {
    postScore.mockResolvedValue(sampleLineItem({ results: [results[0], { ...results[2], resultScore: "9" }] }));
    const { wrapper } = await render();

    await choose(wrapper, "User", "user-2");
    await type(wrapper, "Score", "9");
    await type(wrapper, "Comment", "Nice");
    await choose(wrapper, "Activity progress", "Submitted");
    await choose(wrapper, "Grading progress", "Pending");
    await submit(wrapper, "Post score");

    expect(postScore).toHaveBeenCalledWith("api-token", "5", {
      userId: "user-2",
      scoreGiven: "9",
      scoreMaximum: "10",
      comment: "Nice",
      activityProgress: "Submitted",
      gradingProgress: "Pending"
    });
    expect(wrapper.text()).toContain("Posted the score for Grace Hopper.");
    expect(wrapper.text()).toContain("9 of 10");
    expect(field(wrapper, "User").props("modelValue")).toBe("");
    expect(field(wrapper, "Activity progress").props("modelValue")).toBe("Completed");
  });

  it("posts by user id and falls back to the line item maximum", async () => {
    postScore.mockResolvedValue(sampleLineItem({ scoreMaximum: null }));
    const { wrapper } = await render();

    await field(wrapper, "Enter a user ID instead").find("input").setValue(true);
    await type(wrapper, "User ID", " someone ");
    await type(wrapper, "Out of", "");
    await type(wrapper, "Score", "4");
    await submit(wrapper, "Post score");

    expect(postScore).toHaveBeenCalledWith("api-token", "5", expect.objectContaining({ userId: "someone", scoreGiven: "4", scoreMaximum: "10" }));
    expect(wrapper.text()).toContain("Posted the score for someone.");
    expect(field(wrapper, "Out of").props("modelValue")).toBe("");
  });

  it("posts progress without a score", async () => {
    postScore.mockResolvedValue(sampleLineItem());
    const { wrapper } = await render({ launch: withoutRoster });

    expect(getRoster).not.toHaveBeenCalled();
    expect(field(wrapper, "Enter a user ID instead")).toBeUndefined();

    await type(wrapper, "User ID", "user-1");
    field(wrapper, "Score").vm.$emit("update:modelValue", null);
    await choose(wrapper, "Activity progress", "Started");
    await choose(wrapper, "Grading progress", "NotReady");
    await submit(wrapper, "Post score");

    expect(postScore).toHaveBeenCalledWith("api-token", "5", expect.objectContaining({ scoreGiven: null, scoreMaximum: null, activityProgress: "Started" }));
  });

  it("checks the score form", async () => {
    const { wrapper } = await render({ launch: withoutRoster });

    await type(wrapper, "Out of", "-2");
    await submit(wrapper, "Post score");

    expect(postScore).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("Choose a user.");
    expect(wrapper.text()).toContain("Enter a maximum score greater than 0.");

    const rules = field(wrapper, "Score").props("rules");
    expect(rules[0]("abc")).toBe("Enter the score as a number.");
    expect(rules[0](null)).toBe(true);
    expect(rules[0](undefined)).toBe(true);
    expect(rules[0]("1.5")).toBe(true);
    const max = field(wrapper, "Out of").props("rules")[0];
    expect(max(null)).toBe(true);
    expect(max(undefined)).toBe(true);
  });

  it("shows a failed post", async () => {
    postScore.mockRejectedValue(new Error("Can't post scores"));
    const { wrapper } = await render();

    await choose(wrapper, "User", "user-1");
    await submit(wrapper, "Post score");

    expect(wrapper.text()).toContain("Can't post scores");
  });

  it("works without the roster when it fails", async () => {
    getRoster.mockRejectedValue(new Error("Forbidden"));
    const { wrapper } = await render();

    expect(wrapper.text()).toContain("The roster isn't available, so enter a user ID. Forbidden");
    expect(field(wrapper, "User ID")).toBeTruthy();
    expect(wrapper.text()).toContain("stranger");
  });

  it("shows an error and tries again", async () => {
    getLineItem.mockRejectedValueOnce(new Error("Can't get the lineitem 5"));
    const { wrapper } = await render();

    expect(wrapper.text()).toContain("Couldn't load the line item");
    expect(wrapper.text()).toContain("Can't get the lineitem 5");

    await click(wrapper, "Try again");

    expect(wrapper.find("h1").text()).toBe("Quiz 1");
  });

  it("loads once a token arrives", async () => {
    const { session } = await render({ token: null });

    session.token = "later";
    await flushPromises();

    expect(getLineItem).toHaveBeenCalledWith("later", "5");
  });
});
