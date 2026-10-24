<template>
  <el-input
    id="source-json"
    aria-label="当前源 JSON 草稿"
    v-model="sourceString"
    type="textarea"
    placeholder="这里是当前源的 JSON 草稿，可直接导入“阅读”APP"
    :rows="24"
    @change="update"
    style="margin-bottom: 4px"
  ></el-input>
</template>
<script setup lang="ts">
import { useSourceStore } from '@/store'

const store = useSourceStore()
const sourceString = ref('')
const update = async (string: string) => {
  try {
    store.changeEditTabSource(JSON.parse(string))
  } catch {
    ElMessage({
      message: '粘贴的源格式错误',
      type: 'error',
    })
  }
}

watchEffect(async () => {
  const source = store.editTabSource
  if (Object.keys(source).length > 0) {
    sourceString.value = JSON.stringify(source, null, 4)
  } else {
    sourceString.value = ''
  }
})
</script>
<style lang="scss" scoped>
:deep(.el-input) {
  width: 100%;
}
:deep(#source-json) {
  min-height: 300px;
  height: min(58dvh, 720px);
}

:deep(.el-textarea__inner) {
  min-height: 300px !important;
  height: 100%;
  resize: vertical;
  border-color: var(--app-line);
  border-radius: 11px;
  color: var(--app-ink-soft);
  background: var(--app-panel-raised);
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 11px;
  line-height: 1.55;
}

@media (max-width: 620px) {
  :deep(#source-json),
  :deep(.el-textarea__inner) {
    min-height: 360px !important;
    height: 52dvh;
  }
}
</style>
