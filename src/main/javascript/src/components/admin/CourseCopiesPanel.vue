<template>
  <section aria-labelledby="copies-heading">
    <div class="d-flex align-center flex-wrap ga-2 mb-2">
      <h2 id="copies-heading" class="text-h6 flex-grow-1">Course copies</h2>
      <v-btn variant="outlined" prepend-icon="mdi-refresh" :loading="loading" @click="load">Refresh</v-btn>
    </div>
    <p class="text-body-2 text-medium-emphasis mb-4">
      Course copy notices the platforms sent, newest first. A notice arrives when content from a course that used this tool is copied into another course.
    </p>

    <v-alert v-if="error" type="error" variant="tonal" class="mb-4" :text="error" />
    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-2" aria-label="Loading course copies" />

    <v-alert v-if="!loading && !error && !items.length" type="info" variant="tonal">
      No course copies yet. They show up here once a platform with notices turned on sends one.
    </v-alert>

    <template v-if="items.length">
      <v-table aria-labelledby="copies-heading" :aria-busy="loading">
        <thead>
          <tr>
            <th scope="col">Copied into</th>
            <th scope="col">Copied from</th>
            <th scope="col">Received</th>
            <th scope="col">Deployment ID</th>
            <th scope="col">Notice ID</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="notice in items" :key="notice.noticeId">
            <th scope="row" class="font-weight-regular">
              <div>{{ notice.contextTitle || "Untitled course" }}</div>
              <div class="text-caption text-medium-emphasis text-break">{{ notice.contextKey }}</div>
            </th>
            <td class="text-break">
              <div v-for="key in origins(notice)" :key="key">{{ key }}</div>
              <span v-if="!origins(notice).length" class="text-medium-emphasis">Not sent</span>
            </td>
            <td>
              <div>{{ formatDate(notice.createdAt) }}</div>
              <div v-if="notice.noticeTimestamp" class="text-caption text-medium-emphasis">Sent {{ formatDate(notice.noticeTimestamp) }}</div>
            </td>
            <td class="text-break">
              <div>{{ notice.deploymentId || "Not sent" }}</div>
              <div class="text-caption text-medium-emphasis">Platform key ID {{ notice.keyId }}</div>
            </td>
            <td class="text-caption text-break">{{ notice.noticeId }}</td>
          </tr>
        </tbody>
      </v-table>

      <div class="d-flex align-center flex-wrap ga-2 mt-2">
        <span class="text-body-2 text-medium-emphasis flex-grow-1">{{ range }}</span>
        <v-pagination v-if="pages > 1" v-model="page" :length="pages" density="comfortable" total-visible="5" @update:model-value="load" />
      </div>
    </template>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { listCourseCopies } from "../../api/admin";
import { useAdminStore } from "../../stores/admin";

const props = defineProps({
  size: { type: Number, default: 20 }
});

const admin = useAdminStore();
const items = ref([]);
const total = ref(0);
const page = ref(1);
const loading = ref(false);
const error = ref(null);

const pages = computed(() => Math.ceil(total.value / props.size));
const range = computed(() => {
  const first = (page.value - 1) * props.size + 1;

  return `Showing ${first} to ${first + items.value.length - 1} of ${total.value}`;
});

function origins(notice) {
  return (notice.originContextKeys || "").split(",").map((key) => key.trim()).filter(Boolean);
}

function formatDate(value) {
  if (value === null || value === undefined || value === "") {
    return "Unknown";
  }

  const date = new Date(value);

  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString();
}

async function load() {
  loading.value = true;
  error.value = null;

  try {
    const result = await listCourseCopies(admin.credentials, { page: page.value - 1, size: props.size });
    items.value = result.items;
    total.value = result.total;
  } catch (e) {
    if (!admin.failed(e)) {
      error.value = `Couldn't load the course copies: ${e.message}`;
    }
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
