<template>
  <v-card max-width="460" class="mx-auto" variant="outlined">
    <v-card-item>
      <h2 class="text-h6">Sign in</h2>
      <p class="text-body-2 text-medium-emphasis">
        Use the admin username and password from the tool's settings
        (<code>terracotta.admin.user</code> and <code>terracotta.admin.password</code>).
      </p>
    </v-card-item>
    <v-card-text>
      <v-alert v-if="admin.error" type="error" variant="tonal" class="mb-4" :text="admin.error" />
      <v-form ref="form" @submit.prevent="submit">
        <v-text-field
          v-model="username"
          label="Username"
          autocomplete="username"
          class="mb-2"
          :rules="[required('Enter the username.')]"
        />
        <v-text-field
          v-model="password"
          label="Password"
          type="password"
          autocomplete="current-password"
          class="mb-2"
          :rules="[required('Enter the password.')]"
        />
        <v-btn type="submit" color="primary" block :loading="admin.signingIn">Sign in</v-btn>
      </v-form>
    </v-card-text>
  </v-card>
</template>

<script setup>
import { ref } from "vue";
import { useAdminStore } from "../../stores/admin";
import { required } from "./rules";

const admin = useAdminStore();
const form = ref(null);
const username = ref(admin.username || "");
const password = ref("");

async function submit() {
  const { valid } = await form.value.validate();

  if (!valid) {
    return;
  }

  const entered = password.value;
  password.value = "";
  await admin.signIn(username.value.trim(), entered);
}
</script>
