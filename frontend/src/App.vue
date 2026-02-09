<template>
  <div class="flex flex-col h-full" style="background-color: #f8f9fa;">
    <Toast />
    
    <!-- Config View -->
    <div v-if="currentView === 'config'" class="flex-1 overflow-hidden">
      <ConfigForm @result="handleResult" @open-env="openEnvManage" />
    </div>

    <!-- Env Management View -->
    <div v-else-if="currentView === 'env-manage'" class="flex-1 overflow-hidden">
      <EnvManagement @back="backToConfig" />
    </div>

    <!-- Detail View -->
    <div v-else class="flex-1 overflow-hidden p-4">
      <div class="h-full flex flex-col gap-4 ">
        <div class="flex items-center">
          <Button @click="backToConfig" icon="pi pi-arrow-left" label="Back to Config" size="small" text />
        </div>
        <ResultView :resultId="resultId" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import ConfigForm from './components/ConfigForm.vue';
import ResultView from './components/ResultView.vue';
import EnvManagement from './components/EnvManagement.vue';
import Toast from 'primevue/toast';
import Button from 'primevue/button';

const resultId = ref<string>('');
const currentView = ref<'config' | 'detail' | 'env-manage'>('config');

const handleResult = (id: string) => {
  resultId.value = id;
  currentView.value = 'detail';
};

const backToConfig = () => {
  currentView.value = 'config';
};

const openEnvManage = () => {
  currentView.value = 'env-manage';
};
</script>

