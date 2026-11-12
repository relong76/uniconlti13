import { createRouter, createWebHashHistory } from "vue-router";
import LaunchView from "../views/LaunchView.vue";

export const routes = [
  { path: "/", redirect: "/launch" },
  { path: "/launch", name: "launch", component: LaunchView },
  { path: "/deeplink", name: "deeplink", component: () => import("../views/DeepLinkView.vue") },
  { path: "/grades", name: "grades", component: () => import("../views/GradesView.vue") },
  { path: "/grades/:id", name: "lineItem", component: () => import("../views/LineItemView.vue"), props: true },
  { path: "/roster", name: "roster", component: () => import("../views/RosterView.vue") },
  { path: "/admin", name: "admin", component: () => import("../views/AdminView.vue"), meta: { admin: true } },
  { path: "/:pathMatch(.*)*", redirect: "/launch" }
];

export function createAppRouter(history = createWebHashHistory()) {
  return createRouter({ history, routes });
}

export default createAppRouter();
