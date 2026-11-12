<template>
  <section aria-labelledby="platforms-heading">
    <div class="d-flex align-center flex-wrap ga-2 mb-2">
      <h2 id="platforms-heading" class="text-h6 flex-grow-1">Platforms</h2>
      <v-btn color="primary" prepend-icon="mdi-plus" @click="openAdd">Add platform</v-btn>
    </div>
    <p class="text-body-2 text-medium-emphasis mb-4">The LMS registrations this tool accepts launches from.</p>

    <v-alert v-if="message" type="success" variant="tonal" class="mb-4" closable :text="message" @click:close="message = null" />
    <v-alert v-if="error" type="error" variant="tonal" class="mb-4" :text="error" />
    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-2" aria-label="Loading platforms" />

    <v-alert v-if="!loading && !error && !items.length" type="info" variant="tonal">
      No platforms yet. Add one to accept launches from an LMS.
    </v-alert>

    <template v-if="items.length">
      <v-table aria-labelledby="platforms-heading" :aria-busy="loading">
        <thead>
          <tr>
            <th scope="col">Issuer</th>
            <th scope="col">Client ID</th>
            <th scope="col">Deployment ID</th>
            <th scope="col">Endpoints</th>
            <th scope="col"><span class="text-medium-emphasis">Actions</span></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="platform in items" :key="platform.keyId">
            <th scope="row" class="font-weight-regular">
              <div class="text-break">{{ platform.iss }}</div>
              <div class="text-caption text-medium-emphasis">Key ID {{ platform.keyId }}</div>
            </th>
            <td class="text-break">{{ platform.clientId }}</td>
            <td class="text-break">{{ platform.deploymentId || "Not set" }}</td>
            <td class="text-caption py-2">
              <div v-for="endpoint in endpoints(platform)" :key="endpoint.label" class="text-break">
                <span class="font-weight-medium">{{ endpoint.label }}:</span>
                {{ endpoint.value || "Not set" }}
              </div>
            </td>
            <td class="text-no-wrap">
              <v-btn icon="mdi-pencil-outline" variant="text" size="small" :aria-label="`Edit ${describe(platform)}`" @click="openEdit(platform)" />
              <v-btn icon="mdi-delete-outline" variant="text" size="small" color="error" :aria-label="`Delete ${describe(platform)}`" @click="openDelete(platform)" />
            </td>
          </tr>
        </tbody>
      </v-table>

      <div class="d-flex align-center flex-wrap ga-2 mt-2">
        <span class="text-body-2 text-medium-emphasis flex-grow-1">{{ range }}</span>
        <v-pagination v-if="pages > 1" v-model="page" :length="pages" density="comfortable" total-visible="5" @update:model-value="load" />
      </div>
    </template>

    <v-dialog v-model="editing.open" max-width="640" :persistent="editing.saving">
      <PlatformForm
        v-if="editing.open"
        :platform="editing.platform"
        :saving="editing.saving"
        :error="editing.error"
        @save="save"
        @cancel="editing.open = false"
      />
    </v-dialog>

    <v-dialog v-model="deleting.open" max-width="520" :persistent="deleting.busy">
      <v-card v-if="deleting.platform" role="alertdialog" aria-labelledby="delete-heading">
        <v-card-item>
          <h2 id="delete-heading" class="text-h6">Delete this platform?</h2>
        </v-card-item>
        <v-card-text>
          <v-alert v-if="deleting.error" type="error" variant="tonal" class="mb-4" :text="deleting.error" />
          <p>
            The tool will stop accepting launches from {{ describe(deleting.platform) }}.
            You can't undo this.
          </p>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn :disabled="deleting.busy" @click="deleting.open = false">Cancel</v-btn>
          <v-btn color="error" variant="flat" :loading="deleting.busy" @click="remove">Delete platform</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { createPlatform, deletePlatform, listPlatforms, updatePlatform } from "../../api/admin";
import { useAdminStore } from "../../stores/admin";
import PlatformForm from "./PlatformForm.vue";

const props = defineProps({
  size: { type: Number, default: 10 }
});

const admin = useAdminStore();
const items = ref([]);
const total = ref(0);
const page = ref(1);
const loading = ref(false);
const error = ref(null);
const message = ref(null);
const editing = reactive({ open: false, platform: null, saving: false, error: null });
const deleting = reactive({ open: false, platform: null, busy: false, error: null });

const pages = computed(() => Math.ceil(total.value / props.size));
const range = computed(() => {
  const first = (page.value - 1) * props.size + 1;
  const last = first + items.value.length - 1;

  return `Showing ${first} to ${last} of ${total.value}`;
});

function describe(platform) {
  return `${platform.iss}, client ID ${platform.clientId}, deployment ID ${platform.deploymentId || "not set"}`;
}

function endpoints(platform) {
  const list = [
    { label: "OIDC", value: platform.oidcEndpoint },
    { label: "JWKS", value: platform.jwksEndpoint },
    { label: "Token", value: platform.oAuth2TokenUrl }
  ];

  if (platform.oAuth2TokenAud) {
    list.push({ label: "Token audience", value: platform.oAuth2TokenAud });
  }

  return list;
}

async function load() {
  loading.value = true;
  error.value = null;

  try {
    const result = await listPlatforms(admin.credentials, { page: page.value - 1, size: props.size });

    // the last page can empty out after a delete
    if (!result.items.length && page.value > 1) {
      page.value -= 1;
      await load();
      return;
    }

    items.value = result.items;
    total.value = result.total;
  } catch (e) {
    if (!admin.failed(e)) {
      error.value = `Couldn't load the platforms: ${e.message}`;
    }
  } finally {
    loading.value = false;
  }
}

function openAdd() {
  Object.assign(editing, { open: true, platform: null, saving: false, error: null });
}

function openEdit(platform) {
  Object.assign(editing, { open: true, platform, saving: false, error: null });
}

function openDelete(platform) {
  Object.assign(deleting, { open: true, platform, busy: false, error: null });
}

async function save(values) {
  editing.saving = true;
  editing.error = null;
  const existing = editing.platform;

  try {
    if (existing) {
      await updatePlatform(admin.credentials, existing.keyId, values);
    } else {
      await createPlatform(admin.credentials, values);
    }

    editing.open = false;
    message.value = existing ? "Saved the platform." : "Added the platform.";
    await load();
  } catch (e) {
    if (!admin.failed(e)) {
      editing.error = e.status === 409
        ? "A platform with this issuer, client ID and deployment ID already exists."
        : `Couldn't save the platform: ${e.message}`;
    }
  } finally {
    editing.saving = false;
  }
}

async function remove() {
  deleting.busy = true;
  deleting.error = null;

  try {
    await deletePlatform(admin.credentials, deleting.platform.keyId);
    deleting.open = false;
    message.value = "Deleted the platform.";
    await load();
  } catch (e) {
    if (!admin.failed(e)) {
      // a 409 explains what still uses the platform
      deleting.error = e.status === 409 ? e.message : `Couldn't delete the platform: ${e.message}`;
    }
  } finally {
    deleting.busy = false;
  }
}

onMounted(load);
</script>
