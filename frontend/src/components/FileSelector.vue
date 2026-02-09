<template>
  <Dialog v-model:visible="visible" modal header="Select Directory" :style="{ width: '50rem' }" :breakpoints="{ '1199px': '75vw', '575px': '90vw' }">
    <div class="flex flex-col gap-4">
      <div class="flex gap-2">
        <Button icon="pi pi-arrow-up" @click="goUp" :disabled="!currentPath || currentPath === '/'" />
        <InputText v-model="currentPath" @keyup.enter="loadFiles(currentPath)" fluid placeholder="Enter path..." />
        <Button icon="pi pi-refresh" @click="loadFiles(currentPath)" />
      </div>

      <div class="border rounded p-2 overflow-y-auto h-[300px]">
        <div v-if="loading" class="flex justify-center items-center h-full">
          <i class="pi pi-spin pi-spinner text-2xl"></i>
        </div>
        <div v-else-if="files.length === 0" class="flex justify-center items-center h-full text-gray-500">
          No files found
        </div>
        <div v-else class="flex flex-col">
          <div 
            v-for="file in files" 
            :key="file.path"
            class="p-2 hover:bg-gray-100 cursor-pointer flex items-center gap-2 transition-colors duration-200"
            :class="{ 'bg-blue-50': selectedPath === file.path }"
            @click="handleSelect(file)"
            @dblclick="handleDoubleClick(file)"
          >
            <i :class="file.isDirectory ? 'pi pi-folder text-yellow-500' : 'pi pi-file text-gray-500'"></i>
            <span>{{ file.name }}</span>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <Button label="Cancel" icon="pi pi-times" text @click="visible = false" />
      <Button label="Select" icon="pi pi-check" @click="confirmSelect" :disabled="!selectedPath" />
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import Dialog from 'primevue/dialog';
import Button from 'primevue/button';
import InputText from 'primevue/inputtext';
import { listFiles, getParentDir, type FileItem } from '../api';

const props = defineProps<{
  initialPath?: string,
  selectionMode?: 'file' | 'directory'
}>();

const emit = defineEmits(['select', 'update:visible']);

const visible = ref(false);
const currentPath = ref('');
const files = ref<FileItem[]>([]);
const loading = ref(false);
const selectedPath = ref('');
const currentSelectionMode = ref<'file' | 'directory'>('directory');

// Expose open method
const open = (path?: string, mode?: 'file' | 'directory') => {
  visible.value = true;
  currentSelectionMode.value = mode || props.selectionMode || 'directory';
  // If we have a path, use it. If it's a file path, loadFiles will handle getting the parent dir listing
  // but we want currentPath to reflect the directory being viewed.
  // Ideally backend should return the "current directory path" in the response, but for now:
  currentPath.value = path || props.initialPath || '';
  
  // If opening in file mode and path is provided, pre-select it
  if (currentSelectionMode.value === 'file' && path) {
      selectedPath.value = path;
  } else {
      selectedPath.value = '';
  }
  
  loadFiles(currentPath.value);
};

defineExpose({ open });

const loadFiles = async (path?: string) => {
  loading.value = true;
  try {
    const res = await listFiles(path);
    files.value = res.data;
    // Update current path if we loaded root or normalized path
    if (res.data.length > 0 && path) {
       // We can't easily get the absolute path of "path" from list response unless we assume it worked.
       // But if path is empty, we don't know where we are. 
       // Actually, we can assume the first item's parent is the current path if items exist.
       // Or simpler: just trust the input path if it returned results, or update it if we clicked.
    }
    // If path was empty, try to deduce it from first item
    if (!path && res.data.length > 0) {
        const firstItem = res.data[0];
        if (firstItem) {
            const parent = await getParentDir(firstItem.path);
            if (parent && parent.data) {
                currentPath.value = parent.data.path;
            }
        }
    } else if (path) {
         // If path is a file (based on our logic, files are listed in their parent dir), 
         // we might want to update currentPath to be the directory if we can detect it.
         // For now, if the list is not empty, let's assume the first item's parent is the correct dir.
         if (res.data.length > 0) {
             const firstItem = res.data[0];
             if (firstItem) {
                 const parent = await getParentDir(firstItem.path);
                 if (parent && parent.data) {
                    currentPath.value = parent.data.path;
                 }
             }
         }
    }
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
};

const goUp = async () => {
  if (!currentPath.value) return;
  try {
    const res = await getParentDir(currentPath.value);
    if (res.data) {
      currentPath.value = res.data.path;
      loadFiles(currentPath.value);
    }
  } catch (error) {
    console.error(error);
  }
};

const handleSelect = (file: FileItem) => {
  if (currentSelectionMode.value === 'file') {
      selectedPath.value = file.path;
  } else {
      if (file.isDirectory) {
        selectedPath.value = file.path;
      }
  }
};

const handleDoubleClick = (file: FileItem) => {
  if (file.isDirectory) {
    currentPath.value = file.path;
    loadFiles(file.path);
  } else if (currentSelectionMode.value === 'file') {
      selectedPath.value = file.path;
      confirmSelect();
  }
};

const confirmSelect = () => {
  emit('select', selectedPath.value);
  visible.value = false;
};
</script>


