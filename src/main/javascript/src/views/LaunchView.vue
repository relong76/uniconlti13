<template>
  <v-container v-if="launch" class="py-6">
    <div class="d-flex align-center flex-wrap ga-4 mb-6">
      <v-avatar size="56" color="secondary">
        <v-img v-if="launch.user.picture" :src="launch.user.picture" :alt="launch.user.name || 'User'" />
        <span v-else class="text-h6">{{ initials }}</span>
      </v-avatar>
      <div class="flex-grow-1">
        <h1 class="text-h5">{{ launch.user.name || "Unknown user" }}</h1>
        <div class="text-medium-emphasis">{{ launch.user.email }}</div>
      </div>
      <v-chip color="primary" variant="flat" label>{{ launch.messageType }}</v-chip>
      <v-chip variant="outlined" label>LTI {{ launch.version }}</v-chip>
    </div>

    <v-row>
      <v-col cols="12" md="6">
        <v-card title="Course" prepend-icon="mdi-school-outline" class="h-100">
          <v-card-text v-if="launch.context">
            <div class="text-subtitle-1 font-weight-medium">{{ launch.context.title }}</div>
            <div class="text-medium-emphasis">{{ launch.context.label }}</div>
            <div class="text-caption mt-2">Context ID: <code>{{ launch.context.id }}</code></div>
          </v-card-text>
          <v-card-text v-else class="text-medium-emphasis">The launch didn't include a course.</v-card-text>
        </v-card>
      </v-col>

      <v-col cols="12" md="6">
        <v-card title="Your roles" prepend-icon="mdi-badge-account-outline" class="h-100">
          <v-card-text>
            <div v-if="launch.user.roles.length" class="d-flex flex-wrap ga-2">
              <v-chip v-for="role in launch.user.roles" :key="role" size="small" :title="role">
                {{ roleLabel(role) }}
                <span v-if="roleScope(role)" class="text-medium-emphasis ml-1">({{ roleScope(role) }})</span>
              </v-chip>
            </div>
            <span v-else class="text-medium-emphasis">No roles were sent.</span>
          </v-card-text>
        </v-card>
      </v-col>

      <v-col cols="12" md="6">
        <v-card title="Resource link" prepend-icon="mdi-link-variant" class="h-100">
          <v-card-text v-if="launch.resourceLink">
            <div class="text-subtitle-1 font-weight-medium">{{ launch.resourceLink.title || "Untitled" }}</div>
            <div v-if="launch.resourceLink.description" class="text-medium-emphasis">{{ launch.resourceLink.description }}</div>
            <div class="text-caption mt-2">Resource link ID: <code>{{ launch.resourceLink.id }}</code></div>
          </v-card-text>
          <v-card-text v-else class="text-medium-emphasis">This launch isn't for a resource link.</v-card-text>
        </v-card>
      </v-col>

      <v-col cols="12" md="6">
        <v-card title="Requested content" prepend-icon="mdi-file-document-outline" class="h-100">
          <v-card-text v-if="launch.content">
            <div class="text-subtitle-1 font-weight-medium">{{ launch.content.title }}</div>
            <div class="text-medium-emphasis">{{ launch.content.description }}</div>
            <div class="mt-2 d-flex ga-2">
              <v-chip v-if="launch.content.assignment" size="small" color="primary" variant="tonal">
                Assignment, {{ launch.content.maxGrade }} points
              </v-chip>
              <v-chip size="small" variant="outlined">{{ launch.content.toolLinkId }}</v-chip>
            </div>
          </v-card-text>
          <v-card-text v-else class="text-medium-emphasis">
            The launch didn't ask for demo content. Content chosen through deep linking opens here.
          </v-card-text>
        </v-card>
      </v-col>

      <v-col cols="12" md="6">
        <v-card title="Platform" prepend-icon="mdi-domain" class="h-100">
          <v-list density="compact">
            <v-list-item title="Issuer" :subtitle="launch.issuer" />
            <v-list-item title="Client ID" :subtitle="launch.clientId" />
            <v-list-item title="Deployment ID" :subtitle="launch.deploymentId" />
            <v-list-item v-if="launch.platform" title="Platform" :subtitle="platformName" />
          </v-list>
        </v-card>
      </v-col>

      <v-col cols="12" md="6">
        <v-card title="LTI Advantage services" prepend-icon="mdi-puzzle-outline" class="h-100">
          <v-list density="compact">
            <v-list-item
              title="Assignment and Grade Services"
              :subtitle="launch.services.assignmentsAndGrades ? 'Available' : 'Not offered on this launch'"
            >
              <template v-if="launch.services.assignmentsAndGrades" #append>
                <v-btn to="/grades" size="small" variant="tonal">Grades</v-btn>
              </template>
            </v-list-item>
            <v-list-item
              title="Names and Role Provisioning"
              :subtitle="launch.services.namesAndRoles ? 'Available' : 'Not offered on this launch'"
            >
              <template v-if="launch.services.namesAndRoles" #append>
                <v-btn to="/roster" size="small" variant="tonal">Roster</v-btn>
              </template>
            </v-list-item>
            <v-list-item
              title="Platform Notification Service"
              :subtitle="launch.services.platformNotifications ? 'Available' : 'Not offered on this launch'"
            />
          </v-list>
        </v-card>
      </v-col>

      <v-col v-if="customParameters.length" cols="12">
        <v-card title="Custom parameters" prepend-icon="mdi-tune-variant">
          <v-table density="compact">
            <thead>
              <tr><th scope="col">Name</th><th scope="col">Value</th></tr>
            </thead>
            <tbody>
              <tr v-for="row in customParameters" :key="row.key">
                <td><code>{{ row.key }}</code></td>
                <td>{{ row.value }}</td>
              </tr>
            </tbody>
          </v-table>
        </v-card>
      </v-col>

      <v-col cols="12">
        <v-expansion-panels>
          <v-expansion-panel title="All claims in the id_token">
            <v-expansion-panel-text>
              <pre class="claims">{{ prettyJson(launch.claims) }}</pre>
            </v-expansion-panel-text>
          </v-expansion-panel>
        </v-expansion-panels>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import { computed } from "vue";
import { useSessionStore } from "../stores/session";
import { entries, prettyJson, roleLabel, roleScope } from "../utils/format";

const session = useSessionStore();
const launch = computed(() => session.launch);

const initials = computed(() => {
  const name = launch.value?.user?.name || "";
  const letters = name.split(/\s+/).filter(Boolean).map((part) => part[0]).join("");

  return letters.slice(0, 2).toUpperCase() || "?";
});

const platformName = computed(() => {
  const platform = launch.value?.platform || {};

  return [platform.name, platform.product_family_code, platform.version].filter(Boolean).join(" · ");
});

const customParameters = computed(() => entries(launch.value?.custom));
</script>

<style scoped>
.claims {
  font-size: 0.8rem;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 32rem;
  overflow: auto;
}
</style>
