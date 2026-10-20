<template>
  <el-tabs v-model="current_tab">
    <el-tab-pane
      v-for="(tab, index) in tabData"
      :key="tab.name"
      :name="tab.name"
    >
      <template #label>
        <span class="tool-tab-label"><el-icon><component :is="tab.icon" /></el-icon>{{ tab.label }}</span>
      </template>
      <source-json v-if="index == 0" />
      <source-debug v-if="index == 1" />
      <source-list v-if="index == 2" />
      <source-help v-if="index == 3" />
    </el-tab-pane>
  </el-tabs>
</template>

<script setup lang="ts">
import { useSourceStore } from '@/store'
import { EditPen, VideoPlay, List, QuestionFilled } from '@element-plus/icons-vue'

const store = useSourceStore()

const current_tab = computed({
  get: () => store.currentTab,
  set: val => (store.currentTab = val),
})

const tabData = ref([
  { name: 'editTab', label: '源编辑', icon: EditPen },
  { name: 'editDebug', label: '调试', icon: VideoPlay },
  { name: 'editList', label: '源列表', icon: List },
  { name: 'editHelp', label: '帮助', icon: QuestionFilled },
])
</script>

<style lang="scss" scoped>
:deep(.el-tabs__nav) {
  gap: 4px;
}

:deep(.el-tabs__item) {
  height: 46px;
  color: var(--app-ink-muted);
  font-size: 12px;
  font-weight: 650;
}

:deep(.el-tabs__item.is-active) {
  color: var(--app-accent-strong);
}

.tool-tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

:deep(.el-tabs__header) {
  margin-bottom: 0;
}
</style>
