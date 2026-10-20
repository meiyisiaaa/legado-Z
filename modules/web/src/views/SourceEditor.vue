<template>
  <div class="editor">
    <source-tab-form
      class="left"
      :class="{ 'is-mobile-active': activeMobilePanel === 'form' }"
      :config="config"
      :role="isNarrowViewport ? 'tabpanel' : undefined"
      :aria-labelledby="isNarrowViewport ? 'editor-panel-form' : undefined"
      :aria-hidden="isNarrowViewport && activeMobilePanel !== 'form' ? 'true' : 'false'"
      :inert="isNarrowViewport && activeMobilePanel !== 'form'"
    />
    <div class="toolbar">
      <div class="editor-utilities">
        <button class="source-switch" type="button" @click="switchSourceType">
          切换为{{ isBookSource ? '订阅源' : '书源' }}
        </button>
        <button
          class="theme-switch"
          type="button"
          :disabled="!themeReady || isSavingTheme"
          :aria-label="isNight ? '切换到浅色主题' : '切换到深色主题'"
          @click="toggleTheme"
        >{{ isNight ? '浅色主题' : '深色主题' }}</button>
        <router-link class="shelf-link" to="/">返回书架</router-link>
      </div>
      <tool-bar />
    </div>
    <source-tab-tools
      id="source-tools"
      class="right"
      :class="{ 'is-mobile-active': activeMobilePanel === 'tools' }"
      :role="isNarrowViewport ? 'tabpanel' : undefined"
      :aria-labelledby="isNarrowViewport ? 'editor-panel-tools' : undefined"
      :aria-hidden="isNarrowViewport && activeMobilePanel !== 'tools' ? 'true' : 'false'"
      :inert="isNarrowViewport && activeMobilePanel !== 'tools'"
    />
    <div
      v-if="isNarrowViewport"
      class="mobile-panel-tabs"
      role="tablist"
      aria-label="源编辑面板"
      @keydown="handlePanelTabKeydown"
    >
      <button
        id="editor-panel-form"
        type="button"
        role="tab"
        aria-controls="source-edit"
        :aria-selected="activeMobilePanel === 'form'"
        :tabindex="activeMobilePanel === 'form' ? 0 : -1"
        @click="activeMobilePanel = 'form'"
      >规则字段</button>
      <button
        id="editor-panel-tools"
        type="button"
        role="tab"
        aria-controls="source-tools"
        :aria-selected="activeMobilePanel === 'tools'"
        :tabindex="activeMobilePanel === 'tools' ? 0 : -1"
        @click="activeMobilePanel = 'tools'"
      >源工具</button>
    </div>
  </div>
</template>
<script setup lang="ts">
import { Moon, Sunny } from '@element-plus/icons-vue'
import bookSourceConfig from '@/config/bookSourceEditConfig'
import rssSourceConfig from '@/config/rssSourceEditConfig'
import '@/assets/sourceeditor.css'
import { useAppTheme } from '@/hooks/useAppTheme'
import { useBookStore } from '@/store'
import type { SourceConfig } from '@/config/sourceConfig'

const bookStore = useBookStore()
const {
  isNight,
  ready: themeReady,
  isSaving: isSavingTheme,
  markReady: markThemeReady,
  toggleTheme,
} = useAppTheme()

let config: SourceConfig
const isBookSource = ref<boolean>(/bookSource/i.test(location.href))
const isNarrowViewport = ref(false)
const activeMobilePanel = ref<'form' | 'tools'>('form')
let mobileViewportQuery: MediaQueryList | undefined
const onMobileViewportChange = (event: MediaQueryListEvent) => {
  isNarrowViewport.value = event.matches
}

provide('isBookSource', isBookSource)
if (isBookSource.value) {
  config = bookSourceConfig as SourceConfig
  document.title = '书源管理'
} else {
  config = rssSourceConfig as SourceConfig
  document.title = '订阅源管理'
}

const switchSourceType = () => {
  const nextRoute = isBookSource.value ? 'rssSource' : 'bookSource'
  const url = new URL(location.href)
  url.searchParams.set('editorRoute', nextRoute)
  url.hash = `/${nextRoute}`
  location.assign(url.toString())
}

const handlePanelTabKeydown = (event: KeyboardEvent) => {
  const panels = ['form', 'tools'] as const
  const currentIndex = panels.indexOf(activeMobilePanel.value)
  let nextIndex = currentIndex

  if (event.key === 'ArrowRight' || event.key === 'ArrowDown') nextIndex = (currentIndex + 1) % panels.length
  else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') nextIndex = (currentIndex + panels.length - 1) % panels.length
  else if (event.key === 'Home') nextIndex = 0
  else if (event.key === 'End') nextIndex = panels.length - 1
  else return

  event.preventDefault()
  activeMobilePanel.value = panels[nextIndex]
  nextTick(() => document.getElementById(`editor-panel-${panels[nextIndex]}`)?.focus())
}

onMounted(async () => {
  mobileViewportQuery = window.matchMedia('(max-width: 980px)')
  isNarrowViewport.value = mobileViewportQuery.matches
  mobileViewportQuery.addEventListener('change', onMobileViewportChange)

  try {
    await bookStore.loadWebConfig()
  } catch {
    ElMessage.warning('阅读服务未响应，当前先使用浅色主题')
  } finally {
    markThemeReady()
  }
})

onUnmounted(() => mobileViewportQuery?.removeEventListener('change', onMobileViewportChange))
</script>
<style lang="scss" scoped>
.editor {
  width: 100%;
  height: 100vh;
  height: 100dvh;
  display: grid;
  grid-template-columns: minmax(0, 1fr) max-content minmax(360px, min(45vw, 560px));
  grid-template-areas: 'form toolbar tools';
  gap: 16px;
  box-sizing: border-box;
  overflow: hidden;
  padding: 0 20px;
  color: var(--app-ink);
  background: var(--app-bg);
}

.left,
.right {
  min-width: 0;
  min-height: 0;
  height: 100%;
  overflow: hidden;
}

.left { grid-area: form; }
.right { grid-area: tools; }

.toolbar {
  grid-area: toolbar;
  width: 150px;
  min-width: 142px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  border-inline: 1px solid var(--app-line);
  padding: 14px 9px;
}

.editor-utilities {
  display: grid;
  gap: 6px;
  border-bottom: 1px solid var(--app-line);
  padding-bottom: 11px;
}

.source-switch,
.theme-switch {
  min-height: 38px;
  border: 1px solid var(--app-line);
  border-radius: 6px;
  padding: 0 8px;
  color: var(--app-ink-soft);
  background: var(--app-panel);
  font: inherit;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}

.source-switch:hover,
.theme-switch:hover:not(:disabled) {
  border-color: var(--app-accent-line);
  color: var(--app-accent-strong);
  background: var(--app-accent-soft);
}

.theme-switch:disabled {
  cursor: wait;
  opacity: .6;
}

.shelf-link {
  min-height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--app-ink-muted);
  font-size: 11px;
  text-decoration: none;
}

.shelf-link:hover { color: var(--app-accent-strong); }

.mobile-panel-tabs { display: none; }

.toolbar :deep(.menu) {
  flex-direction: column;
  align-items: stretch;
}

.toolbar :deep(.menu .el-button) {
  min-height: 38px;
  justify-content: flex-start;
}

.left :deep(#source-edit),
.right :deep(.el-tabs) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.left :deep(.el-tabs__header),
.right :deep(.el-tabs__header) {
  flex: none;
  margin: 0;
}

.left :deep(.el-tabs__content),
.right :deep(.el-tabs__content) {
  min-height: 0;
  flex: 1;
  overflow: auto;
}

.left :deep(.el-tab-pane) {
  min-height: 100%;
  padding: 16px 10px 24px 0;
}

.left :deep(.el-form-item) { margin-bottom: 14px; }
.left :deep(.el-form-item__label) { color: var(--app-ink-soft); font-size: 12px; font-weight: 600; }
.right :deep(.el-tabs__nav) { gap: 2px; }
.right :deep(.el-tabs__item) { min-height: 44px; color: var(--app-ink-muted); font-size: 12px; font-weight: 600; }
.right :deep(.el-tabs__item.is-active) { color: var(--app-accent-strong); }
.right :deep(.el-tab-pane) { min-height: 0; padding: 12px 0; }

@media (max-width: 1023px) {
  .editor {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: auto auto minmax(0, 1fr);
    grid-template-areas: 'toolbar' 'tabs' 'panels';
    gap: 7px;
    padding: 0 12px 10px;
  }

  .toolbar {
    width: auto;
    min-width: 0;
    position: sticky;
    top: 0;
    z-index: 10;
    display: flex;
    gap: 7px;
    border-inline: 0;
    border-bottom: 1px solid var(--app-line);
    padding: 7px 0;
    background: var(--app-bg);
  }

  .editor-utilities {
    display: flex;
    align-items: center;
    gap: 6px;
    border: 0;
    padding: 0;
  }

  .source-switch,
  .theme-switch {
    min-height: 36px;
    padding-inline: 8px;
    white-space: nowrap;
  }

  .toolbar :deep(.menu) {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 4px;
  }

  .toolbar :deep(.menu .el-button) {
    min-width: 0;
    min-height: 38px;
    justify-content: center;
    padding: 4px 5px;
    white-space: normal;
  }

  .toolbar :deep(.menu kbd) { display: none; }

  .mobile-panel-tabs {
    grid-area: tabs;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 5px;
    border-bottom: 1px solid var(--app-line);
    padding-bottom: 7px;
  }

  .mobile-panel-tabs button {
    min-height: 40px;
    border: 1px solid var(--app-line);
    border-radius: 6px;
    color: var(--app-ink-muted);
    background: var(--app-panel);
    font: inherit;
    font-size: 12px;
    font-weight: 620;
    cursor: pointer;
  }

  .mobile-panel-tabs button[aria-selected='true'] {
    border-color: var(--app-accent-line);
    color: var(--app-accent-strong);
    background: var(--app-accent-soft);
  }

  .mobile-panel-tabs button:focus-visible {
    outline: 2px solid var(--app-focus-ring);
    outline-offset: 2px;
  }

  .left,
  .right {
    grid-area: panels;
    min-height: 0;
    opacity: 0;
    visibility: hidden;
    pointer-events: none;
    transform: translateX(8px);
    transition:
      opacity var(--app-duration-panel) ease-out,
      transform var(--app-duration-panel) ease-out,
      visibility 0s linear var(--app-duration-panel);
  }

  .left.is-mobile-active,
  .right.is-mobile-active {
    opacity: 1;
    visibility: visible;
    pointer-events: auto;
    transform: translateX(0);
    transition-delay: 0s;
  }

  .left :deep(.el-tab-pane) { min-height: 100%; }
}

@media (max-width: 620px) {
  .editor { gap: 5px; padding-inline: 8px; }
  .toolbar { gap: 5px; }
  .editor-utilities { gap: 4px; }
  .source-switch,.theme-switch { min-height: 35px; padding-inline: 6px; font-size: 10px; }
  .toolbar :deep(.menu) { gap: 3px; }
  .toolbar :deep(.menu .el-button) { min-height: 36px; gap: 3px; padding-inline: 2px; font-size: 10px; }
}

@media (prefers-reduced-motion: reduce) {
  .left,.right { transition: none; }
}
</style>
