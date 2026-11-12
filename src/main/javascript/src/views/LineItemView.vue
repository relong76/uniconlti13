<template>
  <v-container class="py-6">
    <v-btn :to="{ name: 'grades' }" variant="text" prepend-icon="mdi-arrow-left" class="mb-2 px-0">All line items</v-btn>

    <v-alert v-if="!session.token" type="info" variant="tonal" text="Launch the tool from your course to see its grades." />
    <v-alert v-else-if="error" type="error" variant="tonal" title="Couldn't load the line item" :text="error">
      <template #append>
        <v-btn variant="text" @click="load">Try again</v-btn>
      </template>
    </v-alert>
    <div v-else-if="loading" class="text-center py-8" role="status">
      <v-progress-circular indeterminate color="primary" />
      <p class="mt-4 text-medium-emphasis">Loading the line item…</p>
    </div>
    <template v-else-if="lineItem">
      <h1 class="text-h5 mb-4">{{ lineItem.label || "Untitled line item" }}</h1>

      <v-row>
        <v-col cols="12" md="5">
          <v-card class="h-100">
            <v-card-item><h2 class="text-h6">Details</h2></v-card-item>
            <v-table density="compact">
              <tbody>
                <tr v-for="row in details" :key="row.label">
                  <th scope="row">{{ row.label }}</th>
                  <td class="text-break">{{ row.value || "Not set" }}</td>
                </tr>
              </tbody>
            </v-table>
          </v-card>
        </v-col>

        <v-col cols="12" md="7">
          <v-card class="h-100">
            <v-card-item><h2 class="text-h6">Post a score</h2></v-card-item>
            <v-form ref="formRef" @submit.prevent="submit">
              <v-card-text>
                <v-alert v-if="notice" type="success" variant="tonal" class="mb-4" :text="notice" />
                <v-alert v-if="formError" type="error" variant="tonal" class="mb-4" :text="formError" />
                <v-alert v-if="rosterError" type="warning" variant="tonal" density="compact" class="mb-4" :text="rosterError" />

                <v-select
                  v-if="useRoster"
                  v-model="score.userId"
                  :items="memberItems"
                  label="User"
                  :rules="[requiredUser]"
                />
                <v-text-field v-else v-model="score.userId" label="User ID" :rules="[requiredUser]" />
                <v-checkbox
                  v-if="members.length"
                  v-model="enterId"
                  label="Enter a user ID instead"
                  density="compact"
                  hide-details
                  class="mb-2"
                />

                <v-row>
                  <v-col cols="12" sm="6">
                    <v-text-field v-model="score.scoreGiven" label="Score" type="number" step="any" :rules="[number]" hint="Leave blank to send progress only." persistent-hint />
                  </v-col>
                  <v-col cols="12" sm="6">
                    <v-text-field v-model="score.scoreMaximum" label="Out of" type="number" min="0" step="any" :rules="[positiveOrBlank]" />
                  </v-col>
                </v-row>
                <v-textarea v-model="score.comment" label="Comment" rows="2" auto-grow />
                <v-row>
                  <v-col cols="12" sm="6">
                    <v-select v-model="score.activityProgress" :items="activityItems" label="Activity progress" />
                  </v-col>
                  <v-col cols="12" sm="6">
                    <v-select v-model="score.gradingProgress" :items="gradingItems" label="Grading progress" />
                  </v-col>
                </v-row>
              </v-card-text>
              <v-card-actions>
                <v-spacer />
                <v-btn type="submit" color="primary" variant="flat" :loading="posting">Post score</v-btn>
              </v-card-actions>
            </v-form>
          </v-card>
        </v-col>
      </v-row>

      <h2 class="text-h6 mt-6 mb-2">Results</h2>
      <v-alert v-if="!lineItem.results.length" type="info" variant="tonal" text="No scores have been posted to this line item yet." />
      <v-card v-else>
        <v-table>
          <caption class="d-sr-only">Results for {{ lineItem.label }}</caption>
          <thead>
            <tr>
              <th scope="col">User</th>
              <th scope="col">Score</th>
              <th scope="col">Comment</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="result in lineItem.results" :key="result.id || result.userId">
              <th scope="row" class="font-weight-regular">
                <div>{{ memberName(result.userId) || result.userId }}</div>
                <div v-if="memberName(result.userId)" class="text-caption text-medium-emphasis"><code>{{ result.userId }}</code></div>
              </th>
              <td>{{ scoreLabel(result) }}</td>
              <td>{{ result.comment }}</td>
            </tr>
          </tbody>
        </v-table>
      </v-card>
    </template>
  </v-container>
</template>

<script setup>
import { computed, reactive, ref, watch } from "vue";
import { ACTIVITY_PROGRESS, GRADING_PROGRESS, getLineItem, getRoster, postScore } from "../api/advantage";
import { useSessionStore } from "../stores/session";
import { formatDateTime } from "../utils/dates";
import { roleLabel } from "../utils/format";

const props = defineProps({
  id: { type: String, required: true }
});

const PROGRESS_LABELS = {
  Initialized: "Initialized",
  Started: "Started",
  InProgress: "In progress",
  Submitted: "Submitted",
  Completed: "Completed",
  FullyGraded: "Fully graded",
  Pending: "Pending",
  PendingManual: "Pending manual grading",
  Failed: "Failed",
  NotReady: "Not ready"
};

const session = useSessionStore();

const lineItem = ref(null);
const loading = ref(false);
const error = ref("");

const members = ref([]);
const rosterError = ref("");
const enterId = ref(false);

const formRef = ref(null);
const posting = ref(false);
const formError = ref("");
const notice = ref("");
const score = reactive(emptyScore());

const activityItems = ACTIVITY_PROGRESS.map((value) => ({ value, title: PROGRESS_LABELS[value] }));
const gradingItems = GRADING_PROGRESS.map((value) => ({ value, title: PROGRESS_LABELS[value] }));

const useRoster = computed(() => members.value.length > 0 && !enterId.value);

const memberItems = computed(() => members.value.map((member) => ({
  value: member.userId,
  title: `${member.name || member.userId} (${member.roles.map(roleLabel).join(", ") || "no role"})`
})));

const details = computed(() => [
  { label: "Line item ID", value: lineItem.value.id },
  { label: "Max score", value: lineItem.value.scoreMaximum },
  { label: "Tag", value: lineItem.value.tag },
  { label: "Resource ID", value: lineItem.value.resourceId },
  { label: "Resource link ID", value: lineItem.value.resourceLinkId },
  { label: "Starts", value: formatDateTime(lineItem.value.startDateTime) },
  { label: "Ends", value: formatDateTime(lineItem.value.endDateTime) }
]);

const requiredUser = (value) => Boolean(value && String(value).trim()) || "Choose a user.";
const number = (value) => value === "" || value === null || value === undefined || Number.isFinite(Number(value)) || "Enter the score as a number.";
const positiveOrBlank = (value) => value === "" || value === null || value === undefined || Number(value) > 0 || "Enter a maximum score greater than 0.";

function emptyScore() {
  return { userId: "", scoreGiven: "", scoreMaximum: "", comment: "", activityProgress: "Completed", gradingProgress: "FullyGraded" };
}

function memberName(userId) {
  return members.value.find((member) => member.userId === userId)?.name;
}

function scoreLabel(result) {
  if (result.resultScore === null || result.resultScore === undefined || result.resultScore === "") {
    return "No score";
  }

  return result.resultMaximum ? `${result.resultScore} of ${result.resultMaximum}` : String(result.resultScore);
}

async function loadRoster() {
  rosterError.value = "";

  if (!session.hasRoster) {
    members.value = [];
    return;
  }

  try {
    members.value = await getRoster(session.token);
  } catch (e) {
    members.value = [];
    rosterError.value = `The roster isn't available, so enter a user ID. ${e.message}`;
  }
}

async function load() {
  loading.value = true;
  error.value = "";

  try {
    const [item] = await Promise.all([getLineItem(session.token, props.id), loadRoster()]);
    lineItem.value = item;
    score.scoreMaximum = item.scoreMaximum || "";
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

async function submit() {
  const { valid } = await formRef.value.validate();

  if (!valid) {
    return;
  }

  const given = String(score.scoreGiven ?? "").trim();
  const body = {
    userId: String(score.userId).trim(),
    scoreGiven: given || null,
    scoreMaximum: given ? String(score.scoreMaximum || lineItem.value.scoreMaximum) : null,
    comment: score.comment,
    activityProgress: score.activityProgress,
    gradingProgress: score.gradingProgress
  };

  posting.value = true;
  formError.value = "";
  notice.value = "";

  try {
    lineItem.value = await postScore(session.token, props.id, body);
    notice.value = `Posted the score for ${memberName(body.userId) || body.userId}.`;
    Object.assign(score, emptyScore(), { scoreMaximum: lineItem.value.scoreMaximum || "" });
    formRef.value.resetValidation();
  } catch (e) {
    formError.value = e.message;
  } finally {
    posting.value = false;
  }
}

watch([() => session.token, () => props.id], ([token]) => {
  if (token) {
    void load(); // load() shows its own errors
  }
}, { immediate: true });
</script>
