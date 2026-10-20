<template>
  <el-checkbox
    class="source-choice"
    size="large"
    border
    :value="sourceUrl"
    :class="{
      error: isSaveError,
      edit: sourceUrl == currentSourceUrl,
    }"
  >
    <span class="source-indicator" :class="{ 'is-error': isSaveError, 'is-current': sourceUrl == currentSourceUrl }" aria-hidden="true"></span>
    <span class="source-name">{{ getSourceName(source) }}</span>
    <el-tag v-if="isSaveError" size="small" type="danger" effect="plain">未推送</el-tag>
    <el-button
      text
      :icon="Edit"
      :aria-label="`编辑${getSourceName(source)}`"
      @click.stop="handleSourceClick(source)"
    />
  </el-checkbox>
</template>

<script setup lang="ts">
import { Edit } from '@element-plus/icons-vue'
import { getSourceUniqueKey, getSourceName } from '@/utils/souce'
import type { Source } from '@/source'

const props = defineProps<{
  source: Source
}>()

const store = useSourceStore()

const currentSourceUrl = computed(() => store.currentSourceUrl)
const sourceUrl = computed(() => getSourceUniqueKey(props.source))

const handleSourceClick = (source: Source) => {
  store.changeCurrentSource(source)
}
const isSaveError = computed(() => {
  const map = store.savedSourcesMap
  if (map.size == 0) return false
  return !map.has(sourceUrl.value)
})
</script>
<style lang="scss" scoped>
:deep(.el-checkbox__label) {
  flex: 1;
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
  color: var(--app-ink-soft);
  font-size: 12px;
}

.source-choice {
  width: 100%;
  min-height: 42px;
  border-color: var(--app-line);
  border-radius: 9px;
  background: var(--app-panel-raised);
}

.source-name {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-indicator {
  width: 7px;
  height: 7px;
  flex: none;
  border-radius: 50%;
  background: var(--app-success);
}

.source-indicator.is-current {
  background: var(--app-accent);
  box-shadow: 0 0 0 3px var(--app-accent-glow);
}

.source-indicator.is-error {
  background: var(--app-danger);
  box-shadow: 0 0 0 3px var(--app-danger-glow);
}

.source-choice :deep(.el-tag) {
  flex: none;
  border-radius: 6px;
  font-size: 10px;
}

.source-choice :deep(.el-button) {
  flex: none;
  margin-left: auto;
}

.source-choice:hover {
  border-color: var(--app-accent-line);
}
</style>
