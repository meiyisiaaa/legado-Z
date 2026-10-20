<template>
  <section class="debug-workspace">
    <div class="debug-controls">
      <el-input
        v-if="isBookSource"
        id="debug-key"
        v-model="searchKey"
        placeholder="搜索书名、作者"
        :prefix-icon="Search"
        @keydown.enter="runDebug"
      />
      <div v-else class="debug-rss-note">
        <el-icon><Connection /></el-icon><span>订阅源调试不需要搜索关键词</span>
      </div>
      <div class="debug-actions">
        <el-button v-if="!store.isDebuging" type="primary" :icon="VideoPlay" @click="runDebug">
          开始调试
        </el-button>
        <el-button v-else type="danger" :icon="VideoPause" @click="stopDebug">
          停止
        </el-button>
        <el-button text :icon="Delete" :disabled="!printDebug" @click="printDebug = ''">
          清空日志
        </el-button>
      </div>
    </div>

    <div class="debug-status" :class="{ 'is-active': store.isDebuging, 'has-error': debugStatus === '连接异常' || debugStatus === '运行失败' }">
      <span class="status-dot"></span>
      <span>{{ debugStatus }}</span>
      <span class="source-name">{{ store.currentSourceUrl || '尚未选择源' }}</span>
    </div>

    <el-input
      id="debug-text"
      v-model="printDebug"
      class="debug-output"
      type="textarea"
      readonly
      :rows="20"
      placeholder="调试日志会按时间顺序显示在这里。先选择一个源，再开始调试。"
      aria-label="调试日志"
    />
  </section>
</template>

<script setup lang="ts">
import API from '@api'
import {
  Connection,
  Delete,
  Search,
  VideoPause,
  VideoPlay,
} from '@element-plus/icons-vue'
import type { SocketTask } from '@/api/socketTask'

const store = useSourceStore()

const printDebug = ref('')
const searchKey = ref('')
const debugStatus = ref('等待启动')
let debugTask: SocketTask | undefined
let debugGeneration = 0

const appendDebugMsg = (msg: string) => {
  printDebug.value += msg + '\n'
  nextTick(() => {
    const debugDom = document.querySelector('#debug-text')
    if (debugDom) debugDom.scrollTop = debugDom.scrollHeight
  })
}

const startDebug = async () => {
  const generation = ++debugGeneration
  const previousTask = debugTask
  debugTask = undefined
  previousTask?.cancel()
  printDebug.value = ''
  debugStatus.value = '正在保存草稿'

  try {
    const { data } = await API.saveSource(store.currentSource)
    if (!data.isSuccess) throw new Error(data.errorMsg || '保存源失败')
  } catch (error) {
    if (generation !== debugGeneration) return
    debugStatus.value = '运行失败'
    appendDebugMsg(
      `无法启动调试：${error instanceof Error ? error.message : String(error)}`,
    )
    store.debugFinish()
    return
  }

  if (generation !== debugGeneration || !store.isDebuging) return

  debugStatus.value = '连接中'
  let finished = false
  let socketFailed = false
  const task = API.debug(
    store.currentSourceUrl,
    searchKey.value || store.searchKey,
    message => {
      if (generation !== debugGeneration) return
      debugStatus.value = '正在接收日志'
      appendDebugMsg(message)
    },
    () => {
      finished = true
      if (generation !== debugGeneration) return
      debugTask = undefined
      if (!socketFailed) debugStatus.value = '连接已结束'
      store.debugFinish()
    },
    error => {
      if (generation !== debugGeneration) return
      socketFailed = true
      debugStatus.value = '连接异常'
      appendDebugMsg(
        `WebSocket 连接错误：${error instanceof Error ? error.message : String(error)}`,
      )
    },
  )
  if (!finished && generation === debugGeneration) debugTask = task
}

const runDebug = () => {
  if (store.isDebuging) {
    void startDebug()
  } else {
    store.startDebug()
  }
}

const stopDebug = () => {
  debugGeneration += 1
  const task = debugTask
  debugTask = undefined
  task?.cancel()
  debugStatus.value = '已停止'
  store.debugFinish()
}

const isBookSource = computed(() => /bookSource/i.test(window.location.href))

watch(
  () => store.isDebuging,
  active => {
    if (active) {
      void startDebug()
    } else if (debugTask) {
      const task = debugTask
      debugTask = undefined
      debugGeneration += 1
      task.cancel()
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  debugGeneration += 1
  const task = debugTask
  debugTask = undefined
  task?.cancel()
  store.debugFinish()
})
</script>

<style lang="scss" scoped>
.debug-workspace {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.debug-controls {
  display: flex;
  align-items: center;
  gap: 10px;
}

#debug-key {
  min-width: 0;
  flex: 1;
}

.debug-rss-note {
  min-height: 40px;
  display: flex;
  flex: 1;
  align-items: center;
  gap: 8px;
  color: var(--app-ink-muted);
  font-size: 12px;
}

.debug-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.debug-status {
  min-height: 38px;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--app-line);
  border-radius: 10px;
  padding: 7px 10px;
  color: var(--app-ink-muted);
  background: var(--app-panel-muted);
  font-size: 11px;
}

.status-dot {
  width: 7px;
  height: 7px;
  flex: none;
  border-radius: 50%;
  background: var(--app-ink-muted);
}

.debug-status.is-active .status-dot {
  background: var(--app-accent);
  box-shadow: 0 0 0 4px var(--app-accent-glow);
}

.debug-status.has-error {
  color: var(--app-danger);
  border-color: var(--app-danger-line);
}

.debug-status.has-error .status-dot {
  background: var(--app-danger);
}

.source-name {
  min-width: 0;
  margin-left: auto;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.debug-output {
  min-height: 300px;
}

.debug-output :deep(.el-textarea__inner) {
  min-height: 300px !important;
  height: min(58dvh, 720px);
  resize: vertical;
  border-color: var(--app-line);
  border-radius: 11px;
  color: var(--app-ink-soft);
  background: var(--app-panel-raised);
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 11px;
  line-height: 1.6;
}

@media (max-width: 620px) {
  .debug-controls {
    align-items: stretch;
    flex-direction: column;
  }

  .debug-actions {
    justify-content: flex-end;
  }

  .debug-output :deep(.el-textarea__inner) {
    height: 420px;
    min-height: 280px !important;
  }
}
</style>
