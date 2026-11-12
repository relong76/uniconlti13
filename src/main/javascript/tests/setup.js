// jsdom lacks the browser APIs Vuetify's layout components use
class ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}

globalThis.ResizeObserver = globalThis.ResizeObserver || ResizeObserver;
globalThis.CSS = globalThis.CSS || { supports: () => false };

// Vuetify's dialogs and menus read the visual viewport, which jsdom doesn't have
globalThis.visualViewport = globalThis.visualViewport
  || Object.assign(new EventTarget(), { width: 1024, height: 768, offsetLeft: 0, offsetTop: 0, scale: 1 });
