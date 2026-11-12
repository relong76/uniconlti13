<template>
  <v-container class="py-6">
    <h1 class="text-h5 mb-1">Choose content for your course</h1>
    <p class="text-medium-emphasis mb-6">Pick what to add, then send it to your course.</p>

    <v-alert
      v-if="!isDeepLinking"
      type="info"
      variant="tonal"
      title="Not a content selection launch"
      text="This launch isn't for choosing content. To add content from this tool, use your course's option to add external tool content."
    />

    <template v-else>
      <v-card class="mb-6" variant="outlined">
        <v-card-item prepend-icon="mdi-tune-variant">
          <h2 class="text-h6">What your course accepts</h2>
        </v-card-item>
        <v-list density="compact">
          <v-list-item title="Content types" :subtitle="listOrNotStated(settings.accept_types)" />
          <v-list-item title="Opens in" :subtitle="listOrNotStated(settings.accept_presentation_document_targets)" />
          <v-list-item title="More than one item" :subtitle="acceptMultiple ? 'Yes' : 'No, choose one item'" />
          <v-list-item
            title="Added automatically"
            :subtitle="settings.auto_create ? 'Yes' : 'No, you confirm it in your course'"
          />
          <v-list-item v-if="settings.title" title="Suggested title" :subtitle="settings.title" />
          <v-list-item v-if="settings.text" title="Suggested text" :subtitle="settings.text" />
        </v-list>
      </v-card>

      <v-alert
        v-if="!acceptsLinks"
        type="warning"
        variant="tonal"
        class="mb-6"
        text="Your course doesn't accept LTI links here, so content from this tool can't be added."
      />

      <h2 id="available-content" class="text-h6 mb-3">Available content</h2>

      <div v-if="loading" class="py-6 text-center">
        <v-progress-circular indeterminate color="primary" />
        <p class="mt-2 text-medium-emphasis">Loading content…</p>
      </div>

      <v-alert v-else-if="loadError" type="error" variant="tonal" title="Couldn't load the content" :text="loadError" />

      <v-alert v-else-if="!items.length" type="info" variant="tonal" text="There's no content to add yet." />

      <template v-else>
        <v-radio-group
          v-if="!acceptMultiple"
          v-model="selectedOne"
          aria-labelledby="available-content"
          :disabled="sending"
          hide-details
        >
          <v-card v-for="item in items" :key="item.toolLinkId" class="mb-3" variant="outlined">
            <v-card-text class="d-flex align-start ga-2">
              <v-radio :value="item.toolLinkId" :label="titleOf(item)" class="flex-grow-0" />
              <div class="flex-grow-1 pt-3">
                <div class="text-medium-emphasis">{{ item.description }}</div>
                <v-chip class="mt-2" size="small" :color="item.assignment ? 'primary' : undefined" variant="tonal">
                  {{ gradeLabel(item) }}
                </v-chip>
              </div>
            </v-card-text>
          </v-card>
        </v-radio-group>

        <div v-else role="group" aria-labelledby="available-content">
          <v-card v-for="item in items" :key="item.toolLinkId" class="mb-3" variant="outlined">
            <v-card-text class="d-flex align-start ga-2">
              <v-checkbox
                v-model="selectedMany"
                :value="item.toolLinkId"
                :label="titleOf(item)"
                :disabled="sending"
                class="flex-grow-0"
                hide-details
              />
              <div class="flex-grow-1 pt-3">
                <div class="text-medium-emphasis">{{ item.description }}</div>
                <v-chip class="mt-2" size="small" :color="item.assignment ? 'primary' : undefined" variant="tonal">
                  {{ gradeLabel(item) }}
                </v-chip>
              </div>
            </v-card-text>
          </v-card>
        </div>

        <v-alert v-if="sendError" type="error" variant="tonal" class="my-4" title="Couldn't send to your course" :text="sendError" />
        <v-alert v-if="sent" type="success" variant="tonal" class="my-4" text="Sending your content to the course…" />

        <div class="d-flex align-center flex-wrap ga-4 mt-4">
          <v-btn color="primary" prepend-icon="mdi-send" :disabled="!canSend" :loading="sending" @click="send">
            Send to course
          </v-btn>
          <span class="text-medium-emphasis" aria-live="polite">{{ selectionSummary }}</span>
        </div>
      </template>
    </template>
  </v-container>
</template>

<script setup>
import { computed, ref, watch } from "vue";
import { useSessionStore } from "../stores/session";
import { createResponse, getItems, postToPlatform } from "../api/deeplink";

const EXPIRED = "This launch has expired. Launch the tool again from your course.";

const session = useSessionStore();

const items = ref([]);
const loading = ref(false);
const loadError = ref(null);
const selectedOne = ref(null);
const selectedMany = ref([]);
const sending = ref(false);
const sendError = ref(null);
const sent = ref(false);

const isDeepLinking = computed(() => session.isDeepLinking);
const settings = computed(() => session.launch?.deepLinking || {});
const acceptMultiple = computed(() => settings.value.accept_multiple === true);
const acceptsLinks = computed(() => {
  const types = settings.value.accept_types;

  return !Array.isArray(types) || types.includes("ltiResourceLink");
});

const selectedIds = computed(() => {
  if (acceptMultiple.value) {
    return selectedMany.value;
  }

  return selectedOne.value ? [selectedOne.value] : [];
});

const canSend = computed(() => acceptsLinks.value && selectedIds.value.length > 0 && !sent.value);

const selectionSummary = computed(() => {
  const count = selectedIds.value.length;

  if (!count) {
    return "Nothing chosen yet";
  }

  return count === 1 ? "1 item chosen" : `${count} items chosen`;
});

function listOrNotStated(values) {
  return Array.isArray(values) && values.length ? values.join(", ") : "Not stated";
}

function titleOf(item) {
  return item.title || "Untitled";
}

function gradeLabel(item) {
  if (!item.assignment) {
    return "Not graded";
  }

  return item.maxGrade == null ? "Assignment" : `Assignment, ${item.maxGrade} points`;
}

// the API answers errors as problem details, with the reason in "detail"
function messageOf(e) {
  if (e.status === 401) {
    return EXPIRED;
  }

  return e.body?.detail || e.message;
}

async function load() {
  if (!session.token || !isDeepLinking.value) {
    return;
  }

  loading.value = true;
  loadError.value = null;

  try {
    items.value = await getItems(session.token);
  } catch (e) {
    loadError.value = messageOf(e);
  } finally {
    loading.value = false;
  }
}

async function send() {
  sending.value = true;
  sendError.value = null;

  try {
    const { returnUrl, jwt } = await createResponse(session.token, selectedIds.value);
    postToPlatform(returnUrl, jwt);
    sent.value = true;
  } catch (e) {
    sendError.value = messageOf(e);
    sending.value = false;
  }
}

watch(() => [session.token, isDeepLinking.value], load, { immediate: true });
</script>
