<template>
  <div class="editor-page" :class="{ 'is-night': isNight }">
    <header class="editor-header">
      <router-link class="editor-brand" to="/">
        <span class="brand-mark"><el-icon><Reading /></el-icon></span>
        <span>阅读<small>LEGADO</small></span>
      </router-link>
      <div class="editor-title">
        <h1>{{ isBookSource ? '书源工作台' : '订阅源工作台' }}</h1>
        <span>{{ isBookSource ? '规则编辑 · 检查 · 调试 · 管理' : '规则编辑 · 调试 · 管理' }}</span>
      </div>
      <div class="editor-header-actions">
        <button class="mode-switch" type="button" @click="switchSourceType">
          <el-icon><Switch /></el-icon>
          <span>{{ isBookSource ? '订阅源' : '书源' }}</span>
        </button>
        <button class="theme-toggle" type="button" :disabled="!themeReady || isSavingTheme" @click="toggleTheme">
          <el-icon><Sunny v-if="isNight" /><Moon v-else /></el-icon>
          <span>{{ isNight ? '浅色' : '深色' }}</span>
        </button>
        <router-link class="back-shelf" to="/">
          <el-icon><Collection /></el-icon><span>返回书架</span>
        </router-link>
      </div>
    </header>

    <div class="editor-toolbar">
      <div class="toolbar-caption"><span class="live-dot"></span><span>编辑操作</span></div>
      <tool-bar />
    </div>

    <div
      v-if="isNarrowViewport"
      class="mobile-panel-tabs"
      role="tablist"
      aria-label="源工作台面板"
      @keydown="handlePanelTabKeydown"
    >
      <button
        id="editor-mobile-tab-form"
        type="button"
        role="tab"
        aria-controls="editor-form-panel"
        :aria-selected="activeMobilePanel === 'form'"
        :tabindex="activeMobilePanel === 'form' ? 0 : -1"
        @click="activeMobilePanel = 'form'"
      >
        规则编辑
      </button>
      <button
        id="editor-mobile-tab-tools"
        type="button"
        role="tab"
        aria-controls="editor-tools-panel"
        :aria-selected="activeMobilePanel === 'tools'"
        :tabindex="activeMobilePanel === 'tools' ? 0 : -1"
        @click="activeMobilePanel = 'tools'"
      >
        工具与调试
      </button>
    </div>

    <main class="editor-grid">
      <section
        id="editor-form-panel"
        class="editor-panel form-panel"
        :class="{ 'is-mobile-active': activeMobilePanel === 'form' }"
        :role="isNarrowViewport ? 'tabpanel' : undefined"
        :aria-labelledby="isNarrowViewport ? 'editor-mobile-tab-form' : undefined"
        :aria-hidden="isNarrowViewport && activeMobilePanel !== 'form' ? 'true' : 'false'"
        :inert="isNarrowViewport && activeMobilePanel !== 'form'"
      >
        <div class="panel-heading">
          <div><h2>规则配置</h2><span>按字段编辑，变更会保留在当前草稿中</span></div>
          <el-icon><EditPen /></el-icon>
        </div>
        <source-tab-form class="editor-form" :config="config" />
      </section>
      <aside
        id="editor-tools-panel"
        class="editor-panel tools-panel"
        :class="{ 'is-mobile-active': activeMobilePanel === 'tools' }"
        :role="isNarrowViewport ? 'tabpanel' : undefined"
        :aria-labelledby="isNarrowViewport ? 'editor-mobile-tab-tools' : undefined"
        :aria-hidden="isNarrowViewport && activeMobilePanel !== 'tools' ? 'true' : 'false'"
        :inert="isNarrowViewport && activeMobilePanel !== 'tools'"
      >
        <source-tab-tools />
      </aside>
    </main>
  </div>
</template>
<script setup lang="ts">
import {
  Collection,
  EditPen,
  Moon,
  Reading,
  Sunny,
  Switch,
} from '@element-plus/icons-vue'
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
  nextTick(() => document.getElementById(`editor-mobile-tab-${panels[nextIndex]}`)?.focus())
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
.editor-page {
  min-height: 100vh;
  min-height: 100dvh;
  color: var(--app-ink);
  background: var(--app-bg);
}

.editor-header {
  min-height: 72px;
  display: flex;
  align-items: center;
  gap: 22px;
  border-bottom: 1px solid var(--app-line);
  padding: 10px clamp(16px, 3vw, 42px);
  background: var(--app-panel);
}

.editor-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--app-ink);
  font-size: 15px;
  font-weight: 730;
  text-decoration: none;
}

.brand-mark {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  color: var(--app-accent-strong);
  background: var(--app-accent-soft);
  font-size: 18px;
}

.editor-brand small {
  display: block;
  margin-top: 1px;
  color: var(--app-ink-muted);
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 9px;
  letter-spacing: 0.12em;
}

.editor-title {
  min-width: 200px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-left: 22px;
  border-left: 1px solid var(--app-line);
}

.editor-title h1 {
  margin: 0;
  font-size: 16px;
  font-weight: 740;
}

.editor-title span {
  color: var(--app-ink-muted);
  font-size: 11px;
}

.editor-header-actions {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-left: auto;
}

.mode-switch,
.theme-toggle,
.back-shelf {
  min-height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  border: 1px solid var(--app-line);
  border-radius: 10px;
  padding: 0 11px;
  color: var(--app-ink-soft);
  background: var(--app-panel-raised);
  font: inherit;
  font-size: 12px;
  font-weight: 650;
  text-decoration: none;
  cursor: pointer;
}

.mode-switch:hover,
.theme-toggle:hover:not(:disabled),
.back-shelf:hover {
  border-color: var(--app-accent-line);
  color: var(--app-accent-strong);
}

.theme-toggle:disabled {
  cursor: wait;
  opacity: 0.62;
}

.editor-toolbar {
  min-height: 64px;
  display: flex;
  align-items: center;
  gap: 14px;
  border-bottom: 1px solid var(--app-line);
  padding: 8px clamp(16px, 3vw, 42px);
  background: var(--app-panel);
}

.toolbar-caption {
  min-width: max-content;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding-right: 14px;
  border-right: 1px solid var(--app-line);
  color: var(--app-ink-muted);
  font-size: 11px;
  font-weight: 680;
}

.live-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--app-accent);
}

.mobile-panel-tabs {
  display: none;
}

.editor-grid {
  width: min(calc(100% - 40px), 1520px);
  min-height: calc(100vh - 136px);
  min-height: calc(100dvh - 136px);
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(340px, 0.85fr);
  align-items: stretch;
  gap: 14px;
  margin: 0 auto;
  padding: 18px 0 24px;
}

.editor-panel {
  min-width: 0;
  min-height: 540px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--app-line);
  border-radius: 15px;
  background: var(--app-panel);
  box-shadow: var(--app-shadow-soft);
}

.panel-heading {
  min-height: 62px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid var(--app-line);
  padding: 12px 17px;
  color: var(--app-accent-strong);
}

.panel-heading h2 {
  margin: 0;
  color: var(--app-ink);
  font-size: 14px;
  font-weight: 730;
}

.panel-heading span {
  display: block;
  margin-top: 4px;
  color: var(--app-ink-muted);
  font-size: 10px;
}

.editor-form {
  min-height: 0;
  flex: 1;
  overflow: auto;
}

.form-panel :deep(#source-edit) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.form-panel :deep(.el-tabs__header) {
  flex: none;
  margin: 0;
  padding: 0 14px;
}

.form-panel :deep(.el-tabs__content) {
  min-height: 0;
  flex: 1;
  overflow: auto;
}

.form-panel :deep(.el-tab-pane) {
  height: 100%;
  padding: 16px 18px 22px;
  overflow: auto;
}

.form-panel :deep(.el-form-item) {
  margin-bottom: 16px;
}

.form-panel :deep(.el-form-item__label) {
  color: var(--app-ink-soft);
  font-size: 12px;
  font-weight: 630;
}

.tools-panel :deep(.el-tabs) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.tools-panel :deep(.el-tabs__header) {
  flex: none;
  margin: 0;
  padding: 0 10px;
}

.tools-panel :deep(.el-tabs__content) {
  min-height: 0;
  flex: 1;
  padding: 12px;
}

.tools-panel :deep(.el-tab-pane) {
  min-height: 0;
}

@media (max-width: 980px) {
  .mobile-panel-tabs {
    width: min(calc(100% - 40px), 1520px);
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 5px;
    margin: 14px auto 0;
    padding: 4px;
    border: 1px solid var(--app-line);
    border-radius: 13px;
    background: var(--app-panel);
  }

  .mobile-panel-tabs button {
    min-height: 42px;
    border: 1px solid transparent;
    border-radius: 9px;
    padding: 7px 10px;
    color: var(--app-ink-muted);
    background: transparent;
    font: inherit;
    font-size: 12px;
    font-weight: 680;
    cursor: pointer;
  }

  .mobile-panel-tabs button[aria-selected='true'] {
    border-color: var(--app-accent-line);
    color: var(--app-accent-strong);
    background: var(--app-accent-soft);
    box-shadow: 0 4px 12px rgb(8 130 139 / 10%);
  }

  .mobile-panel-tabs button:focus-visible {
    outline: 2px solid var(--app-accent);
    outline-offset: 2px;
  }

  .editor-grid {
    grid-template-columns: minmax(0, 1fr);
    min-height: 0;
  }

  .editor-panel {
    grid-area: 1 / 1;
    min-height: 460px;
    opacity: 0;
    visibility: hidden;
    pointer-events: none;
    transform: translateX(10px);
    transition:
      opacity 360ms cubic-bezier(0.2, 0.7, 0.2, 1),
      transform 360ms cubic-bezier(0.2, 0.7, 0.2, 1),
      visibility 0s linear 360ms;
  }

  .editor-panel.is-mobile-active {
    opacity: 1;
    visibility: visible;
    pointer-events: auto;
    transform: translateX(0);
  }

  .form-panel :deep(.el-tabs__content) {
    min-height: 360px;
  }

  .form-panel :deep(.el-tab-pane) {
    height: auto;
    min-height: 360px;
  }

  .tools-panel {
    min-height: 520px;
  }
}

@media (max-width: 620px) {
  .editor-header {
    flex-wrap: wrap;
    gap: 8px 12px;
    padding: 10px 14px;
  }

  .editor-brand {
    margin-right: auto;
  }

  .editor-title {
    order: 3;
    width: 100%;
    border: 0;
    padding: 4px 0 2px;
  }

  .editor-title h1 {
    font-size: 15px;
  }

  .editor-header-actions {
    gap: 4px;
  }

  .mode-switch,
  .theme-toggle,
  .back-shelf {
    min-height: 36px;
    gap: 5px;
    padding: 0 8px;
    font-size: 11px;
  }

  .editor-toolbar {
    align-items: flex-start;
    flex-direction: column;
    gap: 5px;
    padding: 9px 12px;
  }

  .toolbar-caption {
    border: 0;
    padding-right: 0;
  }

  .editor-grid {
    width: calc(100% - 20px);
    gap: 10px;
    padding-top: 10px;
  }

  .mobile-panel-tabs {
    width: calc(100% - 20px);
    margin-top: 10px;
  }

  .editor-panel {
    min-height: 420px;
    border-radius: 12px;
  }

  .panel-heading {
    min-height: 54px;
    padding: 10px 13px;
  }

  .panel-heading span {
    max-width: 270px;
    line-height: 1.4;
  }

  .form-panel :deep(.el-tab-pane) {
    padding: 12px 12px 18px;
  }

  .form-panel :deep(.el-form-item) {
    display: block;
    margin-bottom: 14px;
  }

  .form-panel :deep(.el-form-item__label) {
    display: block;
    justify-content: flex-start;
    width: 100%;
    height: auto;
    margin: 0 0 5px;
    line-height: 1.4;
    text-align: left;
  }

  .form-panel :deep(.el-form-item__content) {
    margin-left: 0 !important;
  }

  .tools-panel {
    min-height: 500px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .editor-panel {
    transition: none;
  }
}
</style>
