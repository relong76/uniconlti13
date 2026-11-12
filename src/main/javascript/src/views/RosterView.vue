<template>
  <v-container class="py-6">
    <div class="mb-4">
      <h1 class="text-h5">Roster</h1>
      <p v-if="courseTitle" class="text-medium-emphasis mb-0">Members of {{ courseTitle }}</p>
    </div>

    <v-alert v-if="!session.token" type="info" variant="tonal" text="Launch the tool from your course to see its roster." />
    <v-alert v-else-if="error" type="error" variant="tonal" title="Couldn't load the roster" :text="error">
      <template #append>
        <v-btn variant="text" @click="load">Try again</v-btn>
      </template>
    </v-alert>
    <div v-else-if="loading" class="text-center py-8" role="status">
      <v-progress-circular indeterminate color="primary" />
      <p class="mt-4 text-medium-emphasis">Loading the roster…</p>
    </div>
    <v-alert v-else-if="!members.length" type="info" variant="tonal" title="No members" text="The platform didn't return any members for this course." />
    <template v-else>
      <div class="d-flex align-center flex-wrap ga-4 mb-4">
        <v-text-field
          v-model="search"
          label="Search members"
          hint="Name, email, user ID or role"
          prepend-inner-icon="mdi-magnify"
          clearable
          hide-details="auto"
          class="search"
        />
        <p class="text-medium-emphasis mb-0" role="status">{{ countLabel }}</p>
      </div>

      <v-card>
        <v-table>
          <caption class="d-sr-only">Course members, sorted by {{ sortLabel }}</caption>
          <thead>
            <tr>
              <th v-for="column in columns" :key="column.key" scope="col" :aria-sort="ariaSort(column.key)">
                <button type="button" class="sort" @click="sortBy(column.key)">
                  {{ column.title }}
                  <v-icon size="small" :icon="sortIcon(column.key)" aria-hidden="true" />
                </button>
              </th>
              <th scope="col">Roles</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!visible.length">
              <td :colspan="columns.length + 1" class="text-medium-emphasis">No members match "{{ search }}".</td>
            </tr>
            <tr v-for="member in visible" :key="member.userId">
              <th scope="row" class="font-weight-regular">{{ member.name || "No name" }}</th>
              <td><code>{{ member.userId }}</code></td>
              <td>{{ member.email }}</td>
              <td>
                <v-chip size="small" :color="member.status === 'Active' ? 'success' : undefined" variant="tonal" label>{{ member.status }}</v-chip>
              </td>
              <td>
                <div class="d-flex flex-wrap ga-1 py-1">
                  <v-chip v-for="role in member.roles" :key="role" size="small" :title="role">{{ roleLabel(role) }}</v-chip>
                </div>
              </td>
            </tr>
          </tbody>
        </v-table>
      </v-card>
    </template>
  </v-container>
</template>

<script setup>
import { computed, ref, watch } from "vue";
import { getRoster } from "../api/advantage";
import { useSessionStore } from "../stores/session";
import { roleLabel } from "../utils/format";

const session = useSessionStore();

const columns = [
  { key: "name", title: "Name" },
  { key: "userId", title: "User ID" },
  { key: "email", title: "Email" },
  { key: "status", title: "Status" }
];

const members = ref([]);
const loading = ref(false);
const error = ref("");
const search = ref("");
const sort = ref({ key: "name", descending: false });

const courseTitle = computed(() => session.launch?.context?.title);

const filtered = computed(() => {
  const term = (search.value || "").trim().toLowerCase();

  if (!term) {
    return members.value;
  }

  return members.value.filter((member) => [member.name, member.email, member.userId, ...member.roles.map(roleLabel)]
    .some((value) => value && value.toLowerCase().includes(term)));
});

const visible = computed(() => {
  const { key, descending } = sort.value;
  const direction = descending ? -1 : 1;

  return [...filtered.value].sort((a, b) => direction * (a[key] || "").localeCompare(b[key] || "", undefined, { sensitivity: "base" }));
});

const countLabel = computed(() => {
  const total = members.value.length;
  const shown = filtered.value.length;
  const noun = total === 1 ? "member" : "members";

  return shown === total ? `${total} ${noun}` : `${shown} of ${total} ${noun}`;
});

const sortLabel = computed(() => {
  const column = columns.find((item) => item.key === sort.value.key);

  return `${column.title.toLowerCase()}, ${sort.value.descending ? "descending" : "ascending"}`;
});

function sortBy(key) {
  sort.value = { key, descending: sort.value.key === key ? !sort.value.descending : false };
}

function ariaSort(key) {
  if (sort.value.key !== key) {
    return "none";
  }

  return sort.value.descending ? "descending" : "ascending";
}

function sortIcon(key) {
  if (sort.value.key !== key) {
    return "mdi-swap-vertical";
  }

  return sort.value.descending ? "mdi-arrow-down" : "mdi-arrow-up";
}

async function load() {
  loading.value = true;
  error.value = "";

  try {
    members.value = await getRoster(session.token);
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

watch(() => session.token, (token) => {
  if (token) {
    void load(); // load() shows its own errors
  }
}, { immediate: true });
</script>

<style scoped>
.search {
  max-width: 24rem;
  min-width: 14rem;
}

.sort {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  font: inherit;
  color: inherit;
  background: none;
  border: 0;
  padding: 0;
  cursor: pointer;
}
</style>
