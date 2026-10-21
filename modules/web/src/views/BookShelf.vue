<template>
  <div :class="{ 'index-wrapper': true, night: isNight, day: !isNight }">
    <aside class="navigation-wrapper" aria-label="阅读导航">
      <div class="navigation-title-wrapper">
        <div class="navigation-title">阅读</div>
        <div class="navigation-sub-title">清风不识字，何故乱翻书</div>
      </div>
      <nav class="source-navigation" aria-label="源管理">
        <button type="button" @click="openSourceEditor('bookSource')">书源管理</button>
        <button type="button" @click="openSourceEditor('rssSource')">订阅源管理</button>
      </nav>
      <div class="search-wrapper">
        <el-input
          v-model="searchWord"
          aria-label="搜索书籍"
          placeholder="搜索书籍，在线书籍自动加入书架"
          class="search-input"
          :prefix-icon="Search"
          @keyup.enter="searchBook"
        />
        <div class="search-actions">
          <el-button v-if="isSearching && isLoading" text @click="cancelSearch(true)">
            取消搜索
          </el-button>
          <el-button v-else-if="isSearching" text @click="returnToShelf">
            返回书架
          </el-button>
          <el-button v-else type="primary" :disabled="!searchWord.trim()" @click="searchBook">在线搜索</el-button>
        </div>
      </div>
      <div class="bottom-wrapper">
        <div class="recent-wrapper">
          <div class="recent-title">最近阅读</div>
          <div class="reading-recent">
            <el-tag
              :type="readingRecent.name === '尚无阅读记录' ? 'warning' : 'primary'"
              class="recent-book"
              size="large"
              :class="{ 'no-point': !readingRecent.bookUrl }"
              @click="toDetail(readingRecent.bookUrl, readingRecent.name, readingRecent.author, readingRecent.chapterIndex, readingRecent.chapterPos, readingRecent.isSeachBook, true)"
            >{{ readingRecent.name }}</el-tag>
          </div>
        </div>
        <div class="setting-wrapper">
          <div class="setting-title">基本设定</div>
          <div class="setting-item">
            <el-tag
              :type="connectType"
              size="large"
              class="setting-connect"
              :class="{ 'no-point': newConnect }"
              @click="setLegadoRetmoteUrl"
            >{{ connectStatus }}</el-tag>
          </div>
          <button class="setting-theme" type="button" :disabled="!themeReady || isSavingTheme" @click="toggleTheme">
            {{ isNight ? '浅色主题' : '深色主题' }}
          </button>
        </div>
      </div>
      <div class="bottom-icons">
        <a href="https://github.com/gedoor/legado_web_bookshelf" target="_blank" rel="noreferrer">
          <div class="bottom-icon"><img :src="githubUrl" alt="" /></div>
        </a>
      </div>
    </aside>
    <main class="shelf-wrapper" ref="shelfWrapper" aria-live="polite">
      <header class="shelf-heading">
        <h1>{{ isSearching ? '搜索结果' : '我的书架' }}</h1>
        <span>{{ isSearching ? '在线书源' : books.length + ' 本藏书' }}</span>
      </header>
      <div v-if="searchError" class="shelf-error" role="alert">
        <span>{{ searchError }}</span><el-button text @click="searchBook">重试</el-button>
      </div>
      <div v-if="!isLoading && books.length === 0 && !searchError" class="shelf-empty">
        {{ isSearching ? '没有可添加的新书。书架已有的匹配书不会重复显示，可返回书架查看。' : searchWord ? '书架中没有匹配藏书。' : '书架还空着，请搜索一本书开始阅读。' }}
      </div>
      <book-items v-else :books="books" @bookClick="handleBookClick" :isSearch="isSearching" />
    </main>
  </div>
</template>

<script setup lang="ts">
import { Moon, Search, Sunny } from '@element-plus/icons-vue'
import { useBookStore } from '@/store'
import githubUrl from '@/assets/imgs/github.png'
import { useLoading } from '@/hooks/loading'
import { baseURL_localStorage_key } from '@/api/axios'
import { useAppTheme } from '@/hooks/useAppTheme'
import type { SocketTask } from '@/api/socketTask'
import API, {
  legado_http_entry_point,
  parseLeagdoHttpUrlWithDefault,
  setApiEntryPoint,
} from '@api'
import { validatorHttpUrl } from '@/utils/utils'
import type { Book, SeachBook } from '@/book'
import type { webReadConfig } from '@/web'

const store = useBookStore()
const isNight = computed(() => store.isNight)
const {
  ready: themeReady,
  isSaving: isSavingTheme,
  markReady: markThemeReady,
  toggleTheme,
} = useAppTheme()

/** shortcuts of `store.setConfig` */
const applyReadConfig = (config?: webReadConfig) => {
  try {
    if (config !== undefined) store.setConfig(config)
  } catch {
    ElMessage.info('阅读界面配置解析错误')
  }
}

const readingRecent = ref<typeof store.readingBook>({
  name: '尚无阅读记录',
  author: '',
  bookUrl: '',
  chapterIndex: 0,
  chapterPos: 0,
  isSeachBook: false,
})

const shelfWrapper = ref<HTMLElement>()
//const shelfWrapper = useTemplateRef<HTMLElement>("shelfWrapper")
const { showLoading, closeLoading, loadingWrapper, isLoading } = useLoading(
  shelfWrapper,
  '正在获取书籍信息',
)

// 书架书籍和在线书籍搜索
const books = shallowRef<Book[] | SeachBook[]>([])
const shelf = computed(() => store.shelf)
const searchWord = ref('')
const isSearching = ref(false)
const searchError = ref('')
let activeSearch: SocketTask | undefined
let searchGeneration = 0

const cancelSearch = (restoreShelf = false) => {
  searchGeneration += 1
  const task = activeSearch
  activeSearch = undefined
  task?.cancel()
  closeLoading()
  if (restoreShelf) {
    isSearching.value = false
    searchError.value = ''
    store.clearSearchBooks()
  }
}

const returnToShelf = () => {
  cancelSearch(true)
  searchWord.value = ''
}

watch(searchWord, () => {
  if (isSearching.value) cancelSearch(true)
})

watchEffect(() => {
  if (isSearching.value) return
  const query = searchWord.value.trim().toLocaleLowerCase()
  books.value = query
    ? shelf.value.filter(book =>
        `${book.name} ${book.author}`.toLocaleLowerCase().includes(query),
      )
    : shelf.value
})
//搜索在线书籍
const searchBook = () => {
  const query = searchWord.value.trim()
  if (!query) return
  cancelSearch()
  books.value = []
  store.clearSearchBooks()
  searchError.value = ''
  showLoading()
  isSearching.value = true
  const currentGeneration = ++searchGeneration
  let finished = false
  const task = API.search(
    query,
    searchBooks => {
      if (currentGeneration !== searchGeneration) return
      try {
        store.setSearchBooks(searchBooks)
        books.value = store.searchBooks
      } catch (error) {
        searchError.value = '搜索结果无法读取，请检查阅读服务返回的数据。'
        throw error
      }
    },
    () => {
      finished = true
      if (currentGeneration !== searchGeneration) return
      activeSearch = undefined
      closeLoading()
    },
    () => {
      if (currentGeneration !== searchGeneration) return
      searchError.value = '在线搜索连接中断，请检查阅读服务后重试。'
    },
  )
  if (!finished) activeSearch = task
}

//连接状态
const connectionStore = useConnectionStore()
const { connectStatus, connectType, newConnect } = storeToRefs(connectionStore)

const setLegadoRetmoteUrl = () => {
  ElMessageBox.prompt(
    '请输入 后端地址 ( 如：http://127.0.0.1:9527 或者通过内网穿透的地址)',
    '提示',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: legado_http_entry_point,
      inputValidator: value => validatorHttpUrl(value),
      inputErrorMessage: '输入的格式不对',
      beforeClose: (action, instance, done) => {
        if (action === 'confirm') {
          connectionStore.setNewConnect(true)
          instance.confirmButtonLoading = true
          instance.confirmButtonText = '校验中……'
          // instance.inputValue
          const url = new URL(instance.inputValue).toString()
          API.getReadConfig(url)
            .then(function (config) {
              connectionStore.setNewConnect(false)
              applyReadConfig(config)
              instance.confirmButtonLoading = false
              store.clearSearchBooks()
              setApiEntryPoint(...parseLeagdoHttpUrlWithDefault(url))
              if (url === location.origin) {
                localStorage.removeItem(baseURL_localStorage_key)
              } else {
                localStorage.setItem(baseURL_localStorage_key, url)
              }
              store.loadBookShelf()
              done()
            })
            .catch(function (error) {
              connectionStore.setNewConnect(false)
              instance.confirmButtonLoading = false
              instance.confirmButtonText = '确定'
              throw error
            })
        } else {
          done()
        }
      },
    },
  )
}

const router = useRouter()
const openSourceEditor = (route: 'bookSource' | 'rssSource') => {
  const url = new URL(location.href)
  url.searchParams.set('editorRoute', route)
  url.hash = `/${route}`
  location.assign(url.toString())
}

const handleBookClick = async (book: SeachBook | Book) => {
  // 判断是否为 searchBook
  const isSeachBook = 'respondTime' in book
  if (isSeachBook) {
    await API.saveBook(book)
  }
  const {
    bookUrl,
    name,
    author,
    // @ts-expect-error: descruct with default value
    durChapterIndex = 0,
    // @ts-expect-error: descruct with default value
    durChapterPos = 0,
  } = book

  toDetail(bookUrl, name, author, durChapterIndex, durChapterPos, isSeachBook)
}
const toDetail = (
  bookUrl: string,
  bookName: string,
  bookAuthor: string,
  chapterIndex: number,
  chapterPos: number,
  isSeachBook: boolean | undefined = false,
  fromReadRecentClick = false,
) => {
  if (bookName === '尚无阅读记录') return
  // 最近书籍不再书架上 自动搜索
  if (
    fromReadRecentClick &&
    shelf.value.every(book => book.bookUrl !== bookUrl)
  ) {
    searchWord.value = bookName
    searchBook()
    return
  }
  sessionStorage.setItem('bookUrl', bookUrl)
  sessionStorage.setItem('bookName', bookName)
  sessionStorage.setItem('bookAuthor', bookAuthor)
  sessionStorage.setItem('chapterIndex', String(chapterIndex))
  sessionStorage.setItem('chapterPos', String(chapterPos))
  sessionStorage.setItem('isSeachBook', String(isSeachBook))
  readingRecent.value = {
    name: bookName,
    author: bookAuthor,
    bookUrl,
    chapterIndex,
    chapterPos,
    isSeachBook,
  }
  localStorage.setItem('readingRecent', JSON.stringify(readingRecent.value))
  router.push({
    path: '/chapter',
  })
}

const loadShelf = async () => {
  try {
    await store.loadWebConfig()
  } finally {
    markThemeReady()
  }
  await store.saveBookProgress()
  //确保各种网络情况下同步请求先完成
  await store.loadBookShelf()
}

onMounted(() => {
  //获取最近阅读书籍
  const readingRecentStr = localStorage.getItem('readingRecent')
  if (readingRecentStr != null) {
    readingRecent.value = JSON.parse(readingRecentStr)
    if (typeof readingRecent.value.chapterIndex == 'undefined') {
      readingRecent.value.chapterIndex = 0
    }
  }
  console.log('bookshelf mounted')
  loadingWrapper(loadShelf())
})

onBeforeUnmount(() => cancelSearch())
</script>

<style lang="scss" scoped>
.index-wrapper {
  height: 100%;
  width: 100%;
  display: flex;
  flex-direction: row;

  .navigation-wrapper {
    width: 260px;
    min-width: 260px;
    padding: 48px 36px;
    background-color: #f7f7f7;

    .navigation-title {
      font-size: 24px;
      font-weight: 500;
      font-family: FZZCYSK;
    }

    .navigation-sub-title {
      font-size: 16px;
      font-weight: 300;
      font-family: FZZCYSK;
      margin-top: 16px;
      color: var(--app-ink-muted);
    }

    .search-wrapper {
      .search-input {
        border-radius: 50%;
        margin-top: 24px;

        :deep(.el-input__wrapper) {
          border-radius: 50px;
          border-color: #e3e3e3;
        }
      }
    }

    .bottom-wrapper {
      display: flex;
      flex-direction: column;
    }

    .recent-wrapper {
      margin-top: 36px;

      .recent-title {
        font-size: 14px;
        color: var(--app-ink-muted);
        font-family: FZZCYSK;
      }

      .reading-recent {
        margin: 18px 0;

        .recent-book {
          font-size: 10px;
          /*           // font-weight: 400;
          // margin: 12px 0;
          // font-weight: 500;
          // color: #6B7C87; */
          cursor: pointer;
          /*           // padding: 6px 18px; */
        }
      }
    }

    .setting-wrapper {
      margin-top: 36px;

      .setting-title {
        font-size: 14px;
        color: var(--app-ink-muted);
        font-family: FZZCYSK;
      }

      .no-point {
        pointer-events: none;
      }

      .setting-connect {
        font-size: 8px;
        margin-top: 16px;
        /*         // color: #6B7C87; */
        cursor: pointer;
      }
    }

    .bottom-icons {
      position: static;
      flex: none;
      height: auto;
      width: auto;
      min-height: 52px;
      margin-top: 8px;
      border-top: 1px solid var(--app-line);
      padding-top: 4px;
      align-items: center;
      display: flex;
      flex-direction: row;

      a {
        min-width: 44px;
        min-height: 44px;
        display: inline-flex;
        align-items: center;
        justify-content: center;
      }

      .bottom-icon {
        width: 44px;
        height: 44px;
        display: grid;
        place-items: center;
        border-radius: 50%;
      }

      .bottom-icon img {
        display: block;
        width: 24px;
        height: 24px;
      }
    }
  }

  .shelf-wrapper {
    padding: 48px 48px;
    width: 100%;
    display: flex;
    flex-direction: column;
    box-sizing: border-box;
    overflow: hidden;
  }
}

@media screen and (max-width: 750px) {
  .index-wrapper {
    overflow-x: hidden;
    flex-direction: column;

    .navigation-wrapper {
      padding: 20px 24px;
      box-sizing: border-box;
      width: 100%;

      .navigation-title-wrapper {
        white-space: nowrap;
        display: flex;
        justify-content: space-between;
        align-items: flex-end;
      }

      .bottom-wrapper {
        flex-direction: row;

        > * {
          flex-grow: 1;
          margin-top: 18px;

          .reading-recent,
          .setting-item {
            margin-bottom: 0px;
          }
        }
      }

      .bottom-icons {
        display: none;
      }
    }

    .shelf-wrapper {
      padding: 0;
      flex-grow: 1;

      :deep(.el-loading-spinner) {
        display: none;
      }
    }
  }
}

.night {
  .navigation-wrapper {
    background-color: #454545;

    .navigation-title {
      color: var(--app-ink);
    }

    .search-wrapper {
      .search-input {
        .el-input__wrapper {
          background-color: #454545;
        }

        .el-input__inner {
          color: #b1b1b1;
        }
      }
    }
  }

  :deep(.shelf-wrapper) {
    background-color: #161819;
  }
}
.index-wrapper {
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--app-bg);
  color: var(--app-ink);
}

.index-wrapper .navigation-wrapper {
  position: sticky;
  top: 0;
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  overflow-y: auto;
  gap: 18px;
  border-right: 1px solid var(--app-line);
  padding: 28px 22px 16px;
  background: var(--app-panel);
}

.index-wrapper .navigation-title {
  color: var(--app-ink);
  font-family: FZZCYSK, Noto Serif SC, Microsoft YaHei, serif;
  font-size: 23px;
  font-weight: 650;
}

.index-wrapper .navigation-sub-title { color: var(--app-ink-muted); font-size: 12px; }
.index-wrapper .source-navigation { display: grid; gap: 4px; margin-top: 0; }

.source-navigation button {
  min-height: 38px;
  border: 1px solid transparent;
  border-radius: 6px;
  padding: 0 10px;
  color: var(--app-ink-soft);
  background: transparent;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.source-navigation button:hover { color: var(--app-accent-strong); background: var(--app-accent-soft); }
.index-wrapper .search-wrapper { display: grid; gap: 8px; margin-top: 0; }
.index-wrapper .search-input { margin-top: 0; }
.search-actions { display: flex; justify-content: flex-end; gap: 6px; }

.setting-theme {
  min-height: 36px;
  margin-top: 8px;
  border: 1px solid var(--app-line);
  border-radius: 6px;
  padding: 0 10px;
  color: var(--app-ink-soft);
  background: var(--app-panel-raised);
  cursor: pointer;
}

.index-wrapper .bottom-wrapper { margin-top: auto; }
.bottom-link { min-height: 32px; display: flex; align-items: center; gap: 8px; margin-top: 12px; color: var(--app-ink-muted); font-size: 11px; text-decoration: none; }
.bottom-link img { width: 16px; height: 16px; }

.index-wrapper .shelf-wrapper {
  min-width: 0;
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  overflow: auto;
  padding: 30px clamp(22px, 4vw, 52px);
}

.shelf-heading { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; margin-bottom: 14px; border-bottom: 1px solid var(--app-line); padding-bottom: 12px; }
.shelf-heading h1 { margin: 0; color: var(--app-ink); font-size: 20px; font-weight: 680; }
.shelf-heading span { color: var(--app-ink-muted); font-size: 12px; }

.shelf-error { display: flex; justify-content: space-between; align-items: center; gap: 8px; margin-bottom: 10px; border: 1px solid var(--app-danger-line); border-radius: 6px; padding: 8px 10px; color: var(--app-danger); background: var(--app-danger-surface); font-size: 12px; }
.shelf-empty { min-height: 240px; display: grid; place-items: center; padding: 20px; color: var(--app-ink-muted); text-align: center; }
.shelf-wrapper :deep(.el-loading-mask) { background: var(--app-loading-backdrop); }

.index-wrapper.night .navigation-wrapper { background: var(--app-panel); }
.index-wrapper.night .navigation-title { color: var(--app-ink); }
.index-wrapper.night .navigation-wrapper .search-input :deep(.el-input__wrapper) { background: var(--app-panel-raised); box-shadow: 0 0 0 1px var(--app-line) inset; }
.index-wrapper.night .navigation-wrapper .search-input :deep(.el-input__inner) { color: var(--app-ink); }
.index-wrapper.night :deep(.shelf-wrapper) { background: var(--app-bg); }

@media (max-width: 750px) {
  .index-wrapper { height: auto; grid-template-columns: minmax(0, 1fr); }
  .index-wrapper .navigation-wrapper { min-height: 0; position: relative; gap: 0; overflow: visible; border-right: 0; border-bottom: 1px solid var(--app-line); padding: 16px 18px 12px; }
  .index-wrapper .navigation-title-wrapper { display: flex; align-items: baseline; justify-content: space-between; }
  .index-wrapper .navigation-title { font-size: 21px; }
  .index-wrapper .navigation-sub-title { margin-top: 4px; font-size: 11px; }
  .index-wrapper .source-navigation { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 5px; margin-top: 12px; }
  .source-navigation button { min-height: 40px; text-align: center; }
  .index-wrapper .search-wrapper { gap: 6px; margin-top: 12px; }
  .index-wrapper .bottom-wrapper { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-top: 14px; }
  .index-wrapper .recent-wrapper, .index-wrapper .setting-wrapper { margin-top: 0; }
  .index-wrapper .bottom-link { display: none; }
  .index-wrapper .shelf-wrapper { min-height: 55vh; padding: 18px; }
}

@media (max-width: 390px) {
  .index-wrapper .navigation-wrapper { padding: 14px; }
  .index-wrapper .navigation-title { font-size: 21px; }
  .index-wrapper .navigation-sub-title { font-size: 10px; }
  .index-wrapper .bottom-wrapper { gap: 6px; }
  .index-wrapper .setting-connect, .index-wrapper .recent-book { max-width: 100%; overflow: hidden; }
  .index-wrapper .shelf-wrapper { padding: 16px 14px; }
}

@media (prefers-reduced-motion: reduce) { .source-navigation button, .setting-theme { transition: none; } }
</style>
