<template>
  <div class="flex h-full gap-4 p-4 box-border overflow-hidden ">
    <!-- Left Column -->
    <Card :pt="{ 
      root: { class: 'h-full flex flex-col w-80 shrink-0' }, 
      body: { class: 'flex-1 flex flex-col h-full p-4 overflow-hidden' }, 
      content: { class: 'flex-1 overflow-y-auto mb-4 p-0 flex flex-col' } 
    }">
      <template #title>Tables</template>
      <template #content>
        <Listbox 
          v-model="selectedTable" 
          :options="tables" 
          optionLabel="tableName"
          optionValue="tableName"
          :pt="{ 
            root: { class: 'w-full h-full flex flex-col flex-1 !border-none' }, 
            list: { class: 'flex-1 overflow-auto' } 
          }"
          scrollHeight="auto"
          @change="onTableChange"
        >
          <template #option="slotProps">
            <div class="flex items-center justify-between w-full px-2">
              <span class="whitespace-nowrap overflow-hidden text-ellipsis mr-2" :title="slotProps.option.tableName">{{ slotProps.option.tableName }}</span>
              <div class="flex gap-1">
                <Tag v-if="slotProps.option.hasStructDiff" severity="warn" value="S" class="text-[0.7rem]! px-[0.4rem]! py-[0.1rem]!" />
                <Tag v-if="slotProps.option.hasDataDiff" severity="danger" value="D" class="text-[0.7rem]! px-[0.4rem]! py-[0.1rem]!" />
              </div>
            </div>
          </template>
        </Listbox>
      </template>
      <template #footer>
        <div class="flex flex-col gap-4">
          <div class="flex justify-between px-1 text-sm">
            <div class="flex items-center gap-1">
              <span class="font-semibold text-gray-600">Struct:</span>
              <Tag severity="warn" :value="String(structDiffCount)" class="px-2" />
            </div>
            <div class="flex items-center gap-1">
              <span class="font-semibold text-gray-600">Data:</span>
              <Tag severity="danger" :value="String(dataDiffCount)" class="px-2" />
            </div>
            <div class="flex items-center gap-1">
              <span class="font-semibold text-gray-600">Both:</span>
              <Tag severity="info" :value="String(bothDiffCount)" class="px-2" />
            </div>
          </div>
          <div class="flex gap-2">
            <Button label="Upgrade" icon="pi pi-download" size="small" class="flex-1" @click="downloadSql('upgrade')" />
            <Button label="Rollback" icon="pi pi-download" size="small" severity="secondary" class="flex-1" @click="downloadSql('rollback')" />
          </div>
        </div>
      </template>
    </Card>
    
    <!-- Right Column: Details -->
    <Card :pt="{ 
      root: { class: 'h-full flex flex-col flex-1 overflow-hidden' }, 
      body: { class: 'flex-1 flex flex-col h-full p-4 overflow-hidden' }, 
      content: { class: 'flex-1 overflow-hidden p-0 flex flex-col' } 
    }">
      <template #title>Details</template>
      <template #content>
        <div class="h-full w-full">
          <div v-if="loading" class="flex justify-center items-center h-full">
            <i class="pi pi-spin pi-spinner text-4xl text-gray-400 mb-2"></i>
          </div>
          <div v-else-if="selectedTableDetail" class="h-full">
            <DiffDetail :diff="selectedTableDetail" :resultId="props.resultId" />
          </div>
          <div v-else class="flex justify-center items-center h-full text-gray-400">
            <div class="text-center">
              <i class="pi pi-table text-4xl text-gray-400 mb-2"></i>
              <p>Select a table to view details</p>
            </div>
          </div>
        </div>
      </template>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue';
import Card from 'primevue/card';
import Listbox from 'primevue/listbox';
import Tag from 'primevue/tag';
import Button from 'primevue/button';
import { getCompareTables, getTableDetail, getDownloadSqlUrl } from '../api';
import DiffDetail from './DiffDetail.vue';

const props = defineProps<{
  resultId: string
}>();

const tables = ref<any[]>([]);
const selectedTable = ref<string | null>(null);
const selectedTableDetail = ref<any | null>(null);
const loading = ref(false);

const structDiffCount = computed(() => tables.value.filter(t => t.hasStructDiff).length);
const dataDiffCount = computed(() => tables.value.filter(t => t.hasDataDiff).length);
const bothDiffCount = computed(() => tables.value.filter(t => t.hasStructDiff && t.hasDataDiff).length);

const downloadSql = (type: 'upgrade' | 'rollback') => {
  const url = getDownloadSqlUrl(props.resultId, type);
  window.open(url, '_blank');
};

const loadTables = async () => {
  if (!props.resultId) return;
  try {
    const res = await getCompareTables(props.resultId);
    tables.value = res.data.tables;
  } catch (e) {
    console.error(e);
  }
};

const onTableChange = (e: any) => {
  if (e.value) {
    selectTable(e.value);
  }
};

const selectTable = async (tableName: string) => {
  selectedTable.value = tableName;
  loading.value = true;
  try {
    const res = await getTableDetail(props.resultId, tableName);
    selectedTableDetail.value = res.data.diff;
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
  }
};

watch(() => props.resultId, () => {
  tables.value = [];
  selectedTable.value = null;
  selectedTableDetail.value = null;
  loadTables();
}, { immediate: true });

</script>

