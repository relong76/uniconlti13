<template>
  <v-card>
    <v-card-item>
      <h2 class="text-h6">{{ platform ? "Edit platform" : "Add a platform" }}</h2>
      <p class="text-body-2 text-medium-emphasis">The values the LMS shows when you register this tool as an LTI 1.3 developer key or app.</p>
    </v-card-item>
    <v-form ref="form" @submit.prevent="submit">
      <v-card-text>
        <v-alert v-if="error" type="error" variant="tonal" class="mb-4" :text="error" />
        <v-text-field
          v-for="field in fields"
          :key="field.key"
          v-model="values[field.key]"
          :label="field.label"
          :hint="field.hint"
          :rules="field.rules"
          :persistent-hint="Boolean(field.hint)"
          class="mb-3"
        />
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn :disabled="saving" @click="emit('cancel')">Cancel</v-btn>
        <v-btn type="submit" color="primary" variant="flat" :loading="saving">{{ platform ? "Save changes" : "Add platform" }}</v-btn>
      </v-card-actions>
    </v-form>
  </v-card>
</template>

<script setup>
import { reactive, ref } from "vue";
import { PLATFORM_FIELDS } from "../../api/admin";
import { httpUrl, required } from "./rules";

const props = defineProps({
  platform: { type: Object, default: null },
  saving: { type: Boolean, default: false },
  error: { type: String, default: null }
});

const emit = defineEmits(["save", "cancel"]);

const fields = [
  { key: "iss", label: "Issuer", hint: "The iss claim the platform sends, such as https://canvas.instructure.com", rules: [required("Enter the issuer."), httpUrl] },
  { key: "clientId", label: "Client ID", rules: [required("Enter the client ID.")] },
  { key: "deploymentId", label: "Deployment ID", rules: [required("Enter the deployment ID.")] },
  { key: "oidcEndpoint", label: "OIDC authorization endpoint", rules: [required("Enter the OIDC authorization endpoint."), httpUrl] },
  { key: "jwksEndpoint", label: "Public keys (JWKS) URL", rules: [required("Enter the public keys (JWKS) URL."), httpUrl] },
  { key: "oAuth2TokenUrl", label: "OAuth 2 token URL (optional)", hint: "Needed for grades, roster and notice services", rules: [httpUrl] },
  { key: "oAuth2TokenAud", label: "OAuth 2 token audience (optional)", hint: "Only if the platform expects an audience other than the token URL, as D2L does", rules: [] }
];

const values = reactive(Object.fromEntries(PLATFORM_FIELDS.map((key) => [key, props.platform?.[key] ?? ""])));
const form = ref(null);

async function submit() {
  const { valid } = await form.value.validate();

  if (valid) {
    emit("save", { ...values });
  }
}
</script>
