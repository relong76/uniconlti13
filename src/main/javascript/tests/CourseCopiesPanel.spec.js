import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { enableAutoUnmount } from "@vue/test-utils";
import CourseCopiesPanel from "../src/components/admin/CourseCopiesPanel.vue";
import { listCourseCopies } from "../src/api/admin";
import { apiError, mountAdmin, page, sampleNotice, settle } from "./admin-helpers";

vi.mock("../src/api/admin", async (original) => ({ ...(await original()), listCourseCopies: vi.fn() }));

enableAutoUnmount(afterEach);

const sparse = {
  noticeId: "notice-2",
  keyId: 5,
  deploymentId: null,
  noticeTimestamp: null,
  contextKey: "ctx-9",
  contextTitle: null,
  originContextKeys: null,
  createdAt: null
};

describe("CourseCopiesPanel", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("lists the notices with their courses", async () => {
    vi.mocked(listCourseCopies).mockResolvedValue(page([sampleNotice, sparse, { ...sparse, noticeId: "notice-3", createdAt: "yesterday", originContextKeys: " , " }]));
    const { wrapper } = await mountAdmin(CourseCopiesPanel);

    expect(listCourseCopies).toHaveBeenCalledWith("Ym9zczpzM2NyZXQ=", { page: 0, size: 20 });
    expect(wrapper.find("h2").text()).toBe("Course copies");
    expect(wrapper.findAll("th[scope=col]").map((th) => th.text())).toEqual(["Copied into", "Copied from", "Received", "Deployment ID", "Notice ID"]);
    const rows = wrapper.findAll("tbody tr");
    expect(rows).toHaveLength(3);

    const first = rows[0].text();
    expect(first).toContain("Biology 101 (Spring)");
    expect(first).toContain("ctx-new");
    expect(rows[0].findAll("td")[0].findAll("div").map((d) => d.text())).toEqual(["ctx-1", "ctx-2"]);
    expect(first).toContain(new Date(sampleNotice.createdAt).toLocaleString());
    expect(first).toContain(`Sent ${new Date(sampleNotice.noticeTimestamp).toLocaleString()}`);
    expect(first).toContain("284:abc");
    expect(first).toContain("Platform key ID 3");
    expect(first).toContain("notice-1");

    const second = rows[1].text();
    expect(second).toContain("Untitled course");
    expect(second).toContain("Not sent");
    expect(second).toContain("Unknown");
    expect(second).not.toContain("Sent ");

    expect(rows[2].text()).toContain("yesterday");
    expect(rows[2].text()).toContain("Not sent");
    expect(wrapper.text()).toContain("Showing 1 to 3 of 3");
  });

  it("says when there are no notices", async () => {
    vi.mocked(listCourseCopies).mockResolvedValue(page([]));
    const { wrapper } = await mountAdmin(CourseCopiesPanel);

    expect(wrapper.text()).toContain("No course copies yet.");
    expect(wrapper.find("table").exists()).toBe(false);
  });

  it("shows a loading bar, then the error", async () => {
    let fail;
    vi.mocked(listCourseCopies).mockReturnValue(new Promise((resolve, reject) => { fail = reject; }));
    const { wrapper } = await mountAdmin(CourseCopiesPanel);

    expect(wrapper.find(".v-progress-linear").exists()).toBe(true);
    expect(wrapper.text()).not.toContain("No course copies yet.");

    fail(apiError(500, "Server Error"));
    await settle();
    expect(wrapper.find(".v-alert").text()).toContain("Couldn't load the course copies: Server Error");
    expect(wrapper.find(".v-progress-linear").exists()).toBe(false);
  });

  it("signs out when the login stops working", async () => {
    vi.mocked(listCourseCopies).mockRejectedValue(apiError(401));
    const { wrapper, admin } = await mountAdmin(CourseCopiesPanel);

    expect(admin.signedIn).toBe(false);
    expect(wrapper.text()).not.toContain("Couldn't load");
  });

  it("pages and refreshes", async () => {
    vi.mocked(listCourseCopies).mockResolvedValue(page([sampleNotice], 2));
    const { wrapper } = await mountAdmin(CourseCopiesPanel, { props: { size: 1 } });

    await wrapper.find('button[aria-label="Next page"]').trigger("click");
    await settle();
    expect(listCourseCopies).toHaveBeenLastCalledWith("Ym9zczpzM2NyZXQ=", { page: 1, size: 1 });
    expect(wrapper.text()).toContain("Showing 2 to 2 of 2");

    await wrapper.findAll("button").find((b) => b.text() === "Refresh").trigger("click");
    await settle();
    expect(listCourseCopies).toHaveBeenCalledTimes(3);
  });
});
