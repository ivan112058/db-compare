<template>
  <ScrollPanel class="w-full h-full"">
    <div class="p-8 max-w-250 mx-auto">
      <Card class="mb-8">
        <template #title>Configuration</template>
        <template #content>
          <div class="flex flex-col gap-4">
            <div class="flex gap-4">
              <div class="flex flex-col gap-2 flex-1">
                <label class="text-sm font-medium text-gray-600">Load Config</label>
                <div class="flex gap-2">
                  <Select v-model="selectedConfig" :options="configFiles" placeholder="Select a config file" fluid class="flex-1" @change="handleLoadConfig" />
                </div>
              </div>
              <div class="flex flex-col gap-2 flex-1">
                <label class="text-sm font-medium text-gray-600">Save Config</label>
                <div class="flex gap-2 items-center">
                  <InputGroup class="flex-1">
                      <InputText v-model="saveConfigName" placeholder="Config Name (e.g. my-config)" />
                      <Button icon="pi pi-save" @click="handleSaveConfig" :disabled="!saveConfigName" />
                  </InputGroup>
                  <Button icon="pi pi-cog" @click="$emit('open-env')" severity="secondary" v-tooltip="'Manage Environments'" />
                </div>
              </div>
            </div>
          </div>
        </template>
      </Card>

      <div class="flex gap-8">
        <Card class="mb-8 flex-1">
          <template #title>Source</template>
          <template #content>
            <div class="flex flex-col gap-4">
              <div class="flex gap-4">
                <div class="flex flex-col gap-2 flex-1">
                  <label class="text-sm font-medium text-gray-600">Host</label>
                  <InputText v-model="form.source.host" placeholder="localhost" fluid />
                </div>
                <div class="flex flex-col gap-2 w-25">
                  <label class="text-sm font-medium text-gray-600">Port</label>
                  <InputNumber v-model="form.source.port" :min="1" :max="65535" :useGrouping="false" fluid />
                </div>
              </div>
              <div class="flex gap-4">
                <div class="flex flex-col gap-2 flex-1">
                  <label class="text-sm font-medium text-gray-600">Username</label>
                  <InputText v-model="form.source.username" fluid />
                </div>
                <div class="flex flex-col gap-2 flex-1">
                  <label class="text-sm font-medium text-gray-600">Password</label>
                  <Password v-model="form.source.password" :feedback="false" toggleMask fluid />
                </div>
              </div>
              <div class="flex flex-col gap-2">
                <label class="text-sm font-medium text-gray-600">Database</label>
                <InputText v-model="form.source.database" fluid />
              </div>
            </div>
          </template>
        </Card>

        <Card class="mb-8 flex-1">
          <template #title>Target</template>
          <template #content>
            <div class="flex flex-col gap-4">
              <div class="flex gap-4">
                <div class="flex flex-col gap-2 flex-1">
                  <label class="text-sm font-medium text-gray-600">Host</label>
                  <InputText v-model="form.target.host" placeholder="localhost" fluid />
                </div>
                <div class="flex flex-col gap-2 w-25">
                  <label class="text-sm font-medium text-gray-600">Port</label>
                  <InputNumber v-model="form.target.port" :min="1" :max="65535" :useGrouping="false" fluid />
                </div>
              </div>
              <div class="flex gap-4">
                <div class="flex flex-col gap-2 flex-1">
                  <label class="text-sm font-medium text-gray-600">Username</label>
                  <InputText v-model="form.target.username" fluid />
                </div>
                <div class="flex flex-col gap-2 flex-1">
                  <label class="text-sm font-medium text-gray-600">Password</label>
                  <Password v-model="form.target.password" :feedback="false" toggleMask fluid />
                </div>
              </div>
              <div class="flex flex-col gap-2">
                <label class="text-sm font-medium text-gray-600">Database</label>
                <InputText v-model="form.target.database" fluid />
              </div>
            </div>
          </template>
        </Card>
      </div>

      <Card class="mb-8">
        <template #title>Options</template>
        <template #content>
          <div class="flex flex-col gap-4">
            <div class="flex flex-col gap-2">
              <label class="text-sm font-medium text-gray-600">Ignore Fields</label>
              <AutoComplete v-model="form.ignoreFields" multiple :typeahead="false" placeholder="e.g. create_time or table.column" fluid />
            </div>
            <div class="flex flex-col gap-2">
              <label class="text-sm font-medium text-gray-600">Exclude Tables</label>
              <AutoComplete v-model="form.excludeTables" multiple :typeahead="false" placeholder="Table names" fluid />
            </div>
            <div class="flex flex-col gap-2">
              <label class="text-sm font-medium text-gray-600">Ignore Data</label>
              <AutoComplete v-model="form.ignoreDataTables" multiple :typeahead="false" placeholder="Skip data compare" fluid />
            </div>
            <div class="flex flex-col gap-2">
              <label class="text-sm font-medium text-gray-600">Specified Primary Keys</label>
              <AutoComplete v-model="form.specifiedPrimaryKeys" multiple :typeahead="false" placeholder="table(col1,col2)" fluid />
            </div>
            <div class="flex flex-col gap-2">
              <label class="text-sm font-medium text-gray-600">Exclude Data Rows</label>
              <AutoComplete v-model="form.excludeDataRows" multiple :typeahead="false" placeholder="table(col=val) or table(col1#col2=val1#val2)" fluid />
            </div>
            <div class="flex flex-col gap-2">
              <label class="text-sm font-medium text-gray-600">Include Data Rows</label>
              <AutoComplete v-model="form.includeDataRows" multiple :typeahead="false" placeholder="table(col=val) or table(col1#col2=val1#val2)" fluid />
            </div>
            <div class="flex flex-col gap-2">
              <label class="text-sm font-medium text-gray-600">Specified Data Queries</label>
              <AutoComplete v-model="form.specifiedDataQueries" multiple :typeahead="false" placeholder="table=select * from table where ..." fluid />
            </div>
          </div>
        </template>
      </Card>

      <div class="pb-4 flex justify-center relative">
        <Button label="Start Compare" @click="handleCompare" :loading="loading" class="w-50" />
      </div>
    </div>
  </ScrollPanel>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue';
import { compare, listConfigs, loadConfig, saveConfig } from '../api';
import type { CompareRequest } from '../api';
import { useToast } from 'primevue/usetoast';
import InputText from 'primevue/inputtext';
import InputNumber from 'primevue/inputnumber';
import InputGroup from 'primevue/inputgroup';
import Password from 'primevue/password';
import Button from 'primevue/button';
import ScrollPanel from 'primevue/scrollpanel';
import AutoComplete from 'primevue/autocomplete';
import Card from 'primevue/card';
import Select from 'primevue/select';

const emit = defineEmits(['result', 'start-compare', 'open-env']);
const toast = useToast();

const loading = ref(false);
const configFiles = ref<string[]>([]);
const selectedConfig = ref<string | null>(null);
const saveConfigName = ref('');

const form = reactive<CompareRequest>({
  source: {
    host: 'localhost',
    port: 3406,
    username: 'root',
    password: 'Huawei@123',
    database: 'yian-sys-base-db'
  },
  target: {
    host: 'localhost',
    port: 3306,
    username: 'root',
    password: 'Huawei@123',
    database: 'yian-sys-base-db'
  },
  ignoreFields: [],
  excludeTables: [],
  ignoreDataTables: [],
  specifiedPrimaryKeys: [],
  specifiedDataQueries: []
});

const loadConfigs = async () => {
  try {
    const res = await listConfigs();
    configFiles.value = res.data;
  } catch (error) {
    console.error('Failed to list configs', error);
  }
};

const handleLoadConfig = async () => {
  if (!selectedConfig.value) return;
  localStorage.setItem('lastSelectedConfig', selectedConfig.value);
  try {
    const res = await loadConfig(selectedConfig.value);
    Object.assign(form, res.data);
    toast.add({ severity: 'success', summary: 'Success', detail: 'Configuration loaded', life: 3000 });
    // Update save name to match loaded config (without extension if possible)
    saveConfigName.value = selectedConfig.value.replace(/\.ya?ml$/, '');
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to load config: ' + (error.response?.data?.error || error.message), life: 5000 });
  }
};

const handleSaveConfig = async () => {
  if (!saveConfigName.value) return;
  try {
    await saveConfig(saveConfigName.value, form);
    toast.add({ severity: 'success', summary: 'Success', detail: 'Configuration saved', life: 3000 });
    await loadConfigs(); // Refresh list
    selectedConfig.value = saveConfigName.value.endsWith('.yml') 
      ? saveConfigName.value 
      : `${saveConfigName.value}.yml`;
    localStorage.setItem('lastSelectedConfig', selectedConfig.value);
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to save config: ' + (error.response?.data?.error || error.message), life: 5000 });
  }
};

onMounted(async () => {
  await loadConfigs();
  
  const lastConfig = localStorage.getItem('lastSelectedConfig');
  if (lastConfig && configFiles.value.includes(lastConfig)) {
    selectedConfig.value = lastConfig;
  } else if (configFiles.value.includes('yian-local.yml')) {
    selectedConfig.value = 'yian-local.yml';
  }

  if (selectedConfig.value) {
    await handleLoadConfig();
  }
});

const handleCompare = async () => {
  loading.value = true;
  emit('start-compare');
  try {
    const res = await compare(form);
    if (res.data.id) {
      emit('result', res.data.id);
      toast.add({ severity: 'success', summary: 'Success', detail: 'Comparison completed', life: 3000 });
    } else {
      toast.add({ severity: 'info', summary: 'No Differences', detail: 'Source and Target have no differences.', life: 3000 });
    }
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Comparison failed: ' + (error.response?.data?.error || error.message), life: 5000 });
  } finally {
    loading.value = false;
  }
};
</script>


