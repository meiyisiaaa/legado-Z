<template>
  <div class="title" data-chapterpos="0" ref="titleRef">{{ title }}</div>
  <div
    v-for="(para, index) in contents"
    :key="index"
    ref="paragraphRef"
    :data-chapterpos="chapterPos[index]"
  >
    <img
      class="full"
      v-if="/^\s*<img[^>]*src[^>]+>$/.test(String(para))"
      :src="getImageSrc(para)"
      :alt="`${title} 插图`"
      @error.once="proxyImage"
      loading="lazy"
    />
    <p v-else :style="{ fontFamily, fontSize }" v-html="replaceImage(para)" @error.capture="handleImgLoadError" />
  </div>
</template>

<script setup lang="ts">
import { isLegadoUrl, lazyRegex } from '@/utils/utils'
import API from '@api'
import jump from '@/plugins/jump'
import type { webReadConfig } from '@/web'

const store = useBookStore()
const readWidth = computed(() => store.config.readWidth)
const lineImgWidth = computed(() => store.config.fontSize * 2)
const bookUrl = computed(() => store.readingBook.bookUrl)

const props = defineProps<{
  chapterIndex: number
  contents: Array<string>
  title: string
  spacing: webReadConfig['spacing']
  fontFamily: string
  fontSize: string
}>()

const imgPatternStr = '<img[^>]*src=[\'"]([^\'"]*(?:[\'"][^>]+\\})?)[\'"][^>]*>'
const imgPattern = lazyRegex(imgPatternStr)
const imgPatternAll = lazyRegex(imgPatternStr, 'g')
const imgDataUrlPattern = lazyRegex('data:image[^;]+;base64,[^,]{39,}')

const replaceImage = (content: string) => {
  return content.replace(imgPatternAll(), (match, src) => {
    const image = /\balt\s*=/i.test(match)
      ? match
      : match.replace(/<img\b/i, '<img alt="章节插图"')
    const dataUrl = src.match(imgDataUrlPattern())
    if (dataUrl) {
      return dataUrl[0]
    }
    if (isLegadoUrl(src)) {
      const proxySrc = API.getProxyImageUrl(
        bookUrl.value,
        src,
        lineImgWidth.value,
      )
      return image.replace(src, proxySrc)
    }
    return image
  })
}

const getImageSrc = (content: string) => {
  const src = content.match(imgPattern())![1] //reg tested in template
  const dataUrl = src.match(imgDataUrlPattern())
  if (dataUrl) {
      return dataUrl[0] //现成的base64图片，去掉阅读格式后缀
  }
  if (isLegadoUrl(src))
    return API.getProxyImageUrl(
      bookUrl.value,
      src,
      readWidth.value,
    )
  return src
}
const proxyImage = (event: Event) => {
  /* 获取IMG标签原始的src
    <img src="/test" />
    假设location.href = http://example.com
    event.target.src 返回 http://example.com/test
    (event.target as HTMLImageElement)?.getAttribute("src")  返回/test
  */
  const src = (event.target as HTMLImageElement)?.getAttribute("src")
  if (src != null && src.length > 0) {
    (event.target as HTMLImageElement).src = API.getProxyImageUrl(
      bookUrl.value,
      src,
      readWidth.value,
    )
  }
}

/**
 * 处理传入的IMG标签错误事件，自动替换图片的代理链接
 */
const handleImgLoadError = (event: Event) => {
  const target = event.target
  if (target instanceof HTMLImageElement) {
    const srcUrl = target.getAttribute("src")
    console.log(
      "[ChapterContent]: IMG Load Error, replace src:",
      srcUrl,
      "=>",
      API.getProxyImageUrl(
        bookUrl.value,
        srcUrl ?? "",
        readWidth.value,
      )
    )
    proxyImage(event)
  }
}

const calculateWordCount = (paragraph: string) => {
  //内嵌图片文字为1
  const imagePlaceHolder = ' '
  return paragraph.replace(imgPatternAll(), imagePlaceHolder).length
}
const chapterPos = computed(() => {
  let pos = -1
  return Array.from(props.contents, content => {
    pos += calculateWordCount(content) + 1 //计算上一段的换行符
    return pos
  })
})

const titleRef = ref<HTMLElement>()
const paragraphRef = ref<HTMLParagraphElement[]>()
const scrollToReadedLength = (length: number) => {
  if (length === 0) return
  const paragraphIndex = chapterPos.value.findIndex(
    wordCount => wordCount >= length,
  )
  if (paragraphIndex === -1) return
  nextTick(() => {
    jump(paragraphRef.value![paragraphIndex], {
      duration: 0,
    })
  })
}
defineExpose({
  scrollToReadedLength,
})
let intersectionObserver: IntersectionObserver | null = null
const emit = defineEmits(['readedLengthChange'])
onMounted(() => {
  intersectionObserver = new IntersectionObserver(
    entries => {
      for (const { target, isIntersecting } of entries) {
        if (isIntersecting) {
          emit(
            'readedLengthChange',
            props.chapterIndex,
            parseInt((target as HTMLElement).dataset.chapterpos as string),
          )
        }
      }
    },
    {
      rootMargin: `0px 0px -${window.innerHeight - 24}px 0px`,
    },
  )
  intersectionObserver.observe(titleRef.value!)
  paragraphRef.value!.forEach(element => {
    intersectionObserver!.observe(element)
  })
})

onUnmounted(() => {
  intersectionObserver?.disconnect()
  intersectionObserver = null
})
</script>

<style lang="scss" scoped>
.title {
  max-width: 42ch;
  margin-bottom: 38px;
  color: var(--app-ink);
  font-family:
    'Microsoft YaHei', PingFangSC-Regular, HelveticaNeue-Light,
    'Helvetica Neue Light', sans-serif;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

p {
  display: block;
  word-wrap: break-word;
  color: var(--app-ink);
  /*   word-break: break-all; */
  letter-spacing: calc(v-bind('props.spacing.letter') * 1em);
  line-height: calc(1 + v-bind('props.spacing.line'));
  margin: calc(v-bind('props.spacing.paragraph') * 1em) 0;

  :deep(img) {
    height: 1em;
    max-width: 100%;
  }

  :deep(a) {
    color: var(--app-accent-strong);
    text-decoration-thickness: 1px;
    text-underline-offset: 3px;
  }
}

.full {
  display: block;
  width: 100%;
  height: auto;
  border-radius: 8px;
}
</style>
