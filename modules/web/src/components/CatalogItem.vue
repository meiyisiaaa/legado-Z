<template>
  <div class="wrapper">
    <button
      v-for="cata in catas"
      class="cata-text"
      :key="cata.url"
      :class="{ selected: isSelected(cata.index) }"
      type="button"
      :aria-current="isSelected(cata.index) ? 'true' : undefined"
      @click="gotoChapter(cata)"
    >
      {{ cata.title }}
    </button>
  </div>
</template>
<script setup lang="ts">
import type { BookChapter } from '@/book'

const props = defineProps<{
  index: number
  source: BookChapter | { index: number; catas: BookChapter[] }
  gotoChapter: (chapter: BookChapter) => void
  currentChapterIndex: number
}>()

const isSelected = (idx: number) => {
  return idx == props.currentChapterIndex
}

// PC端 一个虚拟列表中有两个章节
const catas = computed(() => {
  const source = props.source
  if ('catas' in source) return source.catas
  return [props.source as BookChapter]
})
</script>

<style lang="scss" scoped>
.selected {
  color: #eb4259;
}
.wrapper {
  display: flex;

  .cata-text {
    width: 100%;
    border: 0;
    padding: 0;
    margin-right: 26px;
    overflow: hidden;
    color: inherit;
    background: transparent;
    font: inherit;
    text-align: left;
    white-space: nowrap;
    text-overflow: ellipsis;
    cursor: pointer;
  }
}
</style>
