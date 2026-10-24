<template>
  <div class="menu" role="group" aria-label="源管理操作">
    <el-button
      v-for="button in buttons"
      size="default"
      :key="button.name"
      :type="button.type"
      :icon="button.icon"
      :aria-label="button.name"
      :aria-busy="button.name === '保存源' && isSavingSource"
      :disabled="button.name === '保存源' && isSavingSource"
      :loading="button.name === '保存源' && isSavingSource"
      @click="button.action"
    >
      <span>{{ button.name }}</span>
      <kbd v-if="button.hotKeys.length">{{ button.hotKeys.join(' + ') }}</kbd>
    </el-button>
    <el-button size="default" :icon="Setting" @click="() => (hotkeysDialogVisible = true)"
      >快捷键设置</el-button
    >
  </div>
  <el-dialog
    class="hotkeys-dialog"
    v-model="hotkeysDialogVisible"
    :show-close="false"
    :before-close="stopRecordKeyDown"
  >
    <template #header="{ titleClass, titleId }">
      <div class="hotkeys-header flex-space-between">
        <div :id="titleId" :class="titleClass">
          快捷键设置
          <span v-if="recordKeyDowning">
            <el-text> / 录入中 </el-text>
          </span>
        </div>
        <el-button
          :disabled="recordKeyDowning"
          @click="saveHotKeys"
          :icon="CircleCheckFilled"
          >保存</el-button
        >
      </div>
    </template>

    <div class="hotkeys-settings flex-column-center">
      <div
        v-for="(button, buttonIndex) in buttons"
        :key="button.name"
        class="hotkeys-item flex-space-between"
      >
        <span class="title"
          ><el-text>{{ button.name }}</el-text></span
        >
        <div class="hotkeys-item__content">
          <div v-for="(key, hotKeysIndex) in button.hotKeys" :key="key">
            <kbd>{{ key }}</kbd>
            <span v-if="hotKeysIndex + 1 < button.hotKeys.length">
              <el-text>+</el-text>
            </span>
          </div>
          <span v-if="button.hotKeys.length == 0">未设置</span>
        </div>
        <el-button
          :disabled="recordKeyDowning"
          text
          :icon="Edit"
          @click="recordKeyDown(buttonIndex)"
          >编辑</el-button
        >
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import API from '@api'
import {
  CircleCheckFilled,
  Delete,
  DocumentAdd,
  Edit,
  EditPen,
  Promotion,
  Refresh,
  RefreshLeft,
  Setting,
  VideoPlay,
} from '@element-plus/icons-vue'
import type { Component } from 'vue'
import hotkeys from 'hotkeys-js'
import { getSourceName, isInvaildSource, normalizeSource } from '../utils/souce'

const store = useSourceStore()
const pull = () => {
  const loadingMsg = ElMessage({
    message: '加载中……',
    showClose: true,
    duration: 0,
  })
  API.getSources()
    .then(({ data }) => {
      if (data.isSuccess) {
        store.changeTabName('editList')
        store.saveSources(data.data)
        ElMessage({
          message: `成功拉取${data.data.length}条源`,
          type: 'success',
        })
      } else {
        ElMessage({
          message: data.errorMsg ?? '后端错误',
          type: 'error',
        })
      }
    })
    .finally(() => loadingMsg.close())
}

const push = () => {
  const sources = store.sources
  store.changeTabName('editList')
  if (sources.length === 0) {
    return ElMessage({
      message: '空空如也',
      type: 'info',
    })
  }
  ElMessage({
    message: '正在推送中',
    type: 'info',
  })
  API.saveSources(sources).then(({ data }) => {
    if (data.isSuccess) {
      const okData = data.data
      if (Array.isArray(okData)) {
        let failMsg = ``
        if (sources.length > okData.length) {
          failMsg = '\n推送失败的源将用红色字体标注!'
          store.setPushReturnSources(okData)
        }
        ElMessage({
          message: `批量推送源到「阅读3.0APP」\n共计: ${
            sources.length
          } 条\n成功: ${okData.length} 条\n失败: ${
            sources.length - okData.length
          } 条${failMsg}`,
          type: sources.length > okData.length ? 'warning' : 'success',
        })
      }
    } else {
      ElMessage({
        message: `批量推送源失败!\nErrorMsg: ${data.errorMsg}`,
        type: 'error',
      })
    }
  })
}

const conver2Tab = () => {
  store.changeTabName('editTab')
  store.changeEditTabSource(store.currentSource)
}
const conver2Source = () => {
  store.changeCurrentSource(store.editTabSource)
}

const undo = () => {
  store.editHistoryUndo()
}

const clearEdit = () => {
  store.clearEdit()
  ElMessage({
    message: '已清除',
    type: 'success',
  })
}

const redo = () => {
  store.clearEdit()
  store.clearAllHistory()
  ElMessage({
    message: '已清除所有历史记录',
    type: 'success',
  })
}

const isSavingSource = ref(false)
const saveSource = async () => {
  if (isSavingSource.value) return
  const source = store.currentSource
  if (isInvaildSource(source)) {
    normalizeSource(source)
    const sourceName = getSourceName(source)
    isSavingSource.value = true
    try {
      const { data } = await API.saveSource(source)
      if (data.isSuccess) {
        ElMessage({
          message: `源《${sourceName}》已成功保存到「阅读3.0APP」`,
          type: 'success',
        })
        //save to store
        store.saveCurrentSource()
      } else {
        ElMessage({
          message: `源《${sourceName}》保存失败!\nErrorMsg: ${data.errorMsg}`,
          type: 'error',
        })
      }
    } catch (error) {
      ElMessage({
        message: `源《${sourceName}》保存失败!\nErrorMsg: ${error instanceof Error ? error.message : '请求失败'}`,
        type: 'error',
      })
    } finally {
      isSavingSource.value = false
    }
  } else {
    ElMessage({
      message: `请检查<必填>项是否全部填写`,
      type: 'error',
    })
  }
}

const debug = () => {
  store.startDebug()
}

const buttons = ref<{
  name: string
  hotKeys: string[]
  action: () => void
  icon: Component
  type?: 'primary' | 'danger'
}[]>([
  { name: '推送源', hotKeys: [], action: push, icon: Promotion, type: 'primary' },
  { name: '拉取源', hotKeys: [], action: pull, icon: Refresh },
  { name: '生成源', hotKeys: [], action: conver2Tab, icon: DocumentAdd },
  { name: '编辑源', hotKeys: [], action: conver2Source, icon: EditPen },
  { name: '清空表单', hotKeys: [], action: clearEdit, icon: Delete, type: 'danger' },
  { name: '撤销操作', hotKeys: [], action: undo, icon: RefreshLeft },
  { name: '清空历史', hotKeys: [], action: redo, icon: Delete },
  { name: '调试源', hotKeys: [], action: debug, icon: VideoPlay },
  { name: '保存源', hotKeys: [], action: saveSource, icon: CircleCheckFilled, type: 'primary' },
])
const hotkeysDialogVisible = ref(true)

const recordKeyDowning = ref(false)

const recordKeyDownIndex = ref(-1)

const stopRecordKeyDown = () => {
  if (!recordKeyDowning.value) {
    hotkeysDialogVisible.value = false
  }
  recordKeyDowning.value = false
}

watch(
  hotkeysDialogVisible,
  visibale => {
    if (!visibale) {
      hotkeys.unbind('*')
      readHotkeysConfig()
      bindHotKeys()
      return
    }
    readHotkeysConfig()
    hotkeys.unbind()
    /**监听按键 */
    hotkeys('*', event => {
      event.preventDefault()
      const pressedKeys = hotkeys.getPressedKeyString()
      if (pressedKeys.length == 1 && pressedKeys[0] == 'esc') {
        //单独按下esc 不录入
        return
      }
      if (recordKeyDowning.value && recordKeyDownIndex.value > -1)
        buttons.value[recordKeyDownIndex.value].hotKeys = pressedKeys
    })
  },
  { immediate: true },
)

const recordKeyDown = (index: number) => {
  recordKeyDowning.value = true
  ElMessage({
    message: '按ESC键或者点击空白处结束录入',
    type: 'info',
  })
  buttons.value[index].hotKeys = []
  recordKeyDownIndex.value = index
}

const saveHotKeys = () => {
  const hotKeysConfig: string[][] = []
  buttons.value.forEach(({ hotKeys }) => {
    hotKeysConfig.push(hotKeys)
  })
  saveHotkeysConfig(hotKeysConfig)
  hotkeysDialogVisible.value = false
}

const bindHotKeys = () => {
  // hotkeys默认过滤INPUT SELECT TEXTAREA
  hotkeys.filter = () => true
  buttons.value.forEach(({ hotKeys, action }) => {
    if (hotKeys.length == 0) return
    hotkeys(hotKeys.join('+'), event => {
      event.preventDefault()
      action.call(null)
    })
  })
}
const saveHotkeysConfig = (config: string[][]) => {
  localStorage.setItem('legado_web_hotkeys', JSON.stringify(config))
}

/**
 * 读取快捷键配置
 * @return 是否成功读取配置
 */
function readHotkeysConfig() {
  try {
    const localStorageConfig = localStorage.getItem('legado_web_hotkeys')
    if (localStorageConfig === null) return false
    const config = JSON.parse(localStorageConfig)
    if (!Array.isArray(config) || config.length == 0) return false
    buttons.value.forEach((button, index) => (button.hotKeys = config[index]))
    return true
  } catch {
    ElMessage({ message: '快捷键配置错误', type: 'error' })
    localStorage.removeItem('legado_web_hotkeys')
  }
  return false
}

onMounted(() => {
  /**读取热键配置 */
  if (readHotkeysConfig()) {
    hotkeysDialogVisible.value = false
  }
})
</script>

<style lang="scss" scoped>
.flex-space-between {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.flex-column-center {
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.menu {
  min-width: 0;
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.menu :deep(.el-button) {
  min-height: 38px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  border-radius: 9px;
  padding: 0 10px;
  font-size: 11px;
  font-weight: 650;
}

.menu :deep(kbd) {
  border: 1px solid var(--app-line);
  border-radius: 4px;
  padding: 1px 4px;
  color: var(--app-ink-muted);
  background: var(--app-panel);
  font: 9px ui-monospace, SFMono-Regular, Consolas, monospace;
}

@media (max-width: 1023px) {
  .menu {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 4px;
  }

  .menu :deep(.el-button) {
    width: auto;
    min-width: 0;
    min-height: 40px;
    margin: 0;
    padding: 6px 3px;
    white-space: normal;
    line-height: 1.25;
  }

  .menu :deep(kbd) {
    display: none;
  }
}

.hotkeys-item {
  .title {
    width: 5em;
    display: flex;
    justify-content: flex-end;
    margin-right: 1em;
  }
  .hotkeys-item__content {
    display: flex;
    flex-wrap: wrap;
    flex: 1;
    div {
      margin-bottom: 1em;
    }
    span {
      margin: 0.5em;
    }
  }
}

@media (max-width: 479px) {
  :global(.hotkeys-dialog) {
    width: calc(100vw - 24px) !important;
    max-width: 560px;
  }

  :global(.hotkeys-dialog .el-dialog__body) {
    max-height: calc(100dvh - 160px);
    overflow-y: auto;
  }

  .hotkeys-header {
    gap: 8px;
  }

  .hotkeys-item {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr) auto;
    gap: 8px;
    align-items: center;

    .title {
      width: auto;
      justify-content: flex-start;
      margin-right: 0;
    }

    .hotkeys-item__content {
      min-width: 0;
      justify-content: flex-start;

      div {
        margin-bottom: 0;
      }
    }
  }
}
</style>
