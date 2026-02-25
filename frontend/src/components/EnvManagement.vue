<template>
  <ScrollPanel class="w-full h-full">
    <div class="p-8 max-w-300 mx-auto">
      <Button icon="pi pi-arrow-left" label="Back" @click="$emit('back')" text />
      <!-- Toolbar -->
      <div class="flex gap-8">
        <Card class="mb-8 flex-1">
          <template #title>Environment Management</template>
          <template #content>
            <div class="flex justify-between items-center gap-2">
              <Select v-model="selectedEnvFile" :options="envFiles" placeholder="Select Env Config" class="w-68" @change="handleLoadEnv" />
              <Button label="Save" icon="pi pi-save" @click="handleSaveEnv" :disabled="!form.name" />
              <Button label="Generate" icon="pi pi-cog" severity="secondary" @click="handleGenerateConfig" :disabled="!form.name" />
            </div>
          </template>
        </Card>

        <!-- Project Name & Global Options -->
        <Card class="mb-8 flex-1">
          <template #title>Project Name</template>
          <template #content>
            <div class="flex justify-between items-center gap-2">
              <InputText v-model="form.name" placeholder="e.g. my-project" class="w-64" />
              <div class="flex items-center gap-2">
                <ToggleSwitch v-model="form.separateCodePath" inputId="separateCode" />
                <label for="separateCode">Separate Source codebase</label>
              </div>
            </div>
          </template>
        </Card>
      </div>

      <!-- Environments -->
      <div class="flex gap-8">
        <!-- Target Config (Main/Default) -->
        <Card class="mb-8 flex-1">
          <template #title>
            <div class="flex justify-between items-center">
              <span>{{ form.separateCodePath ? 'Target Environment' : 'Project Configuration (Target)' }}</span>
              <div class="flex gap-2 items-center">
                <Button v-if="!targetStatus" label="Start" icon="pi pi-play" size="small" severity="success" @click="handleStart('target')" :loading="loadingTarget" />
                <Button v-else label="Stop" icon="pi pi-stop" size="small" severity="danger" @click="handleStop('target')" :loading="loadingTarget" />
              </div>
            </div>
          </template>
          <template #content>
            <div class="flex flex-col gap-4">
              <div class="flex flex-col gap-2">
                <label class="text-sm font-medium text-gray-600">Docker Compose Path</label>
                <InputGroup>
                    <InputText v-model="form.target.composePath" placeholder="/path/to/docker-compose.yml" />
                    <Button icon="pi pi-file" @click="openFileSelector('target', 'file')" />
                </InputGroup>
              </div>
              <div class="flex flex-col gap-2">
                <label class="text-sm font-medium text-gray-600">Code Path</label>
                <InputGroup>
                  <InputText v-model="form.target.codePath" placeholder="/path/to/codebase" @change="fetchGitStatus('target')" />
                  <Button icon="pi pi-folder-open" @click="openFileSelector('target', 'directory')" />
                </InputGroup>
                <small v-if="targetGitStatus" class="text-gray-500 flex gap-2 items-center">
                    <i class="pi pi-code"></i> {{ targetGitStatus.branch }} 
                    <span v-if="targetGitStatus.isDirty" class="text-orange-500">(Dirty)</span>
                    <span v-if="targetGitStatus.error" class="text-red-500">{{ targetGitStatus.error }}</span>
                  </small>
              </div>
              <div class="flex gap-4">
                <div class="flex flex-col gap-2 flex-1">
                  <label class="text-sm font-medium text-gray-600">Service Name</label>
                  <InputText v-model="form.target.serviceName" placeholder="mysql" fluid />
                </div>
                <div class="flex flex-col gap-2 flex-1">
                  <label class="text-sm font-medium text-gray-600">Container Name</label>
                  <InputText v-model="form.target.containerPrefix" placeholder="project_target" fluid />
                </div>
              </div>
              <div class="flex gap-4">
                <div class="flex flex-col gap-2 flex-1">
                  <label class="text-sm font-medium text-gray-600">Port (Host)</label>
                  <InputNumber v-model="form.target.port" :useGrouping="false" fluid />
                </div>
                <div class="flex flex-col gap-2 flex-1">
                  <label class="text-sm font-medium text-gray-600">Git Ref (Branch/Tag)</label>
                  <InputText v-model="form.target.gitRef" placeholder="master" fluid />
                </div>
              </div>
              <div class="flex flex-col gap-2">
                <label class="text-sm font-medium text-gray-600">Exclude Init SQL (Comma separated)</label>
                <AutoComplete v-model="form.target.excludeInitSql" multiple :typeahead="false" placeholder="e.g. test_data.sql" fluid />
              </div>
              <div class="flex gap-4">
                <div class="flex flex-col gap-2 flex-1">
                  <label class="text-sm font-medium text-gray-600">DB Username</label>
                  <InputText v-model="form.target.dbConfig.username" fluid />
                </div>
                <div class="flex flex-col gap-2 flex-1">
                  <label class="text-sm font-medium text-gray-600">DB Password</label>
                  <Password v-model="form.target.dbConfig.password" :feedback="false" toggleMask fluid />
                </div>
              </div>
              <div class="flex flex-col gap-2">
                <label class="text-sm font-medium text-gray-600">Use Database</label>
                <InputText v-model="form.target.dbConfig.database" placeholder="DB Name" class="flex-1" />
              </div>
            </div>
          </template>
        </Card>

        <!-- Source Config -->
        <Card class="mb-8 flex-1">
          <template #title>
            <div class="flex justify-between items-center">
              <span>{{ form.separateCodePath ? 'Source Environment' : 'Source Configuration' }}</span>
              <div class="flex gap-2 items-center">
                <Button v-if="!sourceStatus" label="Start" icon="pi pi-play" size="small" severity="success" @click="handleStart('source')" :loading="loadingSource" />
                <Button v-else label="Stop" icon="pi pi-stop" size="small" severity="danger" @click="handleStop('source')" :loading="loadingSource" />
              </div>
            </div>
          </template>
          <template #content>
            <div class="flex flex-col gap-4">
              <div v-if="form.separateCodePath" class="flex flex-col gap-4">
                <div class="flex flex-col gap-2">
                  <label class="text-sm font-medium text-gray-600">Docker Compose Path</label>
                  <InputGroup>
                      <InputText v-model="form.source.composePath" placeholder="/path/to/docker-compose.yml" />
                      <Button icon="pi pi-file" @click="openFileSelector('source', 'file')" />
                  </InputGroup>
                </div>
                <div class="flex flex-col gap-2">
                  <label class="text-sm font-medium text-gray-600">Code Path</label>
                  <InputGroup>
                    <InputText v-model="form.source.codePath" placeholder="/path/to/codebase" @change="fetchGitStatus('source')" />
                    <Button icon="pi pi-folder-open" @click="openFileSelector('source', 'directory')" />
                  </InputGroup>
                  <small v-if="sourceGitStatus" class="text-gray-500 flex gap-2 items-center">
                      <i class="pi pi-code"></i> {{ sourceGitStatus.branch }} 
                      <span v-if="sourceGitStatus.isDirty" class="text-orange-500">(Dirty)</span>
                      <span v-if="sourceGitStatus.error" class="text-red-500">{{ sourceGitStatus.error }}</span>
                  </small>
                </div>
                <div class="flex gap-4">
                  <div class="flex flex-col gap-2 flex-1">
                    <label class="text-sm font-medium text-gray-600">Service Name</label>
                    <InputText v-model="form.source.serviceName" placeholder="mysql" fluid />
                  </div>
                  <div class="flex flex-col gap-2 flex-1">
                    <label class="text-sm font-medium text-gray-600">Container Name</label>
                    <InputText v-model="form.source.containerPrefix" placeholder="project_source" fluid />
                  </div>
                </div>
              </div>
                  
              <!-- Fields always visible for Source -->
              <div class="flex gap-4" v-if="!form.separateCodePath">
                <div class="flex flex-col gap-2 flex-1">
                  <label class="text-sm font-medium text-gray-600">Container Name</label>
                  <InputText v-model="form.source.containerPrefix" placeholder="project_source" fluid />
                </div>
              </div>
              <div class="flex gap-4">
                <div class="flex flex-col gap-2 flex-1">
                  <label class="text-sm font-medium text-gray-600">Port (Host)</label>
                  <InputNumber v-model="form.source.port" :useGrouping="false" fluid />
                </div>
                <div class="flex flex-col gap-2 flex-1">
                  <label class="text-sm font-medium text-gray-600">Git Ref (Branch/Tag)</label>
                  <InputText v-model="form.source.gitRef" placeholder="master" fluid />
                </div>
              </div>
              <div v-if="form.separateCodePath" class="flex flex-col gap-2">
                <label class="text-sm font-medium text-gray-600">Exclude Init SQL (Comma separated)</label>
                <AutoComplete v-model="form.source.excludeInitSql" multiple :typeahead="false" placeholder="e.g. test_data.sql" fluid />
              </div>
              <div class="flex gap-4">
                <div class="flex flex-col gap-2 flex-1">
                  <label class="text-sm font-medium text-gray-600">DB Username</label>
                  <InputText v-model="form.source.dbConfig.username" fluid />
                </div>
                <div class="flex flex-col gap-2 flex-1">
                  <label class="text-sm font-medium text-gray-600">DB Password</label>
                  <Password v-model="form.source.dbConfig.password" :feedback="false" toggleMask fluid />
                </div>
              </div>
              <div class="flex flex-col gap-2">
                <label class="text-sm font-medium text-gray-600">Use Database</label>
                <div class="flex gap-2">
                  <InputText v-model="form.source.dbConfig.database" placeholder="DB Name" class="flex-1" />
                </div>
              </div>
            </div>
          </template>
        </Card>
      </div>

      <!-- Options Section -->
      <Card>
        <template #title>Project Options</template>
        <template #content>
          <div class="flex flex-col gap-4">
            <div class="flex flex-col gap-2">
              <label class="text-sm font-medium text-gray-600">Ignore Fields</label>
              <AutoComplete v-model="form.ignoreFields" multiple :typeahead="false" placeholder="for global: column_name, for single: table.col" fluid />
            </div>
            <div class="flex flex-col gap-2">
              <label class="text-sm font-medium text-gray-600">Exclude Tables</label>
              <AutoComplete v-model="form.excludeTables" multiple :typeahead="false" placeholder="table_name" fluid />
            </div>
            <div class="flex flex-col gap-2">
              <label class="text-sm font-medium text-gray-600">Ignore Data</label>
              <AutoComplete v-model="form.ignoreDataTables" multiple :typeahead="false" placeholder="table_name" fluid />
            </div>
            <div class="flex flex-col gap-2">
              <label class="text-sm font-medium text-gray-600">Specified Primary Keys</label>
              <AutoComplete v-model="form.specifiedPrimaryKeys" multiple :typeahead="false" placeholder="table(col), table(col1,col2)" fluid />
            </div>
            <div class="flex flex-col gap-2">
              <label class="text-sm font-medium text-gray-600">Exclude Data Rows</label>
              <AutoComplete v-model="form.excludeDataRows" multiple :typeahead="false" placeholder="table(col=val), table(col1#col2=val1#val2)" fluid />
            </div>
            <div class="flex flex-col gap-2">
              <label class="text-sm font-medium text-gray-600">Include Data Rows</label>
              <AutoComplete v-model="form.includeDataRows" multiple :typeahead="false" placeholder="table(col=val), table(col1#col2=val1#val2)" fluid />
            </div>
            <div class="flex flex-col gap-2">
              <label class="text-sm font-medium text-gray-600">Specified Data Queries</label>
              <AutoComplete v-model="form.specifiedDataQueries" multiple :typeahead="false" placeholder="table=select * from table where ..." fluid />
            </div>
          </div>
        </template>
      </Card>
    </div>
  </ScrollPanel>
    
  <FileSelector ref="fileSelector" @select="handleFileSelect" />
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, watch } from 'vue';
import { useToast } from 'primevue/usetoast';
import { listEnvs, loadEnv, saveEnv, generateConfig, startDocker, stopDocker, getDockerStatus, getDockerStatusStreamUrl, getGitStatus, findGitRoot, type EnvConfig, type DockerParams, type GitStatus } from '../api';
import InputText from 'primevue/inputtext';
import InputNumber from 'primevue/inputnumber';
import Password from 'primevue/password';
import Button from 'primevue/button';
import ScrollPanel from 'primevue/scrollpanel';
import AutoComplete from 'primevue/autocomplete';
import Card from 'primevue/card';
import Select from 'primevue/select';
import InputGroup from 'primevue/inputgroup';
import ToggleSwitch from 'primevue/toggleswitch';
import FileSelector from './FileSelector.vue';

defineEmits(['back']);

const toast = useToast();
const envFiles = ref<string[]>([]);
const selectedEnvFile = ref<string | null>(null);
const loadingSource = ref(false);
const loadingTarget = ref(false);
const fileSelector = ref();
const activeSelectorType = ref<'source' | 'target'>('target');

const sourceStatus = ref<boolean | null>(null);
const targetStatus = ref<boolean | null>(null);
const sourceGitStatus = ref<GitStatus | null>(null);
const targetGitStatus = ref<GitStatus | null>(null);

const form = reactive<EnvConfig>({
  name: '',
  separateCodePath: false,
  source: {
    codePath: '',
    composePath: '',
    serviceName: 'mysql',
    excludeInitSql: [],
    gitRef: '',
    containerPrefix: '',
    port: 3307,
    dbConfig: { username: 'root', password: '', database: '' } as any
  },
  target: {
    codePath: '',
    composePath: '',
    serviceName: 'mysql',
    excludeInitSql: [],
    gitRef: '',
    containerPrefix: '',
    port: 3308,
    dbConfig: { username: 'root', password: '', database: '' } as any
  },
  ignoreFields: [],
  excludeTables: [],
  ignoreDataTables: [],
  specifiedPrimaryKeys: [],
  excludeDataRows: [],
  includeDataRows: [],
  specifiedDataQueries: []
});

// Watch separateCodePath to sync source with target if disabled
watch(() => form.separateCodePath, (newVal) => {
    if (!newVal) {
        syncSourceFromTarget();
    }
});

const syncSourceFromTarget = () => {
    form.source.codePath = form.target.codePath;
    form.source.composePath = form.target.composePath;
    form.source.serviceName = form.target.serviceName;
    form.source.excludeInitSql = form.target.excludeInitSql;
    // Keep prefix, port, gitRef, dbConfig separate
};

const loadEnvList = async () => {
  try {
    const res = await listEnvs();
    envFiles.value = res.data;
  } catch (error) {
    console.error(error);
  }
};

const handleLoadEnv = async () => {
  if (!selectedEnvFile.value) return;
  localStorage.setItem('lastSelectedEnv', selectedEnvFile.value);
  try {
    const res = await loadEnv(selectedEnvFile.value);
    Object.assign(form, res.data);
    toast.add({ severity: 'success', summary: 'Loaded', detail: 'Environment config loaded', life: 3000 });
    fetchGitStatus('target');
    if (form.separateCodePath) {
        fetchGitStatus('source');
    }
    checkStatus();
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: error.message, life: 5000 });
  }
};

const handleSaveEnv = async () => {
  if (!form.separateCodePath) {
      syncSourceFromTarget();
  }
  try {
    await saveEnv(form);
    toast.add({ severity: 'success', summary: 'Saved', detail: `Saved to ${form.name}.yml`, life: 3000 });
    await loadEnvList();
    selectedEnvFile.value = `${form.name}.yml`;
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: error.message, life: 5000 });
  }
};

const handleGenerateConfig = async () => {
  if (!form.separateCodePath) {
      syncSourceFromTarget();
  }
  try {
    const res = await generateConfig(form);
    toast.add({ severity: 'success', summary: 'Generated', detail: `Generated config/${res.data.filename}`, life: 3000 });
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: error.message, life: 5000 });
  }
};

const handleStart = async (type: 'source' | 'target') => {
  if (!form.separateCodePath && type === 'source') {
      syncSourceFromTarget();
  }
  const target = type === 'source' ? form.source : form.target;
  const loading = type === 'source' ? loadingSource : loadingTarget;
  
  if (!target.composePath || !target.serviceName) {
    toast.add({ severity: 'warn', summary: 'Missing Info', detail: 'Compose path and Service name required' });
    return;
  }

  loading.value = true;
  try {
    const params: DockerParams = {
      codePath: target.codePath,
      composePath: target.composePath,
      prefix: target.containerPrefix,
      serviceName: target.serviceName,
      port: target.port,
      excludeInitSql: target.excludeInitSql,
      gitRef: target.gitRef
    };
    await startDocker(params);
    toast.add({ severity: 'success', summary: 'Started', detail: `${type} container started`, life: 3000 });
    checkStatus();
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: error.message || 'Failed to start', life: 3000 });
  } finally {
    loading.value = false;
  }
};

const handleStop = async (type: 'source' | 'target') => {
  const target = type === 'source' ? form.source : form.target;
  const loading = type === 'source' ? loadingSource : loadingTarget;

  loading.value = true;
  try {
    const params: DockerParams = {
      codePath: target.codePath,
      composePath: target.composePath,
      prefix: target.containerPrefix,
      serviceName: target.serviceName,
      port: target.port
    };
    await stopDocker(params);
    toast.add({ severity: 'success', summary: 'Stopped', detail: `${type} container stopped`, life: 3000 });
    checkStatus();
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: error.message || 'Failed to stop', life: 3000 });
  } finally {
    loading.value = false;
  }
};

const activeSelectorMode = ref<'file' | 'directory'>('directory');

const openFileSelector = (type: 'source' | 'target', mode: 'file' | 'directory') => {
    activeSelectorType.value = type;
    activeSelectorMode.value = mode;
    const target = type === 'source' ? form.source : form.target;
    // Determine initial path: try composePath for file mode, codePath for directory mode
    let initialPath = mode === 'file' ? target.composePath : target.codePath;
    
    // If selecting file but no path set, try code path as base
    if (mode === 'file' && !initialPath && target.codePath) {
        initialPath = target.codePath;
    }
    
    fileSelector.value.open(initialPath, mode);
};

const handleFileSelect = async (path: string) => {
    const target = activeSelectorType.value === 'source' ? form.source : form.target;
    
    if (activeSelectorMode.value === 'file') {
        target.composePath = path;
        // Auto-detect Code Path (Git Root)
        if (path) {
            try {
                const res = await findGitRoot(path);
                target.codePath = res.data;
                // Fetch git status which will also set gitRef
                fetchGitStatus(activeSelectorType.value);
            } catch (e) {
                console.error("Failed to find git root", e);
            }
        }
    } else {
        target.codePath = path;
        fetchGitStatus(activeSelectorType.value);
    }

    if (!form.separateCodePath && activeSelectorType.value === 'target') {
        syncSourceFromTarget();
    }
};

const fetchGitStatus = async (type: 'source' | 'target') => {
    const target = type === 'source' ? form.source : form.target;
    const path = target.codePath;
    if (!path) return;
    try {
        const res = await getGitStatus(path);
        if (type === 'source') {
            sourceGitStatus.value = res.data;
        } else {
            targetGitStatus.value = res.data;
        }
        
        // Auto-fill Git Ref if branch is available
        if (res.data.branch) {
            target.gitRef = res.data.branch;
        }
    } catch (e) {
        console.error(e);
    }
};

const checkStatus = async () => {
    // Initial check
    const check = async (type: 'source' | 'target') => {
        const target = type === 'source' ? form.source : form.target;
        if (!target.composePath || !target.serviceName) return;
        try {
            const res = await getDockerStatus({
                codePath: target.codePath,
                composePath: target.composePath,
                prefix: target.containerPrefix,
                serviceName: target.serviceName,
                port: target.port
            });
            if (type === 'source') sourceStatus.value = res.data.running;
            else targetStatus.value = res.data.running;
        } catch (e) {
            console.error(e);
        }
    };
    await check('source');
    await check('target');
    
    // Start SSE stream
    startStatusStream('source');
    startStatusStream('target');
};

const statusEventSources = {
    source: null as EventSource | null,
    target: null as EventSource | null
};

const startStatusStream = (type: 'source' | 'target') => {
    // Close existing stream
    if (statusEventSources[type]) {
        statusEventSources[type]?.close();
        statusEventSources[type] = null;
    }

    const target = type === 'source' ? form.source : form.target;
    if (!target.composePath || !target.serviceName) return;

    const url = getDockerStatusStreamUrl({
        codePath: target.codePath,
        composePath: target.composePath,
        prefix: target.containerPrefix,
        serviceName: target.serviceName,
        port: target.port
    });

    const evtSource = new EventSource(url);
    evtSource.addEventListener('status', (event: MessageEvent) => {
        const data = JSON.parse(event.data);
        if (type === 'source') sourceStatus.value = data.running;
        else targetStatus.value = data.running;
    });
    
    evtSource.addEventListener('error', (event) => {
        console.error(`SSE error for ${type}`, event);
        // Optional: retry logic or status reset
    });

    statusEventSources[type] = evtSource;
};

onMounted(async () => {
  await loadEnvList();
  const lastSelectedEnv = localStorage.getItem('lastSelectedEnv');
  if (lastSelectedEnv && envFiles.value.includes(lastSelectedEnv)) {
    selectedEnvFile.value = lastSelectedEnv;
    handleLoadEnv();
  }
});

onUnmounted(() => {
    if (statusEventSources.source) statusEventSources.source.close();
    if (statusEventSources.target) statusEventSources.target.close();
});
</script>


