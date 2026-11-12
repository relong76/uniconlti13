import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import DeepLinkView from "../src/views/DeepLinkView.vue";
import { useSessionStore } from "../src/stores/session";
import { jsonResponse, mountWithApp, sampleLaunch } from "./helpers";

const deepLinking = {
  deep_link_return_url: "https://lms.example.com/deep_links",
  accept_types: ["ltiResourceLink", "link"],
  accept_presentation_document_targets: ["iframe", "window"],
  accept_multiple: true,
  auto_create: true,
  title: "Week 2 reading",
  text: "Pick a reading"
};

const items = [
  { toolLinkId: "quiz", title: "Quiz", description: "A quiz", assignment: true, maxGrade: 10 },
  { toolLinkId: "page", title: "Page", description: "A page", assignment: false, maxGrade: null },
  { toolLinkId: "essay", title: null, description: "", assignment: true, maxGrade: null }
];

function deepLinkingLaunch(settings = deepLinking) {
  return { ...sampleLaunch, messageType: "LtiDeepLinkingRequest", deepLinking: settings };
}

let fetch;
let submit;

async function render({ launch = deepLinkingLaunch(), token = "api-token" } = {}) {
  const pinia = createPinia();
  setActivePinia(pinia);
  const session = useSessionStore();
  session.token = token;
  session.launch = launch;
  const { wrapper } = await mountWithApp(DeepLinkView, { route: "/deeplink", pinia });
  await flushPromises();
  return { wrapper, session };
}

function sendButton(wrapper) {
  return wrapper.findAll("button").find((button) => button.text().includes("Send to course"));
}

function respond(...responses) {
  responses.forEach((response) => fetch.mockResolvedValueOnce(response));
}

describe("DeepLinkView", () => {
  beforeEach(() => {
    fetch = vi.fn();
    vi.stubGlobal("fetch", fetch);
    submit = vi.spyOn(HTMLFormElement.prototype, "submit").mockImplementation(() => {});
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    document.body.querySelectorAll("form").forEach((form) => form.remove());
  });

  it("explains a launch that isn't for choosing content", async () => {
    const { wrapper } = await render({ launch: sampleLaunch });

    expect(wrapper.text()).toContain("This launch isn't for choosing content.");
    expect(wrapper.text()).not.toContain("Available content");
    expect(fetch).not.toHaveBeenCalled();
  });

  it("waits for the API token before loading", async () => {
    respond(jsonResponse(items));
    const { wrapper, session } = await render({ token: null });

    expect(fetch).not.toHaveBeenCalled();

    session.token = "late-token";
    await flushPromises();

    expect(fetch).toHaveBeenCalledTimes(1);
    expect(fetch.mock.calls[0][1].headers.Authorization).toBe("Bearer late-token");
    expect(wrapper.text()).toContain("Quiz");
  });

  it("shows a loading state", async () => {
    fetch.mockReturnValueOnce(new Promise(() => {}));
    const { wrapper } = await render();

    expect(wrapper.text()).toContain("Loading content…");
  });

  it("shows the settings and content, and sends several items", async () => {
    respond(jsonResponse(items), jsonResponse({ returnUrl: "https://lms.example.com/deep_links", jwt: "signed-jwt" }));
    const { wrapper } = await render();
    const text = wrapper.text();

    expect(wrapper.find("h1").text()).toBe("Choose content for your course");
    expect(wrapper.findAll("h2").map((h) => h.text())).toEqual(["What your course accepts", "Available content"]);
    expect(text).toContain("ltiResourceLink, link");
    expect(text).toContain("iframe, window");
    expect(text).toContain("More than one item");
    expect(text).toContain("Yes");
    expect(text).toContain("Week 2 reading");
    expect(text).toContain("Pick a reading");
    expect(text).toContain("Assignment, 10 points");
    expect(text).toContain("Not graded");
    expect(text).toContain("Untitled");
    expect(text).toMatch(/Assignment(?!,)/);
    expect(text).toContain("Nothing chosen yet");
    expect(wrapper.find('input[type="radio"]').exists()).toBe(false);
    expect(sendButton(wrapper).attributes("disabled")).toBeDefined();

    const boxes = wrapper.findAll('input[type="checkbox"]');
    expect(boxes).toHaveLength(3);
    await boxes[0].setValue(true);
    expect(wrapper.text()).toContain("1 item chosen");
    await boxes[1].setValue(true);
    expect(wrapper.text()).toContain("2 items chosen");

    await sendButton(wrapper).trigger("click");
    await flushPromises();

    const [url, options] = fetch.mock.calls[1];
    expect(url).toMatch(/\/api\/deeplink\/response$/);
    expect(JSON.parse(options.body)).toEqual({ selectedIds: ["quiz", "page"] });
    expect(submit).toHaveBeenCalledTimes(1);
    const form = submit.mock.instances[0];
    expect(form.action).toBe("https://lms.example.com/deep_links");
    expect(form.method).toBe("post");
    expect(form.querySelector('input[name="JWT"]').value).toBe("signed-jwt");
    expect(wrapper.text()).toContain("Sending your content to the course…");
    expect(sendButton(wrapper).attributes("disabled")).toBeDefined();
  });

  it("allows one item when the course doesn't accept several", async () => {
    respond(jsonResponse(items), jsonResponse({ returnUrl: "https://lms.example.com/one", jwt: "one-jwt" }));
    const { wrapper } = await render({
      launch: deepLinkingLaunch({ deep_link_return_url: "https://lms.example.com/one", accept_multiple: false })
    });
    const text = wrapper.text();

    expect(text).toContain("No, choose one item");
    expect(text).toContain("No, you confirm it in your course");
    expect(text).toContain("Not stated");
    expect(text).not.toContain("Suggested title");
    expect(wrapper.find('input[type="checkbox"]').exists()).toBe(false);

    const radios = wrapper.findAll('input[type="radio"]');
    expect(radios).toHaveLength(3);
    await radios[1].setValue(true);
    await radios[0].setValue(true);
    expect(wrapper.text()).toContain("1 item chosen");

    await sendButton(wrapper).trigger("click");
    await flushPromises();

    expect(JSON.parse(fetch.mock.calls[1][1].body)).toEqual({ selectedIds: ["quiz"] });
    expect(submit.mock.instances[0].action).toBe("https://lms.example.com/one");
  });

  it("treats a launch without settings as single choice", async () => {
    respond(jsonResponse(items));
    const { wrapper } = await render({ launch: deepLinkingLaunch(null) });

    expect(wrapper.text()).toContain("No, choose one item");
    expect(wrapper.findAll('input[type="radio"]')).toHaveLength(3);
  });

  it("warns when the course doesn't accept LTI links", async () => {
    respond(jsonResponse(items));
    const { wrapper } = await render({ launch: deepLinkingLaunch({ ...deepLinking, accept_types: ["file"] }) });

    expect(wrapper.text()).toContain("Your course doesn't accept LTI links here");
    await wrapper.findAll('input[type="checkbox"]')[0].setValue(true);
    expect(sendButton(wrapper).attributes("disabled")).toBeDefined();
  });

  it("says when there's no content", async () => {
    respond(jsonResponse([]));
    const { wrapper } = await render();

    expect(wrapper.text()).toContain("There's no content to add yet.");
    expect(sendButton(wrapper)).toBeUndefined();
  });

  it("shows the reason the content couldn't load", async () => {
    respond(jsonResponse({ status: 403, detail: "This launch isn't for choosing content." }, { status: 403, statusText: "Forbidden" }));
    const { wrapper } = await render();

    expect(wrapper.text()).toContain("Couldn't load the content");
    expect(wrapper.text()).toContain("This launch isn't for choosing content.");
  });

  it("explains an expired API token", async () => {
    respond(jsonResponse({ detail: "Invalid API token" }, { status: 401, statusText: "Unauthorized" }));
    const { wrapper } = await render();

    expect(wrapper.text()).toContain("This launch has expired. Launch the tool again from your course.");
    expect(wrapper.text()).not.toContain("Invalid API token");
  });

  it("falls back to the status text", async () => {
    respond(jsonResponse(undefined, { status: 500, statusText: "Server Error" }));
    const { wrapper } = await render();

    expect(wrapper.text()).toContain("Server Error");
  });

  it("shows why sending failed and lets you try again", async () => {
    respond(
      jsonResponse(items),
      jsonResponse({ detail: "These items aren't available: quiz" }, { status: 400, statusText: "Bad Request" })
    );
    const { wrapper } = await render();

    await wrapper.findAll('input[type="checkbox"]')[0].setValue(true);
    await sendButton(wrapper).trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("Couldn't send to your course");
    expect(wrapper.text()).toContain("These items aren't available: quiz");
    expect(submit).not.toHaveBeenCalled();
    expect(sendButton(wrapper).attributes("disabled")).toBeUndefined();
  });
});
