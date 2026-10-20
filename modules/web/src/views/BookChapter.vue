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
            <button class="tool-icon" type="button" aria-label="打开章节目录">
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
            <button class="tool-icon" type="button" :disabled="noPoint" aria-label="阅读设置">
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
    <main class="chapter" ref="content" :style="chapterTheme">
      <div class="content">
        <header class="reader-context">
          <router-link class="reader-shelf-link" to="/" @click.stop>
            <el-icon><Collection /></el-icon><span>书架</span>
          </router-link>
          <div class="reader-book-meta">
            <strong>{{ store.readingBook.name }}</strong>
            <span>{{ chapterIndex + 1 }} / {{ catalog.length || 1 }} 章</span>
          </div>
          <span class="reader-progress-value">{{ chapterProgress }}%</span>
        </header>
        <div
          class="reading-progress"
          role="progressbar"
          aria-label="阅读进度"
          :aria-valuenow="chapterProgress"
          aria-valuemin="0"
          aria-valuemax="100"
        ><span :style="{ transform: `scaleX(${chapterProgress / 100})` }"></span></div>
        <div class="top-bar" ref="top"></div>
        <div
          v-for="data in chapterData"
          :key="data.index"
          :chapterIndex="data.index"
          ref="chapter"
        >
          <chapter-content
            ref="chapterRef"
            :chapterIndex="data.index"
            :contents="data.content"
            :title="data.title"
            :spacing="store.config.spacing"
            :fontSize="fontSize"
            :fontFamily="fontFamily"
            @readedLengthChange="onReadedLengthChange"
            v-if="showContent"
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
const chapterProgress = computed(() => {
  const total = catalog.value.length
  return total > 0
    ? Math.round((Math.min(chapterIndex.value + 1, total) / total) * 100)
    : 0
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
const chapterData = ref<{ index: number; content: string[]; title: string }[]>(
  [],
)
const noPoint = ref(true)
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

  loadingWrapper(
    API.getBookContent(bookUrl, chapterIndex).then(
      res => {
        if (res.data.isSuccess) {
          const data = res.data.data
          const content = data.split(/\n+/)
          chapterData.value.push({ index, content, title })
          if (reloadChapter) toChapterPos(chapterPos)
        } else {
          ElMessage({ message: res.data.errorMsg, type: 'error' })
          const content = [res.data.errorMsg]
          chapterData.value.push({ index, content, title })
        }
        store.setContentLoading(true)
        noPoint.value = false
        store.setShowContent(true)
        if (!res.data.isSuccess) {
          throw res.data
        }
      },
      err => {
        const content = ['获取章节内容失败！']
        chapterData.value.push({ index, content, title })
        store.setShowContent(true)
        throw err
      },
    ),
  )
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
// 监听方向键
const handleKeyPress = (event: KeyboardEvent) => {
  if (!canJump) return
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
  if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
    event.preventDefault()
    event.stopPropagation()
  }
}

onMounted(async () => {
  await store.loadWebConfig()
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
  loadingWrapper(
    store.loadWebCatalog(book).then(chapters => {
      store.setReadingBook(book)
      getContent(chapterIndex, true, chapterPos)
      window.addEventListener('keyup', handleKeyPress)
      window.addEventListener('keydown', ignoreKeyPress)
      // 兼容Safari < 14
      document.addEventListener('visibilitychange', onVisibilityChange)
      //监听底部加载
      scrollObserver = new IntersectionObserver(onReachBottom, {
        rootMargin: '-100% 0% 20% 0%',
      })
      if (infiniteLoading.value === true) scrollObserver.observe(loading.value)
      //第二次点击同一本书 页面标题不会变化
      document.title = '...'
      document.title = (name as string) + ' | ' + chapters[chapterIndex].title
    }),
  )
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

:deep(.pop-cata) {
  margin-left: 10px;
}

.chapter-wrapper {
  min-height: 100vh;
  min-height: 100dvh;
  position: relative;
  overflow-x: hidden;
  color: var(--app-ink);

  .tool-bar,
  .read-bar {
    position: fixed;
    left: 50%;
    z-index: 100;
    width: 54px;
    border: 1px solid var(--app-line);
    border-radius: 14px;
    padding: 7px;
    background: var(--app-panel-raised);
    box-shadow: var(--app-shadow-soft);

    .tools {
      display: flex;
      flex-direction: column;
      gap: 4px;

      .tool-icon {
        width: 40px;
        min-height: 44px;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 4px;
        border: 1px solid transparent;
        border-radius: 9px;
        padding: 4px 2px;
        color: var(--app-ink-soft);
        background: transparent;
        font: inherit;
        font-size: 10px;
        cursor: pointer;
        transition:
          color var(--app-duration-state) ease-out,
          background-color var(--app-duration-state) ease-out,
          border-color var(--app-duration-state) ease-out;

        .el-icon {
          font-size: 17px;
        }

        .icon-text {
          font-size: 10px;
        }
      }

      .tool-icon:hover:not(:disabled) {
        border-color: var(--app-line);
        color: var(--app-accent-strong);
        background: var(--app-accent-soft);
      }

      .tool-icon:disabled {
        cursor: not-allowed;
        opacity: 0.48;
      }
    }
  }

  .tool-bar {
    top: 50%;
    transform: translateY(-50%);
  }

  .read-bar {
    top: 50%;
    right: 50%;
    left: auto;
    width: 54px;
    transform: translateY(-50%);

    .tool-icon {
      min-height: 50px;
    }
  }

  .chapter {
    width: 670px;
    min-height: calc(100vh - 48px);
    min-height: calc(100dvh - 48px);
    box-sizing: content-box;
    margin: 24px auto;
    border: 1px solid var(--app-line);
    border-radius: 17px;
    padding: 0 65px;
    color: var(--app-ink);
    box-shadow: var(--app-shadow);
    text-align: left;

    .content {
      font-size: 18px;
      line-height: 1.8;
      font-family: 'Microsoft YaHei', PingFangSC-Regular, HelveticaNeue-Light,
        'Helvetica Neue Light', sans-serif;

      .reader-context {
        min-height: 66px;
        display: grid;
        grid-template-columns: auto minmax(0, 1fr) auto;
        align-items: center;
        gap: 14px;
        border-bottom: 1px solid var(--app-line);
        color: var(--app-ink-muted);
        font-size: 11px;
      }

      .reader-shelf-link {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        color: var(--app-ink-muted);
        font-size: 11px;
        text-decoration: none;
      }

      .reader-shelf-link:hover {
        color: var(--app-accent-strong);
      }

      .reader-book-meta {
        min-width: 0;
        display: flex;
        align-items: center;
        gap: 10px;
      }

      .reader-book-meta strong {
        overflow: hidden;
        color: var(--app-ink-soft);
        font-size: 12px;
        font-weight: 680;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .reader-book-meta span,
      .reader-progress-value {
        flex: none;
        color: var(--app-ink-muted);
        font-size: 10px;
      }

      .reading-progress {
        height: 2px;
        overflow: hidden;
        background: var(--app-progress-track);
      }

      .reading-progress span {
        height: 100%;
        display: block;
        background: var(--app-accent);
        transform-origin: left center;
        transition: transform var(--app-duration-state) ease-out;
      }

      .bottom-bar,
      .top-bar {
        height: 48px;
      }
    }
  }
}

.day {
  :deep(.popup) {
    box-shadow: var(--app-shadow);
  }
}

.night {
  :deep(.popup) {
    box-shadow: var(--app-shadow);
  }
}

@media (max-width: 776px) {
  :global(.pop-setting) {
    position: fixed !important;
    top: 64px !important;
    right: auto !important;
    bottom: auto !important;
    left: 50% !important;
    width: calc(100vw - 24px) !important;
    max-width: 480px;
    box-sizing: border-box;
    margin: 0 !important;
    transform: translateX(-50%) !important;
  }

  .chapter-wrapper {
    padding: 0;

    .tool-bar {
      top: 8px;
      left: 50%;
      width: calc(100vw - 16px);
      transform: translateX(-50%);
      margin-left: 0 !important;

      .tools {
        flex-direction: row;
        justify-content: space-around;

        .tool-icon {
          width: min(18vw, 54px);
          min-height: 40px;
          border: 0;
        }
      }
    }

    .read-bar {
      top: auto;
      right: 50%;
      bottom: 8px;
      left: auto;
      width: calc(100vw - 16px);
      transform: translateX(50%);
      margin-right: 0 !important;

      .tools {
        flex-direction: row;
        justify-content: space-around;

        .tool-icon {
          width: 48%;
          min-height: 42px;
          flex-direction: row;
          gap: 8px;
          border: 0;
          font-size: 12px;
        }
      }
    }

    .chapter {
      width: 100vw !important;
      min-height: 100dvh;
      box-sizing: border-box;
      margin: 0 auto;
      border: 0;
      border-radius: 0;
      padding: 0 20px 70px;

      .content {
        .reader-context {
          min-height: 58px;
          gap: 8px;
        }

        .reader-shelf-link span {
          display: none;
        }

        .reader-book-meta {
          gap: 6px;
        }

        .reader-book-meta strong {
          font-size: 11px;
        }

        .reader-book-meta span,
        .reader-progress-value {
          font-size: 9px;
        }

        .top-bar {
          height: 26px;
        }
      }
    }
  }
}
</style>
