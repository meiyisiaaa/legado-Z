<template>
  <section class="source-library">
    <div class="source-search-row">
      <el-input
        v-model="searchKey"
        class="search"
        :prefix-icon="Search"
        :placeholder="isBookSource ? '按名称、地址或分组筛选' : '按名称或地址筛选'"
        aria-label="筛选源列表"
        clearable
      />
      <span class="source-count">{{ sourcesFiltered.length }} / {{ sources.length }}</span>
    </div>
    <div class="tool">
      <el-button @click="importSourceFile" :icon="Folder">导入</el-button>
      <el-button
        :disabled="sourcesFiltered.length === 0"
        @click="outExport"
        :icon="Download"
      >导出 JSON</el-button>
      <el-button
        v-if="isBookSource"
        :disabled="!selectedJsSource"
        @click="outExportJs"
        :icon="Download"
      >导出 JS</el-button>
      <el-button
        type="danger"
        :icon="Delete"
        @click="deleteSelectSources"
        :disabled="sourceSelect.length === 0"
      >删除所选 {{ sourceSelect.length ? `(${sourceSelect.length})` : '' }}</el-button>
      <el-button
        type="danger"
        plain
        :icon="Delete"
        @click="clearAllSources"
        :disabled="sources.length === 0"
      >清空暂存</el-button>
    </div>
    <div v-if="sourcesFiltered.length === 0" class="list-empty">
      <el-icon><Search /></el-icon>
      <span>{{ sources.length ? '没有匹配的源' : '还没有加载源' }}</span>
      <small>{{ sources.length ? '试试缩短筛选词。' : '使用“拉取源”加载服务中的书源或订阅源。' }}</small>
    </div>
    <el-checkbox-group v-else id="source-list" v-model="sourceUrlSelect">
      <virtual-list
        style="height: 100%; overflow-y: auto; overflow-x: hidden"
        :data-key="(source: Source) => getSourceName(source)"
        :data-sources="sourcesFiltered"
        :data-component="SourceItem"
        :estimate-size="45"
      />
    </el-checkbox-group>
  </section>
</template>

<script setup lang="ts">
import API from '@api'
import { Folder, Delete, Download, Search } from '@element-plus/icons-vue'
import {
  isSourceMatches,
  getSourceUniqueKey,
  getSourceName,
  convertSourcesToMap,
} from '@utils/souce'
import VirtualList from 'vue3-virtual-scroll-list'
import SourceItem from './SourceItem.vue'
import type { Source } from '@/source'

const store = useSourceStore()
const sourceUrlSelect = ref<string[]>([])
const searchKey = ref('')
const sources = computed(() => store.sources)
const isBookSource = /bookSource/i.test(window.location.href)

const isImportedSource = (value: unknown): value is Source => {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return false
  const source = value as Record<string, unknown>
  if (isBookSource) {
    return (
      typeof source.bookSourceUrl === 'string' &&
      source.bookSourceUrl.trim().length > 0 &&
      typeof source.bookSourceName === 'string' &&
      source.bookSourceName.trim().length > 0
    )
  }
  return (
    typeof source.sourceUrl === 'string' &&
    source.sourceUrl.trim().length > 0 &&
    typeof source.sourceName === 'string' &&
    source.sourceName.trim().length > 0
  )
}

/* 筛选源 */
const sourcesFiltered = computed<Source[]>(() => {
  const key = searchKey.value
  if (key === '') return sources.value
  return sources.value.filter(source => isSourceMatches(source, key))
})
// 计算当前筛选关键词下的选中源
const sourceSelect = computed<Source[]>(() => {
  const urls = sourceUrlSelect.value
  if (urls.length == 0) return []
  const sourcesFilteredMap =
    searchKey.value == ''
      ? store.sourcesMap
      : convertSourcesToMap(sourcesFiltered.value)
  return urls.reduce((sources, sourceUrl) => {
    const source = sourcesFilteredMap.get(sourceUrl)
    if (source) sources.push(source)
    return sources
  }, [] as Source[])
})

const deleteSelectSources = () => {
  const sourceSelectValue = sourceSelect.value
  API.deleteSource(sourceSelectValue).then(({ data }) => {
    if (!data.isSuccess) return ElMessage.error(data.errorMsg)
    store.deleteSources(sourceSelectValue)
    const sourceUrlSelectRawValue = toRaw(sourceUrlSelect.value)
    sourceSelectValue.forEach(source => {
      const index = sourceUrlSelectRawValue.indexOf(getSourceUniqueKey(source))
      if (index > -1) sourceUrlSelectRawValue.splice(index, 1)
    })
    sourceUrlSelect.value = sourceUrlSelectRawValue
  })
}
const clearAllSources = () => {
  store.clearAllSource()
  sourceUrlSelect.value = []
}

//导入本地文件
const importSourceFile = () => {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = isBookSource ? '.json,.txt,.js' : '.json,.txt'
  input.addEventListener('change', () => {
    const files = input.files
    if (files === null || files.length === 0) {
      return ElMessage.info('未选择文件')
    }
    const file = files[0]
    const reader = new FileReader()
    reader.readAsText(file)
    reader.onload = () => {
      const content = reader.result
      if (typeof content !== 'string') {
        return ElMessage.error('无法读取文件内容')
      }
      if (file.name.toLowerCase().endsWith('.js')) {
        if (!isBookSource) return ElMessage.error('单文件 JS 仅支持书源')
        const loadingMessage = ElMessage({
          message: '正在安全解析单文件书源…',
          showClose: true,
          duration: 0,
        })
        API.parseBookSource(content)
          .then(({ data }) => {
            if (!data.isSuccess) {
              return ElMessage.error(data.errorMsg || '单文件书源解析失败')
            }
            if (!('bookSourceUrl' in data.data)) {
              return ElMessage.error('解析结果不是书源')
            }
            store.saveSources([data.data])
            searchKey.value = ''
            sourceUrlSelect.value = [data.data.bookSourceUrl]
            ElMessage.success('已解析单文件书源，请检查后推送')
          })
          .catch((error: unknown) => {
            const message = error instanceof Error ? error.message : '请求失败'
            ElMessage.error('单文件书源解析失败: ' + message)
          })
          .finally(() => loadingMessage.close())
        return
      }
      try {
        const jsonData: unknown = JSON.parse(content)
        const importedSources = Array.isArray(jsonData) ? jsonData : [jsonData]
        if (!importedSources.every(isImportedSource)) {
          return ElMessage.error(
            `上传的源格式错误：请提供有效的${isBookSource ? '书源' : '订阅源'}对象或数组`,
          )
        }
        store.saveSources(importedSources)
        searchKey.value = ''
        sourceUrlSelect.value = []
      } catch (e: unknown) {
        ElMessage.error('上传的源格式错误: ' + (e as Error).message)
      }
    }
  })
  input.click()
}

const selectedJsSource = computed(() => {
  if (sourceUrlSelect.value.length !== 1) return undefined
  const source = sourceSelect.value[0]
  return source?.mainJs?.trim() ? source : undefined
})

const outExport = () => {
  const exportFile = document.createElement('a')
  const sources =
      sourceUrlSelect.value.length === 0
        ? sourcesFiltered.value
        : sourceSelect.value,
    sourceType = isBookSource ? 'BookSource' : 'RssSource'

  exportFile.download = `${sourceType}_${Date()
    .replace(/.*?\s(\d+)\s(\d+)\s(\d+:\d+:\d+).*/, '$2$1$3')
    .replace(/:/g, '')}.json`

  const myBlob = new Blob([JSON.stringify(sources, null, 4)], {
    type: 'application/json',
  })
  exportFile.href = window.URL.createObjectURL(myBlob)
  exportFile.click()
  window.URL.revokeObjectURL(exportFile.href) //avoid memory leak
}

const outExportJs = () => {
  const source = selectedJsSource.value
  if (!source?.mainJs) return
  const sourceName = 'bookSourceName' in source ? source.bookSourceName : 'BookSource'
  const safeName = sourceName.replace(/[\\/:*?"<>|]/g, '_') || 'BookSource'
  const exportFile = document.createElement('a')
  const blob = new Blob([source.mainJs], { type: 'text/javascript;charset=utf-8' })
  exportFile.download = `${safeName}.js`
  exportFile.href = window.URL.createObjectURL(blob)
  exportFile.click()
  window.URL.revokeObjectURL(exportFile.href)
}
</script>

<style lang="scss" scoped>
.source-library {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.source-search-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.search {
  min-width: 0;
  flex: 1;
}

.source-count {
  color: var(--app-ink-muted);
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 10px;
  white-space: nowrap;
}

.tool {
  display: flex;
  justify-content: flex-start;
  flex-wrap: wrap;
  gap: 6px;
}

.tool :deep(.el-button) {
  margin: 0;
  border-radius: 9px;
  font-size: 11px;
}

.list-empty {
  min-height: 240px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--app-ink-muted);
  text-align: center;
}

.list-empty > .el-icon {
  color: var(--app-accent);
  font-size: 24px;
}

.list-empty span {
  color: var(--app-ink-soft);
  font-size: 13px;
  font-weight: 650;
}

.list-empty small {
  max-width: 35ch;
  font-size: 11px;
  line-height: 1.6;
}

#source-list {
  min-height: 0;
  height: min(56dvh, 680px);
  flex: 1;
  overflow: hidden;

  :deep(.virtual-list) {
    height: 100%;
    overflow-y: auto;
    overflow-x: hidden;
  }

  :deep(.el-checkbox) {
    margin: 0 0 5px;
    width: 100%;
  }
}

@media (max-width: 620px) {
  .tool {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 5px;
  }

  .tool :deep(.el-button) {
    width: 100%;
    min-width: 0;
    min-height: 38px;
    margin: 0;
    padding: 7px 4px;
    white-space: normal;
    line-height: 1.2;
  }

  #source-list {
    height: 360px;
  }

  .list-empty {
    min-height: 220px;
  }
}
</style>
