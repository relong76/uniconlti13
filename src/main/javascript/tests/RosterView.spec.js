import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises } from "@vue/test-utils";
import RosterView from "../src/views/RosterView.vue";
import { getRoster } from "../src/api/advantage";
import { sampleLaunch } from "./helpers";
import { deferred, mountView, sampleMembers, type } from "./advantage-helpers";

vi.mock("../src/api/advantage", async (original) => ({ ...(await original()), getRoster: vi.fn() }));

function names(wrapper) {
  return wrapper.findAll("tbody th").map((cell) => cell.text());
}

const third = {
  userId: "user-3",
  name: null,
  email: "x@example.edu",
  roles: [],
  status: "Active"
};

const fourth = { ...third, userId: "user-4", email: null };

describe("RosterView", () => {
  beforeEach(() => {
    getRoster.mockReset();
  });

  it("asks for a launch without a token", async () => {
    const { wrapper } = await mountView(RosterView, { token: null, launch: null });

    expect(wrapper.text()).toContain("Launch the tool from your course to see its roster.");
    expect(getRoster).not.toHaveBeenCalled();
  });

  it("shows loading, then the members with role chips", async () => {
    const pending = deferred();
    getRoster.mockReturnValue(pending.promise);
    const { wrapper } = await mountView(RosterView);

    expect(wrapper.text()).toContain("Loading the roster");
    expect(getRoster).toHaveBeenCalledWith("api-token");

    pending.resolve(sampleMembers);
    await flushPromises();
    const text = wrapper.text();

    expect(text).toContain("Members of Biology 101");
    expect(text).toContain("2 members");
    expect(text).toContain("ada@example.edu");
    expect(text).toContain("Instructor");
    expect(text).toContain("Learner");
    expect(text).toContain("Inactive");
    expect(names(wrapper)).toEqual(["Ada Lovelace", "Grace Hopper"]);
    expect(wrapper.findAll("thead th").every((th) => th.attributes("scope") === "col")).toBe(true);
    expect(wrapper.find("caption").text()).toBe("Course members, sorted by name, ascending");
  });

  it("sorts by a column and flips the direction", async () => {
    getRoster.mockResolvedValue([...sampleMembers, third]);
    const { wrapper } = await mountView(RosterView, { launch: { ...sampleLaunch, context: null } });

    expect(wrapper.text()).not.toContain("Members of");
    expect(names(wrapper)).toEqual(["No name", "Ada Lovelace", "Grace Hopper"]);

    const header = (title) => wrapper.findAll("thead th").find((th) => th.text().startsWith(title));
    await header("Name").find("button").trigger("click");
    expect(names(wrapper)).toEqual(["Grace Hopper", "Ada Lovelace", "No name"]);
    expect(header("Name").attributes("aria-sort")).toBe("descending");

    await header("Email").find("button").trigger("click");
    expect(header("Email").attributes("aria-sort")).toBe("ascending");
    expect(header("Name").attributes("aria-sort")).toBe("none");
    expect(names(wrapper)).toEqual(["Grace Hopper", "Ada Lovelace", "No name"]);
    expect(wrapper.find("caption").text()).toBe("Course members, sorted by email, ascending");
  });


  it("filters the table and says when nothing matches", async () => {
    getRoster.mockResolvedValue([...sampleMembers, third]);
    const { wrapper } = await mountView(RosterView);

    await type(wrapper, "Search members", "learner");
    expect(names(wrapper)).toEqual(["Grace Hopper"]);
    expect(wrapper.text()).toContain("1 of 3 members");

    await type(wrapper, "Search members", "X@EXAMPLE");
    expect(names(wrapper)).toEqual(["No name"]);

    await type(wrapper, "Search members", "user-1");
    expect(names(wrapper)).toEqual(["Ada Lovelace"]);

    await type(wrapper, "Search members", "nobody");
    expect(names(wrapper)).toEqual([]);
    expect(wrapper.text()).toContain('No members match "nobody".');

    await type(wrapper, "Search members", "  ");
    expect(names(wrapper)).toHaveLength(3);
  });

  it("sorts members with missing values", async () => {
    getRoster.mockResolvedValue([third, sampleMembers[1], fourth, sampleMembers[0]]);
    const { wrapper } = await mountView(RosterView);

    expect(names(wrapper)).toEqual(["No name", "No name", "Ada Lovelace", "Grace Hopper"]);

    await wrapper.findAll("thead th").find((th) => th.text().startsWith("Email")).find("button").trigger("click");
    expect(names(wrapper).slice(2)).toEqual(["Ada Lovelace", "No name"]);
  });

  it("counts a single member", async () => {
    getRoster.mockResolvedValue([sampleMembers[0]]);
    const { wrapper } = await mountView(RosterView);

    expect(wrapper.text()).toContain("1 member");
    expect(wrapper.text()).not.toContain("1 members");
  });

  it("explains an empty roster", async () => {
    getRoster.mockResolvedValue([]);
    const { wrapper } = await mountView(RosterView);

    expect(wrapper.text()).toContain("The platform didn't return any members for this course.");
  });

  it("shows an error and tries again", async () => {
    getRoster.mockRejectedValueOnce(new Error("The platform didn't complete the request."));
    getRoster.mockResolvedValueOnce(sampleMembers);
    const { wrapper } = await mountView(RosterView);

    expect(wrapper.text()).toContain("Couldn't load the roster");
    expect(wrapper.text()).toContain("The platform didn't complete the request.");

    await wrapper.findAll("button").find((b) => b.text() === "Try again").trigger("click");
    await flushPromises();

    expect(getRoster).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain("Ada Lovelace");
  });

  it("loads once a token arrives", async () => {
    getRoster.mockResolvedValue(sampleMembers);
    const { wrapper, session } = await mountView(RosterView, { token: null });

    session.token = "later";
    await flushPromises();

    expect(getRoster).toHaveBeenCalledWith("later");
    expect(wrapper.text()).toContain("Grace Hopper");
  });
});
