<template>
  <v-app>
    <v-app-bar color="primary" density="comfortable" flat>
      <v-app-bar-title>Unicon LTI 1.3 Demo</v-app-bar-title>
      <template #append>
        <nav class="d-flex" aria-label="Main">
          <v-btn v-for="item in navigation" :key="item.to" :to="item.to" variant="text" :prepend-icon="item.icon">
            {{ item.title }}
          </v-btn>
        </nav>
      </template>
    </v-app-bar>

    <v-main>
      <v-container v-if="needsLaunch && session.loading" class="text-center py-12">
        <v-progress-circular indeterminate color="primary" />
        <p class="mt-4 text-medium-emphasis">Loading your launch…</p>
      </v-container>
      <v-container v-else-if="needsLaunch && session.error" class="py-8">
        <v-alert type="warning" variant="tonal" :text="session.error" title="No launch" />
      </v-container>
      <router-view v-else />
    </v-main>
  </v-app>
</template>

<script setup>
import { computed, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useSessionStore } from "./stores/session";

const session = useSessionStore();
const route = useRoute();
const router = useRouter();

// the admin pages sign in on their own; everything else shows the launch the app was opened from
const needsLaunch = computed(() => !route.meta.admin);

const navigation = computed(() => {
  const items = [];

  if (session.launch) {
    items.push({ title: "Launch", to: "/launch", icon: "mdi-rocket-launch-outline" });

    if (session.isDeepLinking) {
      items.push({ title: "Choose content", to: "/deeplink", icon: "mdi-link-variant" });
    }

    if (session.hasGrades) {
      items.push({ title: "Grades", to: "/grades", icon: "mdi-clipboard-check-outline" });
    }

    if (session.hasRoster) {
      items.push({ title: "Roster", to: "/roster", icon: "mdi-account-group-outline" });
    }
  }

  items.push({ title: "Admin", to: "/admin", icon: "mdi-cog-outline" });

  return items;
});

onMounted(async () => {
  await router.isReady();

  if (!needsLaunch.value) {
    return;
  }

  await session.start();

  // a deep linking launch goes straight to choosing content
  if (session.isDeepLinking && route.name === "launch") {
    await router.replace("/deeplink");
  }
});
</script>
