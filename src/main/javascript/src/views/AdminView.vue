<template>
  <v-container class="py-6">
    <div class="d-flex align-center flex-wrap ga-2 mb-4">
      <h1 class="text-h5 flex-grow-1">Admin</h1>
      <template v-if="admin.signedIn">
        <span class="text-body-2 text-medium-emphasis">Signed in as {{ admin.username }}</span>
        <v-btn variant="outlined" prepend-icon="mdi-logout" @click="admin.signOut()">Sign out</v-btn>
      </template>
    </div>

    <AdminSignIn v-if="!admin.signedIn" />

    <template v-else>
      <v-tabs v-model="tab" color="primary" class="mb-4">
        <v-tab value="platforms" prepend-icon="mdi-server-network">Platforms</v-tab>
        <v-tab value="copies" prepend-icon="mdi-content-copy">Course copies</v-tab>
      </v-tabs>
      <v-window v-model="tab">
        <v-window-item value="platforms">
          <PlatformsPanel />
        </v-window-item>
        <v-window-item value="copies">
          <CourseCopiesPanel />
        </v-window-item>
      </v-window>
    </template>
  </v-container>
</template>

<script setup>
import { ref } from "vue";
import AdminSignIn from "../components/admin/AdminSignIn.vue";
import CourseCopiesPanel from "../components/admin/CourseCopiesPanel.vue";
import PlatformsPanel from "../components/admin/PlatformsPanel.vue";
import { useAdminStore } from "../stores/admin";

const admin = useAdminStore();
const tab = ref("platforms");
</script>
