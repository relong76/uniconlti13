import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises } from "@vue/test-utils";
import GradesView from "../src/views/GradesView.vue";
import { createLineItem, deleteLineItem, listLineItems, updateLineItem } from "../src/api/advantage";
import { formatDateTime, toLocalInput } from "../src/utils/dates";
import { sampleLaunch } from "./helpers";
import { button, click, deferred, field, mountView, sampleLineItem, submit, type } from "./advantage-helpers";

vi.mock("../src/api/advantage", async (original) => ({
  ...(await original()),
  listLineItems: vi.fn(),
  createLineItem: vi.fn(),
  updateLineItem: vi.fn(),
  deleteLineItem: vi.fn()
}));

const quiz = sampleLineItem({ results: [{ id: "r1", userId: "user-1", resultScore: "8", resultMaximum: "10", comment: "" }] });
const untitled = sampleLineItem({ key: "6", id: "https://lms/line_items/6", label: null, tag: null, resourceLinkId: null, startDateTime: null });

describe("GradesView", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    listLineItems.mockResolvedValue([quiz, untitled]);
  });

  it("asks for a launch without a token", async () => {
    const { wrapper } = await mountView(GradesView, { token: null, launch: null });

    expect(wrapper.text()).toContain("Launch the tool from your course to see its grades.");
    expect(button(wrapper, "New line item").props("disabled")).toBe(true);
    expect(listLineItems).not.toHaveBeenCalled();
  });

  it("shows loading, then a table of line items", async () => {
    const pending = deferred();
    listLineItems.mockReturnValue(pending.promise);
    const { wrapper } = await mountView(GradesView);

    expect(wrapper.text()).toContain("Loading line items");

    pending.resolve([quiz, untitled]);
    await flushPromises();
    const text = wrapper.text();

    expect(text).toContain("Line items in Biology 101");
    expect(text).toContain("Quiz 1");
    expect(text).toContain("Untitled");
    expect(text).toContain("quiz");
    expect(text).toContain("rl-1");
    expect(text).toContain(formatDateTime(quiz.startDateTime));
    expect(wrapper.findAll("thead th").every((th) => th.attributes("scope") === "col")).toBe(true);
    expect(wrapper.find("tbody a").attributes("href")).toContain("/grades/5");
    expect(button(wrapper, "Edit untitled line item")).toBeTruthy();
    expect(button(wrapper, "Delete Quiz 1")).toBeTruthy();
  });

  it("explains an empty gradebook", async () => {
    listLineItems.mockResolvedValue([]);
    const { wrapper } = await mountView(GradesView, { launch: { ...sampleLaunch, context: null } });

    expect(wrapper.text()).toContain("No line items yet");
    expect(wrapper.text()).not.toContain("Line items in");
  });

  it("shows an error and tries again", async () => {
    listLineItems.mockRejectedValueOnce(new Error("This course doesn't offer Assignment and Grade Services to the tool."));
    const { wrapper } = await mountView(GradesView);

    expect(wrapper.text()).toContain("Couldn't load the line items");
    expect(wrapper.text()).toContain("doesn't offer Assignment and Grade Services");

    await click(wrapper, "Try again");

    expect(wrapper.text()).toContain("Quiz 1");
  });

  it("loads once a token arrives", async () => {
    const { session } = await mountView(GradesView, { token: null });

    session.token = "later";
    await flushPromises();

    expect(listLineItems).toHaveBeenCalledWith("later");
  });

  it("creates a line item", async () => {
    createLineItem.mockResolvedValue(sampleLineItem({ key: "9" }));
    const { wrapper } = await mountView(GradesView);

    await click(wrapper, "New line item");
    expect(field(wrapper, "Resource link ID").props("modelValue")).toBe("rl-1");
    expect(field(wrapper, "Maximum score").props("modelValue")).toBe("100");

    await type(wrapper, "Label", "  Essay  ");
    await type(wrapper, "Maximum score", "20");
    await type(wrapper, "Tag", "essay");
    await type(wrapper, "Resource ID", " res-9 ");
    await type(wrapper, "Resource link ID", "");
    await type(wrapper, "Starts", "2026-12-01T10:00");
    await type(wrapper, "Ends", "2026-12-08T10:00");
    await submit(wrapper, "Create line item");

    expect(createLineItem).toHaveBeenCalledWith("api-token", {
      label: "Essay",
      scoreMaximum: "20",
      tag: "essay",
      resourceId: "res-9",
      resourceLinkId: null,
      startDateTime: new Date("2026-12-01T10:00").toISOString(),
      endDateTime: new Date("2026-12-08T10:00").toISOString()
    });
    expect(listLineItems).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('Created "Essay".');

    await wrapper.findAll(".v-alert").find((alert) => alert.text().includes("Created")).find("button").trigger("click");
    expect(wrapper.text()).not.toContain('Created "Essay".');
  });

  it("starts a new line item without a resource link", async () => {
    const { wrapper } = await mountView(GradesView, { launch: { ...sampleLaunch, resourceLink: null } });

    await click(wrapper, "New line item");

    expect(field(wrapper, "Resource link ID").props("modelValue")).toBe("");
  });

  it("checks the form before creating", async () => {
    const { wrapper } = await mountView(GradesView);

    await click(wrapper, "New line item");
    await type(wrapper, "Maximum score", "0");
    await submit(wrapper, "Create line item");

    expect(createLineItem).not.toHaveBeenCalled();
    expect(document.body.textContent).toContain("Enter a label.");
    expect(document.body.textContent).toContain("Enter a maximum score greater than 0.");
  });

  it("keeps the form open when saving fails, and cancels", async () => {
    createLineItem.mockRejectedValue(new Error("The platform didn't complete the request."));
    const { wrapper } = await mountView(GradesView);

    await click(wrapper, "New line item");
    await type(wrapper, "Label", "Essay");
    await submit(wrapper, "Create line item");

    expect(document.body.textContent).toContain("The platform didn't complete the request.");
    expect(listLineItems).toHaveBeenCalledTimes(1);

    const dialog = wrapper.findAllComponents({ name: "VDialog" })[0];
    await click(wrapper, "Cancel");
    expect(dialog.props("modelValue")).toBe(false);

    await click(wrapper, "New line item");
    dialog.vm.$emit("update:modelValue", false);
    await flushPromises();
    expect(dialog.props("modelValue")).toBe(false);
  });

  it("edits a line item", async () => {
    updateLineItem.mockResolvedValue(quiz);
    const { wrapper } = await mountView(GradesView);

    await click(wrapper, "Edit Quiz 1");
    expect(field(wrapper, "Label").props("modelValue")).toBe("Quiz 1");
    expect(field(wrapper, "Starts").props("modelValue")).toBe(toLocalInput(quiz.startDateTime));
    expect(field(wrapper, "Ends").props("modelValue")).toBe("");

    await type(wrapper, "Label", "Quiz one");
    await submit(wrapper, "Save changes");

    expect(updateLineItem).toHaveBeenCalledWith("api-token", "5", expect.objectContaining({
      label: "Quiz one",
      scoreMaximum: "10",
      tag: "quiz",
      resourceId: "res-1",
      startDateTime: new Date(quiz.startDateTime).toISOString(),
      endDateTime: null
    }));
    expect(wrapper.text()).toContain('Saved "Quiz one".');
  });

  it("edits a sparse line item", async () => {
    const { wrapper } = await mountView(GradesView);

    await click(wrapper, "Edit untitled line item");

    expect(field(wrapper, "Label").props("modelValue")).toBe("");
    expect(field(wrapper, "Tag").props("modelValue")).toBe("");
    expect(field(wrapper, "Resource link ID").props("modelValue")).toBe("");

  });

  it("edits a line item without a maximum or resource id", async () => {
    listLineItems.mockResolvedValue([sampleLineItem({ scoreMaximum: null, resourceId: null })]);
    const { wrapper } = await mountView(GradesView);

    await click(wrapper, "Edit Quiz 1");

    expect(field(wrapper, "Maximum score").props("modelValue")).toBe("");
    expect(field(wrapper, "Resource ID").props("modelValue")).toBe("");
  });

  it("confirms before deleting", async () => {
    deleteLineItem.mockResolvedValue(null);
    const { wrapper } = await mountView(GradesView);

    await click(wrapper, "Delete Quiz 1");
    expect(document.body.textContent).toContain('Deleting "Quiz 1" removes it and its scores from the course gradebook.');

    await click(wrapper, "Cancel");
    expect(deleteLineItem).not.toHaveBeenCalled();

    await click(wrapper, "Delete Quiz 1");
    await click(wrapper, "Delete");

    expect(deleteLineItem).toHaveBeenCalledWith("api-token", "5");
    expect(listLineItems).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('Deleted "Quiz 1".');
  });

  it("shows a failed delete in the dialog", async () => {
    deleteLineItem.mockRejectedValue(new Error("Can't delete the lineitem"));
    const { wrapper } = await mountView(GradesView);

    await click(wrapper, "Delete Quiz 1");
    await click(wrapper, "Delete");

    expect(document.body.textContent).toContain("Can't delete the lineitem");
    expect(listLineItems).toHaveBeenCalledTimes(1);

    await click(wrapper, "Cancel");
    expect(document.body.textContent).not.toContain("Can't delete the lineitem");
  });
});
