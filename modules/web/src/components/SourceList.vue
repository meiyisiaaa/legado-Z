<template>
  <el-input
    v-model="searchKey"
    class="search"
    :prefix-icon="Search"
    placeholder="筛选源"
  />
  <div class="tool">
    <el-button @click="importSourceFile" :icon="Folder">打开</el-button>
    <el-button
      :disabled="sourcesFiltered.length === 0"
      @click="outExport"
      :icon="Download"
    >
      导出</el-button
    >
    <el-button
      v-if="isBookSource"
      :disabled="!selectedJsSource"
      @click="outExportJs"
      :icon="Download"
    >
      导出 JS</el-button
    >
    <el-button
      type="danger"
      :icon="Delete"
      @click="deleteSelectSources"
      :disabled="sourceSelect.length === 0"
      >删除</el-button
    >
    <el-button
      type="danger"
      :icon="Delete"
      @click="clearAllSources"
      :disabled="sources.length === 0"
      >清空</el-button
    >
  </div>
  <el-checkbox-group id="source-list" v-model="sourceUrlSelect">
    <virtual-list
      style="height: 100%; overflow-y: auto; overflow-x: hidden"
      :data-key="(source: Source) => getSourceName(source)"
      :data-sources="sourcesFiltered"
      :data-component="SourceItem"
      :estimate-size="45"
    />
  </el-checkbox-group>
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
        const jsonData = JSON.parse(content)
        store.saveSources(jsonData)
      } catch (e: unknown) {
        ElMessage.error('上传的源格式错误: ' + (e as Error).message)
      }
    }
  })
  input.click()
}

const isBookSource = /bookSource/i.test(window.location.href)
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
.tool {
  display: flex;
  margin: 4px 0;
  justify-content: center;
}

#source-list {
  margin-top: 6px;
  height: calc(100vh - 112px - 7px);
  :deep(.el-checkbox) {
    margin-bottom: 4px;
    width: 100%;
  }
}
</style>
