<template>
  <el-tabs id="source-edit">
    <el-tab-pane
      v-for="{ name, children } in Object.values(config)"
      :label="name"
      :key="name"
    >
      <el-form label-position="right" label-width="auto">
        <el-form-item
          v-for="{
            type,
            title,
            namespace,
            id,
            array,
            hint,
            required = false,
          } in children"
          :label="title"
          :key="title"
          :required="required"
        >
          <el-input
            v-if="type == 'String' && typeof namespace == 'undefined'"
            type="textarea"
            v-model="currentSource[id]"
            :placeholder="hint"
            autosize
          />
          <el-input
            v-if="type == 'String' && typeof namespace != 'undefined'"
            type="textarea"
            v-model="currentSource[namespace][id]"
            :placeholder="hint"
            autosize
          />

          <el-switch
            v-if="(type as string) === 'Boolean'"
            v-model="currentSource[id]"
          />

          <el-input-number
            v-if="(type as string) === 'Number'"
            v-model="currentSource[id]"
            :min="0"
          />

          <el-select
            v-if="(type as string) === 'Array'"
            v-model="currentSource[id]"
          >
            <el-option
              v-for="(optionName, index) in array"
              :value="index"
              :key="optionName"
              :label="optionName"
            />
          </el-select>
        </el-form-item>
      </el-form>
    </el-tab-pane>
  </el-tabs>
</template>

<script setup lang="ts">
import type { SourceConfig } from '@/config/sourceConfig'
import { emptyBookSource } from '@/utils/souce'

const store = useSourceStore()
defineProps<{ config: SourceConfig }>()

const currentSource = computed(() => store.currentSource)

watch(
  () => store.currentSource,
  source => {
    if (!('bookSourceUrl' in source)) return
    const sourceValues = source as unknown as Record<string, unknown>
    for (const [namespace, defaults] of Object.entries(emptyBookSource)) {
      if (sourceValues[namespace] == null) {
        sourceValues[namespace] = { ...defaults }
      }
    }
  },
  { immediate: true, flush: 'sync' },
)
/* 
修改currentSource的属性 没有直接修改本身
const { currentSource } = storeToRefs(store);
 */
</script>

<style lang="scss" scoped>
:deep(.el-tab-pane) {
  height: calc(100vh - 55px);
  height: calc(100dvh - 55px);
  padding-top: 15px;
  padding-right: 5px;
  box-sizing: border-box;
  overflow-y: auto;
}
:deep(.el-tabs__header) {
  margin: 0;
}

@media (max-width: 479px) {
  :deep(.el-form-item) {
    display: flex;
    flex-direction: column;
    align-items: stretch;
  }

  :deep(.el-form-item__label) {
    justify-content: flex-start;
    width: 100%;
    height: auto;
    margin: 0 0 4px;
    line-height: 1.4;
    text-align: left;
  }

  :deep(.el-form-item__content) {
    width: 100%;
    min-width: 0;
    margin-left: 0 !important;
  }
}
</style>
