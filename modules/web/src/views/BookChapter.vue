<template>
  <div
    class="chapter-wrapper"
    :style="bodyTheme"
    :class="{ night: isNight, day: !isNight }"
    @click="showToolBar = !showToolBar"
  >
    <nav class="tool-bar" :style="leftBarTheme" aria-label="阅读工具" @click.stop>
      <div class="tools">
        <el-popover
          placement="right"
          :width="popupWidth"
          trigger="click"
          :show-arrow="false"
          v-model:visible="popCataVisible"
          popper-class="pop-cata"
        >
          <PopCatalog @getContent="getContent" class="popup" />
          <template #reference>
            <button ref="catalogTrigger" class="tool-icon" type="button" aria-label="打开章节目录" :aria-expanded="popCataVisible">
              <el-icon><List /></el-icon><span class="icon-text">目录</span>
            </button>
          </template>
        </el-popover>
        <el-popover
          placement="right"
          :width="popupWidth"
          trigger="click"
          :show-arrow="false"
          v-model:visible="readSettingsVisible"
          popper-class="pop-setting"
        >
          <read-settings class="popup" />
          <template #reference>
            <button ref="settingsTrigger" class="tool-icon" type="button" :disabled="noPoint" aria-label="阅读设置" :aria-expanded="readSettingsVisible">
              <el-icon><Setting /></el-icon><span class="icon-text">设置</span>
            </button>
          </template>
        </el-popover>
        <button class="tool-icon" type="button" aria-label="返回书架" @click="toShelf">
          <el-icon><Collection /></el-icon><span class="icon-text">书架</span>
        </button>
        <button class="tool-icon" type="button" :disabled="noPoint" aria-label="回到页面顶部" @click="toTop">
          <el-icon><Top /></el-icon><span class="icon-text">顶部</span>
        </button>
        <button
          class="tool-icon"
          type="button"
          :disabled="noPoint"
          aria-label="跳到页面底部"
          @click="toBottom"
        >
          <el-icon><Bottom /></el-icon><span class="icon-text">底部</span>
        </button>
      </div>
    </nav>
    <nav class="read-bar" :style="rightBarTheme" aria-label="章节切换" @click.stop>
      <div class="tools">
        <button
          class="tool-icon"
          type="button"
          :disabled="noPoint"
          aria-label="上一章"
          @click="toPreChapter"
        >
          <el-icon><ArrowLeft /></el-icon><span>上一章</span>
        </button>
        <button
          class="tool-icon"
          type="button"
          :disabled="noPoint"
          aria-label="下一章"
          @click="toNextChapter"
        >
          <span>下一章</span><el-icon><ArrowRight /></el-icon>
        </button>
      </div>
    </nav>
    <main class="chapter" ref="content" :style="chapterTheme" :aria-busy="isLoading">
      <span class="sr-only" role="status" aria-live="polite" aria-atomic="true">
        {{ isLoading ? '正在获取章节内容…' : '' }}
      </span>
      <div v-if="catalogLoadError" class="chapter-error catalog-error" role="alert">
        <strong>无法打开章节</strong>
        <p>{{ catalogLoadError }}</p>
        <button type="button" @click.stop="retryLoadReader">重试</button>
      </div>
      <div class="content">
        <div class="top-bar" ref="top"></div>
        <div
          v-for="data in chapterData"
          :key="data.index"
          :chapterIndex="data.index"
          ref="chapter"
        >
          <div v-if="showContent && data.error" class="chapter-error" role="alert">
            <strong>{{ data.title }}</strong>
            <p>{{ data.error }}</p>
            <button type="button" :aria-label="`重试加载${data.title}`" @click.stop="getContent(data.index)">重试</button>
          </div>
          <chapter-content
            ref="chapterRef"
            :chapterIndex="data.index"
            :contents="data.content"
            :title="data.title"
            :spacing="store.config.spacing"
            :fontSize="fontSize"
            :fontFamily="fontFamily"
            @readedLengthChange="onReadedLengthChange"
            v-if="showContent && !data.error"
          />
        </div>
        <div class="loading" ref="loading"></div>
        <div class="bottom-bar" ref="bottom"></div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import jump from '@/plugins/jump'
import settings from '@/config/themeConfig'
import API from '@api'
import { useLoading } from '@/hooks/loading'
import { useThrottleFn } from '@vueuse/shared'
import { isNullOrBlank } from '@/utils/utils'
import {
  ArrowLeft,
  ArrowRight,
  Bottom,
  Collection,
  List,
  Setting,
  Top,
} from '@element-plus/icons-vue'

const content = ref()
// loading spinner
const { isLoading, loadingWrapper } = useLoading(content, '正在获取信息')
const store = useBookStore()

const {
  catalog,
  popCataVisible,
  readSettingsVisible,
  miniInterface,
  showContent,
  bookProgress,
  theme,
  isNight,
} = storeToRefs(store)

const chapterPos = computed({
  get: () => store.readingBook.chapterPos,
  set: value => (store.readingBook.chapterPos = value),
})
const chapterIndex = computed({
  get: () => store.readingBook.chapterIndex,
  set: value => (store.readingBook.chapterIndex = value),
})
const isSeachBook = computed({
  get: () => store.readingBook.isSeachBook,
  set: value => (store.readingBook.isSeachBook = value),
})

// 当前阅读书籍readingBook持久化
watch(
  () => store.readingBook,
  book => {
    // 保存localStorage
    // localStorage.setItem(book.bookUrl, JSON.stringify(book));
    // 最近阅读
    localStorage.setItem('readingRecent', JSON.stringify(book))
    //保存 sessionStorage
    sessionStorage.setItem('chapterIndex', book.chapterIndex.toString())
    sessionStorage.setItem('chapterPos', book.chapterPos.toString())
  },
  { deep: 1 },
)

// 无限滚动
const infiniteLoading = computed(() => store.config.infiniteLoading)
let scrollObserver: IntersectionObserver | null
const loading = ref()
watchEffect(() => {
  if (!infiniteLoading.value) {
    scrollObserver?.disconnect()
  } else {
    scrollObserver?.observe(loading.value)
  }
})
const loadMore = () => {
  const index = chapterData.value.slice(-1)[0].index
  if (catalog.value.length - 1 > index) {
    getContent(index + 1, false)
    store.saveBookProgress() // 保存的是上一章的进度，不是预载的本章进度
  }
}
// IntersectionObserver回调 底部加载
const onReachBottom = (entries: IntersectionObserverEntry[]) => {
  if (isLoading.value) return
  for (const { isIntersecting } of entries) {
    if (!isIntersecting) return
    loadMore()
  }
}

// 字体
const fontFamily = computed(() => {
  if (store.config.font >= 0) {
    return settings.fonts[store.config.font]
  }
  return store.config.customFontName
})
const fontSize = computed(() => {
  return store.config.fontSize + 'px'
})

// 主题部分
const bodyColor = computed(() => settings.themes[theme.value].body)
const chapterColor = computed(() => settings.themes[theme.value].content)
const popupColor = computed(() => settings.themes[theme.value].popup)

const readWidth = computed(() => {
  if (!miniInterface.value) {
    return store.config.readWidth - 130 + 'px'
  } else {
    return window.innerWidth + 'px'
  }
})
const popupWidth = computed(() => {
  if (!miniInterface.value) {
    return store.config.readWidth - 33
  } else {
    return window.innerWidth - 33
  }
})
const bodyTheme = computed(() => {
  return {
    background: bodyColor.value,
  }
})
const chapterTheme = computed(() => {
  return {
    background: chapterColor.value,
    width: readWidth.value,
  }
})
const showToolBar = ref(false)
const leftBarTheme = computed(() => {
  return {
    background: popupColor.value,
    marginLeft: miniInterface.value
      ? 0
      : -(store.config.readWidth / 2 + 68) + 'px',
    display: miniInterface.value && !showToolBar.value ? 'none' : 'block',
  }
})
const rightBarTheme = computed(() => {
  return {
    background: popupColor.value,
    marginRight: miniInterface.value
      ? 0
      : -(store.config.readWidth / 2 + 52) + 'px',
    display: miniInterface.value && !showToolBar.value ? 'none' : 'block',
  }
})

/**
 * pc移动端判断 最大阅读宽度修正
 * 阅读宽度最小为640px 加上工具栏 68px 52px 取较大值 为 776px
 */
const onResize = () => {
  store.setMiniInterface(window.innerWidth < 776)
  const width = store.config.readWidth /**包含padding */
  checkPageWidth(width)
}
/** 判断阅读宽度是否超出页面或者低于默认值640 */
const checkPageWidth = (readWidth: number) => {
  if (store.miniInterface) return
  if (readWidth < 640) store.config.readWidth = 640
  if (readWidth + 2 * 68 > window.innerWidth) store.config.readWidth -= 160
}
watch(
  () => store.config.readWidth,
  width => checkPageWidth(width),
)
// 顶部底部跳转
const top = ref()
const bottom = ref()
const toTop = () => {
  jump(top.value)
}
const toBottom = () => {
  jump(bottom.value)
}

// 书架路由切换
const router = useRouter()
const toShelf = () => {
  router.push('/')
}

// 获取章节内容
const chapterData = ref<{
  index: number
  content: string[]
  title: string
  error?: string
}[]>([])
const catalogLoadError = ref('')
const noPoint = ref(true)
const showChapterError = (index: number, title: string, error: string) => {
  const chapter = chapterData.value.find(item => item.index === index)
  if (chapter) {
    chapter.content = []
    chapter.error = error
  } else {
    chapterData.value.push({ index, content: [], title, error })
  }
  noPoint.value = false
  store.setShowContent(true)
}
const getContent = (index: number, reloadChapter = true, chapterPos = 0) => {
  if (reloadChapter) {
    //展示进度条
    store.setShowContent(false)
    //强制滚回顶层
    jump(top.value, { duration: 0 })
    //从目录，按钮切换章节时保存进度 预加载时不保存
    saveReadingBookProgressToBrowser(index, chapterPos)
    chapterData.value = []
  }
  const bookUrl = store.readingBook.bookUrl
  const { title, index: chapterIndex } = catalog.value[index]

  void loadingWrapper(
    API.getBookContent(bookUrl, chapterIndex).then(
      res => {
        if (res.data.isSuccess) {
          const data = res.data.data
          const content = data.split(/\n+/)
          chapterData.value.push({ index, content, title })
          if (reloadChapter) toChapterPos(chapterPos)
        } else {
          showChapterError(index, title, res.data.errorMsg || '获取章节内容失败，请重试。')
        }
        store.setContentLoading(true)
        noPoint.value = false
        store.setShowContent(true)
      },
      () => showChapterError(index, title, '获取章节内容失败，请检查阅读服务后重试。'),
    ),
  ).catch(() => showChapterError(index, title, '章节内容无法读取，请重试。'))
}

// 章节进度跳转和计算
const chapter = ref()
const chapterRef = ref()
const toChapterPos = (pos: number) => {
  nextTick(() => {
    if (chapterRef.value.length === 1)
      chapterRef.value[0].scrollToReadedLength(pos)
  })
}

// 60秒保存一次进度
const saveBookProgressThrottle = useThrottleFn(
  () => store.saveBookProgress(),
  60000,
)

const onReadedLengthChange = (index: number, pos: number) => {
  saveReadingBookProgressToBrowser(index, pos)
  saveBookProgressThrottle()
}

// 文档标题
watchEffect(() => {
  document.title = catalog.value[chapterIndex.value]?.title || document.title
})

// 阅读记录保存浏览器
const saveReadingBookProgressToBrowser = (index: number, pos: number) => {
  // 保存pinia
  chapterIndex.value = index
  chapterPos.value = pos
}

// 进度同步
// 返回导航变化 同步请求会在获取书架前完成

/**
 * VisibilityChange https://developer.mozilla.org/zh-CN/docs/Web/API/Document/visibilitychange_event
 * 监听关闭页面 切换tab 返回桌面 等操作
 * 注意不用监听点击链接导航变化 不对Safari<14.5兼容处理
 **/
const onVisibilityChange = () => {
  const _bookProgress = bookProgress.value
  if (document.visibilityState == 'hidden' && _bookProgress) {
    store.saveBookProgress()
  }
}
// 定时同步

// 章节切换
const toNextChapter = () => {
  store.setContentLoading(true)
  const index = chapterIndex.value + 1
  if (typeof catalog.value[index] !== 'undefined') {
    ElMessage({
      message: '下一章',
      type: 'info',
    })
    getContent(index)
    store.saveBookProgress()
  } else {
    ElMessage({
      message: '本章是最后一章',
      type: 'error',
    })
  }
}
const toPreChapter = () => {
  store.setContentLoading(true)
  const index = chapterIndex.value - 1
  if (typeof catalog.value[index] !== 'undefined') {
    ElMessage({
      message: '上一章',
      type: 'info',
    })
    getContent(index)
    store.saveBookProgress()
  } else {
    ElMessage({
      message: '本章是第一章',
      type: 'error',
    })
  }
}

let canJump = true
const catalogTrigger = ref<HTMLButtonElement>()
const settingsTrigger = ref<HTMLButtonElement>()
const isInteractiveKeyTarget = (target: EventTarget | null) =>
  target instanceof Element &&
  target.closest(
    'input, textarea, select, button, a[href], [contenteditable]:not([contenteditable="false"]), [role="button"], [role="textbox"], [role="combobox"], [role="slider"], [role="switch"], [role="checkbox"], [role="radio"], [role="tab"], [role="menuitem"], [role="option"]',
  ) !== null

// 监听方向键
const handleKeyPress = (event: KeyboardEvent) => {
  if (!canJump || popCataVisible.value || readSettingsVisible.value || isInteractiveKeyTarget(event.target)) return
  switch (event.key) {
    case 'ArrowLeft':
      event.stopPropagation()
      event.preventDefault()
      toPreChapter()
      break
    case 'ArrowRight':
      event.stopPropagation()
      event.preventDefault()
      toNextChapter()
      break
    case 'ArrowUp':
      event.stopPropagation()
      event.preventDefault()
      if (document.documentElement.scrollTop === 0) {
        ElMessage.warning('已到达页面顶部')
      } else {
        canJump = false
        jump(0 - document.documentElement.clientHeight + 100, {
          duration: store.config.jumpDuration,
          callback: () => (canJump = true),
        })
      }
      break
    case 'ArrowDown':
      event.stopPropagation()
      event.preventDefault()
      if (
        document.documentElement.clientHeight +
          document.documentElement.scrollTop ===
        document.documentElement.scrollHeight
      ) {
        ElMessage.warning('已到达页面底部')
      } else {
        canJump = false
        jump(document.documentElement.clientHeight - 100, {
          duration: store.config.jumpDuration,
          callback: () => (canJump = true),
        })
      }
      break
  }
}

// 阻止默认滚动事件
const ignoreKeyPress = (event: KeyboardEvent) => {
  if (event.defaultPrevented || (event.target instanceof Element && event.target.closest('[role="dialog"]'))) return
  if (event.key === 'Escape' && (popCataVisible.value || readSettingsVisible.value)) {
    const trigger = readSettingsVisible.value ? settingsTrigger : catalogTrigger
    readSettingsVisible.value = false
    popCataVisible.value = false
    event.preventDefault()
    event.stopPropagation()
    nextTick(() => trigger.value?.focus())
    return
  }
  if (popCataVisible.value || readSettingsVisible.value) return
  if (
    !isInteractiveKeyTarget(event.target) &&
    (event.key === 'ArrowUp' || event.key === 'ArrowDown')
  ) {
    event.preventDefault()
    event.stopPropagation()
  }
}

let readerRequest: {
  book: typeof store.readingBook
  chapterIndex: number
  chapterPos: number
  name: string
} | undefined

const prepareReader = async ({ book, chapterIndex, chapterPos, name }: NonNullable<typeof readerRequest>) => {
  catalogLoadError.value = ''
  try {
    let chapterTitle = ''
    await loadingWrapper(
      (async () => {
        await store.loadWebConfig()
        const chapters = await store.loadWebCatalog(book)
        const currentChapter = chapters[chapterIndex]
        if (!currentChapter) throw new Error('章节目录中没有当前章节')
        chapterTitle = currentChapter.title
      })(),
    )
    await nextTick()

    store.setReadingBook(book)
    getContent(chapterIndex, true, chapterPos)
    window.addEventListener('keyup', handleKeyPress)
    window.addEventListener('keydown', ignoreKeyPress)
    // 兼容Safari < 14
    document.addEventListener('visibilitychange', onVisibilityChange)
    //监听底部加载
    scrollObserver?.disconnect()
    scrollObserver = new IntersectionObserver(onReachBottom, {
      rootMargin: '-100% 0% 20% 0%',
    })
    if (infiniteLoading.value === true) scrollObserver.observe(loading.value)
    //第二次点击同一本书 页面标题不会变化
    document.title = '...'
    document.title = name + ' | ' + chapterTitle
  } catch {
    catalogLoadError.value = '目录或阅读配置加载失败，请检查阅读服务后重试。'
  }
}

const retryLoadReader = () => {
  if (readerRequest) void prepareReader(readerRequest)
}

onMounted(() => {
  //获取书籍数据
  const bookUrl = sessionStorage.getItem('bookUrl')
  const name = sessionStorage.getItem('bookName')
  const author = sessionStorage.getItem('bookAuthor')
  const chapterIndex = Number(sessionStorage.getItem('chapterIndex') || 0)
  const chapterPos = Number(sessionStorage.getItem('chapterPos') || 0)
  const isSeachBook = sessionStorage.getItem('isSeachBook') === 'true'
  if (isNullOrBlank(bookUrl) || isNullOrBlank(name) || author === null) {
    ElMessage.warning('书籍信息为空，即将自动返回书架页面...')
    return setTimeout(toShelf, 500)
  }
  const book: typeof store.readingBook = {
    // @ts-expect-error: bookUrl name author is NON_Blank string here
    bookUrl,
    // @ts-expect-error: bookUrl name author is NON_Blank string here
    name,
    author,
    chapterIndex,
    chapterPos,
    isSeachBook,
  }
  onResize()
  window.addEventListener('resize', onResize)
  readerRequest = { book, chapterIndex, chapterPos, name: name as string }
  void prepareReader(readerRequest)
})

onUnmounted(() => {
  window.removeEventListener('keyup', handleKeyPress)
  window.removeEventListener('keydown', ignoreKeyPress)
  window.removeEventListener('resize', onResize)
  // 兼容Safari < 14
  document.removeEventListener('visibilitychange', onVisibilityChange)
  readSettingsVisible.value = false
  popCataVisible.value = false
  scrollObserver?.disconnect()
  scrollObserver = null
})

const addToBookShelfConfirm = async () => {
  const book = store.readingBook
  // 阅读的是搜索的书籍 并未在书架
  if (book.isSeachBook === true) {
    await ElMessageBox.confirm(`是否将《${book.name}》放入书架？`, '放入书架', {
      confirmButtonText: '确认',
      cancelButtonText: '否',
      type: 'info',
      /*
        ElMessageBox.confirm默认在触发hashChange事件时自动关闭
        按下物理返回键时触发hashChange事件
        使用router.push("/")则不会触发hashChange事件
        */
      closeOnHashChange: false,
    })
      .then(() => {
        //选择是，无动作
        isSeachBook.value = false
      })
      .catch(async () => {
        //选择否，删除书籍
        await API.deleteBook(book)
      })
      .finally(() => sessionStorage.removeItem('isSeachBook'))
  }
}
onBeforeRouteLeave(async (to, from, next) => {
  console.log('onBeforeRouteLeave')
  // 弹窗时停止响应按键翻页
  window.removeEventListener('keyup', handleKeyPress)
  await addToBookShelfConfirm()
  next()
})
</script>

<style lang="scss" scoped>
:deep(.pop-setting) {
  margin-left: 68px;
  top: 0;
}

@media screen and (max-width: 500px) {
  :global(.el-popover.el-popper.pop-setting) {
    position: fixed !important;
    top: 52px !important;
    left: 12px !important;
    width: calc(100vw - 24px) !important;
    max-width: calc(100vw - 24px) !important;
    margin: 0 !important;
    transform: none !important;
  }
}

:deep(.pop-cata) {
  margin-left: 10px;
}

.chapter-wrapper {
  padding: 0 4%;

  overflow-x: hidden;

  :deep(.no-point) {
    pointer-events: none;
  }

  .tool-bar {
    position: fixed;
    top: 0;
    left: 50%;
    z-index: 100;

    .tools {
      display: flex;
      flex-direction: column;

      .tool-icon {
        font-size: 18px;
        width: 58px;
        height: 48px;
        text-align: center;
        padding-top: 12px;
        cursor: pointer;

        .iconfont {
          font-family: iconfont;
          width: 16px;
          height: 16px;
          font-size: 16px;
          margin: 0 auto 6px;
        }

        .icon-text {
          font-size: 12px;
        }
      }
    }
  }

  .read-bar {
    position: fixed;
    bottom: 0;
    right: 50%;
    z-index: 100;

    .tools {
      display: flex;
      flex-direction: column;

      .tool-icon {
        font-size: 18px;
        width: 42px;
        height: 31px;
        padding-top: 12px;
        text-align: center;
        align-items: center;
        cursor: pointer;
        margin-top: -1px;

        .iconfont {
          font-family: iconfont;
          width: 16px;
          height: 16px;
          font-size: 16px;
          margin: 0 auto 6px;
        }
      }
    }
  }

  .chapter {
    font-family: 'Microsoft YaHei', PingFangSC-Regular, HelveticaNeue-Light,
      'Helvetica Neue Light', sans-serif;
    text-align: left;
    padding: 0 65px;
    min-height: 100vh;
    width: 670px;
    margin: 0 auto;

    .content {
      font-size: 18px;
      line-height: 1.8;
      font-family: 'Microsoft YaHei', PingFangSC-Regular, HelveticaNeue-Light,
        'Helvetica Neue Light', sans-serif;

      .bottom-bar,
      .top-bar {
        height: 64px;
      }
    }
  }
}

.day {
  :deep(.popup) {
    box-shadow:
      0 2px 4px rgba(0, 0, 0, 0.12),
      0 0 6px rgba(0, 0, 0, 0.04);
  }

  :deep(.tool-icon) {
    border: 1px solid rgba(0, 0, 0, 0.1);
    margin-top: -1px;
    color: #000;

    .icon-text {
      color: rgba(0, 0, 0, 0.4);
    }
  }

  :deep(.chapter) {
    border: 1px solid #d8d8d8;
    color: #262626;
  }
}

.night {
  :deep(.popup) {
    box-shadow:
      0 2px 4px rgba(0, 0, 0, 0.48),
      0 0 6px rgba(0, 0, 0, 0.16);
  }

  :deep(.tool-icon) {
    border: 1px solid #444;
    margin-top: -1px;
    color: #666;

    .icon-text {
      color: #666;
    }
  }

  :deep(.chapter) {
    border: 1px solid #444;
    color: #666;
  }

  :deep(.popper__arrow) {
    background: #666;
  }
}

@media screen and (max-width: 776px) {
  .chapter-wrapper {
    padding: 0;

    .tool-bar {
      left: 0;
      width: 100vw;
      margin-left: 0 !important;

      .tools {
        flex-direction: row;
        justify-content: space-between;

        .tool-icon {
          border: none;
        }
      }
    }

    .read-bar {
      right: 0;
      width: 100vw;
      margin-right: 0 !important;

      .tools {
        flex-direction: row;
        justify-content: space-between;
        padding: 0 15px;

        .tool-icon {
          border: none;
          width: auto;

          .iconfont {
            display: inline-block;
          }
        }
      }
    }

    .chapter {
      width: 100vw !important;
      padding: 0 20px;
      box-sizing: border-box;
    }
  }
}

.chapter-wrapper{min-height:100vh;min-height:100dvh;position:relative;overflow-x:hidden;color:var(--app-ink)}
.chapter-wrapper .tool-icon{border-radius:6px;background:var(--app-panel-raised);color:var(--app-ink-soft);font:inherit}
.chapter-wrapper .tool-icon .el-icon{font-size:16px}
.chapter-wrapper .tool-icon:hover:not(:disabled){border-color:var(--app-accent-line);color:var(--app-accent-strong);background:var(--app-accent-soft)}
.chapter-wrapper .tool-icon:disabled{cursor:not-allowed;opacity:.48}
.chapter-wrapper .tool-bar .tools .tool-icon,.chapter-wrapper .read-bar .tools .tool-icon{min-width:44px;min-height:44px;display:flex;align-items:center;justify-content:center;gap:3px;padding:8px 6px;line-height:1.2}
.chapter-wrapper .read-bar .tools .tool-icon{min-width:78px}
.chapter-wrapper .chapter{color:var(--app-ink);font-family:'Microsoft YaHei',PingFangSC-Regular,HelveticaNeue-Light,'Helvetica Neue Light',sans-serif}
.chapter-wrapper .chapter .content{color:inherit;font-size:18px;line-height:1.8}
.chapter-error { margin: 24px 0; border: 1px solid var(--app-danger-line); border-radius: 8px; padding: 14px; color: var(--app-danger); background: var(--app-danger-surface); }
.chapter-error p { margin: 8px 0 12px; }
.chapter-error button { min-height: 36px; border: 1px solid var(--app-danger-line); border-radius: 6px; padding: 0 12px; color: var(--app-danger); background: var(--app-panel-raised); font: inherit; cursor: pointer; }
.chapter-error button:hover { background: var(--app-danger-surface); }
.day :deep(.tool-icon),.night :deep(.tool-icon){border-color:var(--app-line);color:var(--app-ink-soft)}
.day :deep(.tool-icon .icon-text),.night :deep(.tool-icon .icon-text){color:var(--app-ink-muted)}
.day :deep(.chapter),.night :deep(.chapter){border-color:var(--app-line);color:var(--app-ink)}
.day :deep(.popup),.night :deep(.popup){box-shadow:var(--app-shadow-soft)}
@media(max-width:776px){.chapter-wrapper .tool-icon{background:transparent}.chapter-wrapper .chapter{padding-bottom:70px}.chapter-wrapper .chapter .content .top-bar{height:24px}.chapter-wrapper .chapter .content .bottom-bar{height:70px}}
@media(max-width:776px){.chapter-wrapper .tool-bar{padding-top:env(safe-area-inset-top)}.chapter-wrapper .read-bar{padding-bottom:env(safe-area-inset-bottom)}.chapter-wrapper .chapter{padding-bottom:calc(70px + env(safe-area-inset-bottom))}}
</style>
