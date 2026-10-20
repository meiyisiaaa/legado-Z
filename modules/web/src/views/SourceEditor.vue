<template>
  <div class="editor">
    <source-tab-form class="left" :config="config" />
    <div class="toolbar">
      <tool-bar />
    </div>
    <source-tab-tools class="right" />
  </div>
</template>
<script setup lang="ts">
import bookSourceConfig from '@/config/bookSourceEditConfig'
import rssSourceConfig from '@/config/rssSourceEditConfig'
import '@/assets/sourceeditor.css'
import { useDark } from '@vueuse/core'
import type { SourceConfig } from '@/config/sourceConfig'

useDark()

let config: SourceConfig
const isBookSource = ref<boolean>(/bookSource/i.test(location.href))
provide('isBookSource', isBookSource)
if (isBookSource.value) {
  config = bookSourceConfig as SourceConfig
  document.title = '书源管理'
} else {
  config = rssSourceConfig as SourceConfig
  document.title = '订阅源管理'
}
</script>
<style lang="scss" scoped>
.editor {
  display: grid;
  grid-template-columns: minmax(0, 1fr) max-content minmax(360px, min(45vw, 560px));
  gap: 16px;
  box-sizing: border-box;
  height: 100vh;
  height: 100dvh;
  padding: 0 20px;
  overflow: hidden;

  .left,
  .right {
    min-width: 0;
    min-height: 0;
    margin: 0;
  }

  .right {
    width: auto;
  }
}

@media (max-width: 1023px) {
  .editor {
    display: flex;
    flex-direction: column;
    gap: 0;
    height: auto;
    min-height: 100vh;
    min-height: 100dvh;
    padding: 0;
    overflow: visible;

    .toolbar {
      order: -1;
      position: sticky;
      top: 0;
      z-index: 10;
      width: 100%;
      box-sizing: border-box;
      background: var(--el-bg-color);
      border-bottom: 1px solid var(--el-border-color-light);
    }

    .left,
    .right {
      flex: none;
      width: 100%;
      height: 100vh;
      height: 100dvh;
    }

    :deep(.el-tabs__header) {
      position: static;
      top: auto;
    }
  }
}
</style>
