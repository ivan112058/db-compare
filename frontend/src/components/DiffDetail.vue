<template>
  <div class="h-full flex flex-col p-0 bg-white" v-if="diff">
    <ScrollPanel style="width: 100%; height: 100%">
        <!-- Structure Diff -->
        <div v-if="diff.structDiff && ((diff.structDiff.columns && diff.structDiff.columns.length > 0) || (diff.structDiff.indexes && diff.structDiff.indexes.length > 0))" class="mb-8">
          <h3>Structure Difference</h3>
          
          <!-- Columns -->
          <Card style="margin-bottom: 20px">
            <template #title><h4>Columns</h4></template>
            <template #content>
              <DataTable :value="diff.structDiff.columns" size="small" showGridlines :rowClass="tableRowClass">
                <Column field="name" header="Column" style="width: 160px" />
                <Column header="Source" style="min-width: 200px">
                  <template #body="slotProps">
                    <div v-if="slotProps.data.source">{{ formatColumn(slotProps.data.source) }}</div>
                    <div v-else class="text-gray-500">--</div>
                  </template>
                </Column>
                <Column header="Target" style="min-width: 200px">
                  <template #body="slotProps">
                    <div v-if="slotProps.data.target">{{ formatColumn(slotProps.data.target) }}</div>
                    <div v-else class="text-gray-500">--</div>
                  </template>
                </Column>
                <Column field="status" header="Status" style="width: 100px">
                  <template #body="slotProps">
                    <Tag :severity="getStatusSeverity(slotProps.data.status)" :value="slotProps.data.status" />
                  </template>
                </Column>
              </DataTable>
            </template>
          </Card>

          <!-- Indexes & Keys -->
          <Card>
            <template #title><h4>Keys & Indexes</h4></template>
            <template #content>
              <DataTable :value="diff.structDiff.indexes" size="small" showGridlines :rowClass="tableRowClass">
                <Column header="Type" style="width: 100px">
                  <template #body="slotProps">
                    <div>{{ slotProps.data.source?.type || slotProps.data.target?.type }}</div>
                  </template>
                </Column>
                <Column field="name" header="Name" style="width: 230px" />
                <Column header="Source" style="min-width: 200px">
                  <template #body="slotProps">
                    <div v-if="slotProps.data.source">
                      <Tag v-for="col in slotProps.data.source.columns" :key="col" :value="col" severity="secondary" style="margin-right: 4px" />
                    </div>
                    <div v-else class="text-gray-500">--</div>
                  </template>
                </Column>
                <Column header="Target" style="min-width: 200px">
                  <template #body="slotProps">
                    <div v-if="slotProps.data.target">
                      <Tag v-for="col in slotProps.data.target.columns" :key="col" :value="col" severity="secondary" style="margin-right: 4px" />
                    </div>
                    <div v-else class="text-gray-500">--</div>
                  </template>
                </Column>
                <Column field="status" header="Status" style="width: 100px">
                  <template #body="slotProps">
                    <Tag :severity="getStatusSeverity(slotProps.data.status)" :value="slotProps.data.status" />
                  </template>
                </Column>
              </DataTable>
            </template>
          </Card>
          
        </div>

        <!-- Data Diff -->
        <div v-if="diff.dataDiff" class="mb-8">
          <h3>Data Difference</h3>
          
          <div v-if="diff.primaryKeys && diff.primaryKeys.length > 0" style="margin-bottom: 15px;">
            <Tag icon="pi pi-key" severity="info" :value="'Primary Key: ' + diff.primaryKeys.join(', ')" />
          </div>

          <!-- Removed (In Source, Not in Target) -->
          <Card class="mb-5" :pt="{ title: { class: 'text-red-500' } }" v-if="diff.dataDiff.removed.length > 0">
            <template #title>
              <h4>Source Only (Removed in Target) - {{ diff.dataDiff.removed.length }}</h4>
            </template>
            <template #content>
                <DataTable :value="diff.dataDiff.removed.slice(0, 50)" size="small" showGridlines scrollable scrollHeight="400px">
                  <Column v-for="key in getKeys(diff.dataDiff.removed)" :key="key" :field="key" style="min-width: 100px">
                    <template #header>
                      <span v-if="isPrimaryKey(key)" style="font-weight: bold; color: var(--p-primary-color);">
                        <i class="pi pi-key" style="font-size: 0.8rem; margin-right: 4px;"></i>{{ key }}
                      </span>
                      <span v-else>{{ key }}</span>
                    </template>
                  </Column>
                </DataTable>
                <div v-if="diff.dataDiff.removed.length > 50" class="text-center text-gray-400 p-2 text-xs">... showing first 50 rows</div>
            </template>
          </Card>

          <!-- Added (In Target, Not in Source) -->
          <Card class="mb-5" :pt="{ title: { class: 'text-green-500' } }" v-if="diff.dataDiff.added.length > 0">
            <template #title>
              <h4>Target Only (Added in Target) - {{ diff.dataDiff.added.length }}</h4>
            </template>
            <template #content>
                <DataTable :value="diff.dataDiff.added.slice(0, 50)" size="small" showGridlines scrollable scrollHeight="400px">
                  <Column v-for="key in getKeys(diff.dataDiff.added)" :key="key" :field="key" style="min-width: 100px">
                    <template #header>
                      <span v-if="isPrimaryKey(key)" style="font-weight: bold; color: var(--p-primary-color);">
                        <i class="pi pi-key" style="font-size: 0.8rem; margin-right: 4px;"></i>{{ key }}
                      </span>
                      <span v-else>{{ key }}</span>
                    </template>
                  </Column>
                </DataTable>
                <div v-if="diff.dataDiff.added.length > 50" class="text-center text-gray-400 p-2 text-xs">... showing first 50 rows</div>
            </template>
          </Card>

          <!-- Modified (Different values for same PK) -->
          <Card v-if="diff.dataDiff.modified && diff.dataDiff.modified.length > 0" class="mb-5" :pt="{ title: { class: 'text-blue-500' } }">
            <template #title>
              <h4>Modified Rows - {{ diff.dataDiff.modified.length }}</h4>
            </template>
            <template #content>
              <DataTable :value="diff.dataDiff.modified.slice(0, 50)" size="small" showGridlines scrollable scrollHeight="400px">
                <Column v-for="key in getKeys(diff.dataDiff.modified)" :key="key" :field="key" style="min-width: 100px">
                    <template #header>
                      <span v-if="isPrimaryKey(key)" style="font-weight: bold; color: var(--p-primary-color);">
                        <i class="pi pi-key" style="font-size: 0.8rem; margin-right: 4px;"></i>{{ key }}
                      </span>
                      <span v-else>{{ key }}</span>
                    </template>
                    <template #body="slotProps">
                        <div v-if="isCellModified(slotProps.data, key)" class="flex flex-col bg-blue-50 rounded px-1 py-0.5">
                          <div class="text-red-500 line-through text-[0.85em]">{{ formatValue(getSourceValue(slotProps.data, key)) }}</div>
                          <div class="text-green-500 font-medium">{{ formatValue(slotProps.data[key]) }}</div>
                        </div>
                        <span v-else>{{ formatValue(slotProps.data[key]) }}</span>
                    </template>
                </Column>
              </DataTable>
              <div v-if="diff.dataDiff.modified.length > 50" class="text-center text-gray-400 p-2 text-xs">... showing first 50 rows</div>
            </template>
          </Card>
        </div>

        <!-- SQL Scripts -->
        <div class="mb-8">
          <h3>SQL Scripts</h3>
          <div class="flex justify-between mb-2">
            <div class="flex gap-2">
                <SelectButton 
                    v-model="activeSqlTab" 
                    :options="sqlOptions" 
                    optionLabel="label" 
                    optionValue="value"
                    :allowEmpty="false"
                    size="small"
                />
            </div>
            <Button icon="pi pi-copy" label="Copy" size="small" text @click="copySql" />
          </div>
          
          <div class="bg-gray-50 p-2 rounded overflow-auto max-h-[300px] border border-gray-200">
            <pre class="m-0 font-mono whitespace-pre-wrap break-all"><code>{{ activeSqlTab === 'upgrade' ? upgradeSql : rollbackSql }}</code></pre>
          </div>
        </div>

    </ScrollPanel>
  </div>
  <div v-else class="h-full flex justify-center items-center text-gray-400">
    <div class="empty-message">Select a table to view differences</div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import ScrollPanel from 'primevue/scrollpanel';
import DataTable from 'primevue/datatable';
import Column from 'primevue/column';
import Tag from 'primevue/tag';
import Card from 'primevue/card';
import Button from 'primevue/button';
import SelectButton from 'primevue/selectbutton';
import { getTableSql } from '../api';

const props = defineProps<{
  diff: any;
  resultId: string;
}>();

const activeSqlTab = ref('upgrade');
const sqlOptions = ref([
    { label: 'Upgrade SQL', value: 'upgrade' },
    { label: 'Rollback SQL', value: 'rollback' }
]);
const upgradeSql = ref('');
const rollbackSql = ref('');

const fetchSql = async () => {
    if (props.diff && props.resultId) {
        try {
            const res = await getTableSql(props.resultId, props.diff.tableName);
            upgradeSql.value = res.data.upgradeSql;
            rollbackSql.value = res.data.rollbackSql;
        } catch (e) {
            console.error(e);
        }
    }
};

watch(() => props.diff, () => {
    upgradeSql.value = '';
    rollbackSql.value = '';
    fetchSql();
}, { immediate: true });

const copySql = () => {
    const sql = activeSqlTab.value === 'upgrade' ? upgradeSql.value : rollbackSql.value;
    navigator.clipboard.writeText(sql);
};

const getKeys = (rows: any[]) => {
  if (!rows || rows.length === 0) return [];
  // Filter out internal fields like _source
  return Object.keys(rows[0]).filter(k => !k.startsWith('_'));
};

const isPrimaryKey = (key: string) => {
  return props.diff.primaryKeys && props.diff.primaryKeys.includes(key);
};

const formatColumn = (col: any) => {
  // Column: name typeName(size) null default
  const defaultVal = col.defaultValue !== null && col.defaultValue !== undefined ? ('VARCHAR' == col.typeName ? `DEFAULT '${col.defaultValue}'` : `DEFAULT ${col.defaultValue}`) : '';
  return `${col.typeName}(${col.columnSize}) ${col.isNullable ? 'NULL' : 'NOT NULL'} ${col.isAutoIncrement ? 'AUTO_INCREMENT' : ''} ${defaultVal}`;
};

const getStatusSeverity = (status: string) => {
  switch (status) {
    case 'ADDED': return 'success';
    case 'REMOVED': return 'danger';
    case 'MODIFIED': return 'info'; // 'primary' is not a severity, use 'info' or custom
    default: return 'secondary';
  }
};

const tableRowClass = (data: any) => {
  if (data.status === 'REMOVED') return '!bg-red-50';
  if (data.status === 'ADDED') return '!bg-green-50';
  if (data.status === 'MODIFIED') return '!bg-blue-50';
  return '';
};

const getSourceValue = (row: any, key: string) => {
    return row._source ? row._source[key] : '';
};

const isCellModified = (row: any, key: string) => {
    if (!row._source) return false;
    // Compare values
    const sourceVal = row._source[key];
    const targetVal = row[key];
    return sourceVal !== targetVal;
};

const formatValue = (val: any) => {
    if (val === null) return 'NULL';
    if (val === undefined) return '';
    return String(val);
};
</script>

