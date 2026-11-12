<template>
  <v-container class="py-6">
    <div class="d-flex align-center flex-wrap ga-4 mb-4">
      <div class="flex-grow-1">
        <h1 class="text-h5">Grades</h1>
        <p v-if="courseTitle" class="text-medium-emphasis mb-0">Line items in {{ courseTitle }}</p>
      </div>
      <v-btn color="primary" prepend-icon="mdi-plus" :disabled="!session.token" @click="openCreate">New line item</v-btn>
    </div>

    <v-alert v-if="notice" type="success" variant="tonal" class="mb-4" closable :text="notice" @click:close="notice = ''" />
    <v-alert v-if="!session.token" type="info" variant="tonal" text="Launch the tool from your course to see its grades." />
    <v-alert v-else-if="error" type="error" variant="tonal" title="Couldn't load the line items" :text="error">
      <template #append>
        <v-btn variant="text" @click="load">Try again</v-btn>
      </template>
    </v-alert>
    <div v-else-if="loading" class="text-center py-8" role="status">
      <v-progress-circular indeterminate color="primary" />
      <p class="mt-4 text-medium-emphasis">Loading line items…</p>
    </div>
    <v-alert
      v-else-if="!lineItems.length"
      type="info"
      variant="tonal"
      title="No line items yet"
      text="Create a line item to add a column to the course gradebook, then post scores to it."
    />
    <v-card v-else>
      <v-table>
        <caption class="d-sr-only">Line items in the course gradebook</caption>
        <thead>
          <tr>
            <th scope="col">Label</th>
            <th scope="col">Max score</th>
            <th scope="col">Tag</th>
            <th scope="col">Resource link</th>
            <th scope="col">Starts</th>
            <th scope="col">Ends</th>
            <th scope="col">Scores</th>
            <th scope="col"><span class="d-sr-only">Actions</span></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in lineItems" :key="item.id">
            <th scope="row" class="font-weight-regular">
              <router-link :to="{ name: 'lineItem', params: { id: item.key } }">{{ item.label || "Untitled" }}</router-link>
            </th>
            <td>{{ item.scoreMaximum }}</td>
            <td>{{ item.tag }}</td>
            <td><code v-if="item.resourceLinkId">{{ item.resourceLinkId }}</code></td>
            <td>{{ formatDateTime(item.startDateTime) }}</td>
            <td>{{ formatDateTime(item.endDateTime) }}</td>
            <td>{{ item.results.length }}</td>
            <td class="text-no-wrap text-right">
              <v-btn icon="mdi-pencil-outline" variant="text" size="small" :aria-label="`Edit ${item.label || 'untitled line item'}`" @click="openEdit(item)" />
              <v-btn icon="mdi-delete-outline" variant="text" size="small" color="error" :aria-label="`Delete ${item.label || 'untitled line item'}`" @click="pendingDelete = item" />
            </td>
          </tr>
        </tbody>
      </v-table>
    </v-card>

    <v-dialog v-model="formOpen" max-width="560" persistent>
      <v-card :title="editing ? 'Edit line item' : 'New line item'">
        <v-form ref="formRef" @submit.prevent="save">
          <v-card-text>
            <v-alert v-if="formError" type="error" variant="tonal" class="mb-4" :text="formError" />
            <v-text-field v-model="form.label" label="Label" :rules="[required]" autofocus />
            <v-text-field v-model="form.scoreMaximum" label="Maximum score" type="number" min="0" step="any" :rules="[positive]" />
            <v-text-field v-model="form.tag" label="Tag" hint="Optional. Lets the tool find this line item again, for example &quot;quiz&quot;." persistent-hint class="mb-2" />
            <v-text-field v-model="form.resourceId" label="Resource ID" hint="Optional. The tool's own ID for the activity." persistent-hint class="mb-2" />
            <v-text-field v-model="form.resourceLinkId" label="Resource link ID" hint="Optional. Ties the line item to a link in the course." persistent-hint class="mb-2" />
            <v-row>
              <v-col cols="12" sm="6">
                <v-text-field v-model="form.startDateTime" label="Starts" type="datetime-local" />
              </v-col>
              <v-col cols="12" sm="6">
                <v-text-field v-model="form.endDateTime" label="Ends" type="datetime-local" />
              </v-col>
            </v-row>
          </v-card-text>
          <v-card-actions>
            <v-spacer />
            <v-btn variant="text" :disabled="saving" @click="formOpen = false">Cancel</v-btn>
            <v-btn type="submit" color="primary" variant="flat" :loading="saving">{{ editing ? "Save changes" : "Create line item" }}</v-btn>
          </v-card-actions>
        </v-form>
      </v-card>
    </v-dialog>

    <v-dialog :model-value="Boolean(pendingDelete)" max-width="440" persistent>
      <v-card title="Delete line item?">
        <v-card-text>
          <v-alert v-if="deleteError" type="error" variant="tonal" class="mb-4" :text="deleteError" />
          Deleting "{{ pendingDelete?.label }}" removes it and its scores from the course gradebook. This can't be undone.
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" :disabled="deleting" @click="cancelDelete">Cancel</v-btn>
          <v-btn color="error" variant="flat" :loading="deleting" @click="confirmDelete">Delete</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-container>
</template>

<script setup>
import { computed, reactive, ref, watch } from "vue";
import { createLineItem, deleteLineItem, listLineItems, updateLineItem } from "../api/advantage";
import { useSessionStore } from "../stores/session";
import { formatDateTime, fromLocalInput, toLocalInput } from "../utils/dates";

const session = useSessionStore();

const lineItems = ref([]);
const loading = ref(false);
const error = ref("");
const notice = ref("");

const formOpen = ref(false);
const formRef = ref(null);
const editing = ref(null);
const saving = ref(false);
const formError = ref("");
const form = reactive(emptyForm());

const pendingDelete = ref(null);
const deleting = ref(false);
const deleteError = ref("");

const courseTitle = computed(() => session.launch?.context?.title);

const required = (value) => Boolean(value && value.trim()) || "Enter a label.";
const positive = (value) => Number(value) > 0 || "Enter a maximum score greater than 0.";

function emptyForm() {
  return { label: "", scoreMaximum: "100", tag: "", resourceId: "", resourceLinkId: "", startDateTime: "", endDateTime: "" };
}

async function load() {
  loading.value = true;
  error.value = "";

  try {
    lineItems.value = await listLineItems(session.token);
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editing.value = null;
  Object.assign(form, emptyForm(), { resourceLinkId: session.launch?.resourceLink?.id || "" });
  formError.value = "";
  formOpen.value = true;
}

function openEdit(item) {
  editing.value = item;
  Object.assign(form, {
    label: item.label || "",
    scoreMaximum: item.scoreMaximum || "",
    tag: item.tag || "",
    resourceId: item.resourceId || "",
    resourceLinkId: item.resourceLinkId || "",
    startDateTime: toLocalInput(item.startDateTime),
    endDateTime: toLocalInput(item.endDateTime)
  });
  formError.value = "";
  formOpen.value = true;
}

async function save() {
  const { valid } = await formRef.value.validate();

  if (!valid) {
    return;
  }

  const body = {
    label: form.label.trim(),
    scoreMaximum: String(form.scoreMaximum),
    tag: form.tag.trim() || null,
    resourceId: form.resourceId.trim() || null,
    resourceLinkId: form.resourceLinkId.trim() || null,
    startDateTime: fromLocalInput(form.startDateTime),
    endDateTime: fromLocalInput(form.endDateTime)
  };

  saving.value = true;
  formError.value = "";

  try {
    if (editing.value) {
      await updateLineItem(session.token, editing.value.key, body);
      notice.value = `Saved "${body.label}".`;
    } else {
      await createLineItem(session.token, body);
      notice.value = `Created "${body.label}".`;
    }
    formOpen.value = false;
    await load();
  } catch (e) {
    formError.value = e.message;
  } finally {
    saving.value = false;
  }
}

function cancelDelete() {
  pendingDelete.value = null;
  deleteError.value = "";
}

async function confirmDelete() {
  const item = pendingDelete.value;
  deleting.value = true;
  deleteError.value = "";

  try {
    await deleteLineItem(session.token, item.key);
    pendingDelete.value = null;
    notice.value = `Deleted "${item.label}".`;
    await load();
  } catch (e) {
    deleteError.value = e.message;
  } finally {
    deleting.value = false;
  }
}

watch(() => session.token, (token) => {
  if (token) {
    void load(); // load() shows its own errors
  }
}, { immediate: true });
</script>

