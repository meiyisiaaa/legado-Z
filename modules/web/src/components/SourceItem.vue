<template>
  <div
    class="source-choice"
    :class="{
      error: isSaveError,
      edit: sourceUrl == currentSourceUrl,
    }"
  >
    <el-checkbox
      class="source-selection"
      size="large"
      :value="sourceUrl"
      :aria-current="sourceUrl == currentSourceUrl ? 'true' : undefined"
    >
      <span class="source-indicator" :class="{ 'is-error': isSaveError, 'is-current': sourceUrl == currentSourceUrl }" aria-hidden="true"></span>
      <span class="source-name">{{ getSourceName(source) }}</span>
      <el-tag v-if="isSaveError" size="small" type="danger" effect="plain">未推送</el-tag>
    </el-checkbox>
    <el-button
      text
      :icon="Edit"
      :aria-label="`编辑${getSourceName(source)}`"
      :aria-current="sourceUrl == currentSourceUrl ? 'true' : undefined"
      @click.stop="handleSourceClick(source)"
    />
  </div>
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
.source-choice {
  width: 100%;
  min-height: 44px;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--app-line);
  border-radius: 9px;
  padding: 0 8px;
  background: var(--app-panel-raised);
}

.source-choice:focus-within {
  border-color: var(--app-accent-line);
  box-shadow: 0 0 0 3px var(--app-focus-ring);
}

.source-choice :deep(.source-selection) {
  min-height: 44px;
  min-width: 0;
  display: flex;
  flex: 1;
  margin: 0;
}

.source-choice :deep(.el-checkbox__input) {
  flex: none;
}

.source-choice :deep(.el-checkbox__label) {
  min-width: 0;
  display: flex;
  flex: 1;
  align-items: center;
  gap: 8px;
  color: var(--app-ink-soft);
  font-size: 12px;
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
  min-width: 44px;
  min-height: 44px;
  flex: none;
  margin: 0;
}

.source-choice:hover {
  border-color: var(--app-accent-line);
}
</style>
