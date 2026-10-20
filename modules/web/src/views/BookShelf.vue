<template>
  <div class="shelf-app" :class="{ 'is-night': isNight }">
    <header class="app-header">
      <router-link class="brand" to="/" aria-label="阅读书架">
        <span class="brand-mark"><el-icon><Reading /></el-icon></span>
        <span class="brand-wordmark">阅读<span>LEGADO</span></span>
      </router-link>
      <nav class="main-nav" aria-label="主导航">
        <router-link class="nav-link is-active" to="/">
          <el-icon><Collection /></el-icon><span>书架</span>
        </router-link>
        <button class="nav-link" type="button" @click="openSourceEditor('bookSource')">
          <el-icon><Connection /></el-icon><span>书源</span>
        </button>
        <button class="nav-link" type="button" @click="openSourceEditor('rssSource')">
          <el-icon><DataLine /></el-icon><span>订阅源</span>
        </button>
      </nav>
      <div class="header-actions">
        <button
          class="theme-toggle"
          type="button"
          :disabled="!themeReady || isSavingTheme"
          :aria-label="isNight ? '切换到浅色主题' : '切换到深色主题'"
          @click="toggleTheme"
        >
          <el-icon><Sunny v-if="isNight" /><Moon v-else /></el-icon>
          <span>{{ isNight ? '浅色' : '深色' }}</span>
        </button>
        <button
          class="connection-button"
          :class="`connection-${connectType}`"
          type="button"
          :disabled="newConnect"
          @click="setLegadoRetmoteUrl"
        >
          <span class="connection-indicator" aria-hidden="true"></span>
          <span class="connection-copy">
            <small>阅读服务</small><strong>{{ connectStatus }}</strong>
          </span>
          <el-icon><Connection /></el-icon>
        </button>
      </div>
    </header>

    <main class="shelf-main">
      <section class="shelf-intro">
        <div class="intro-copy">
          <h1>把下一段故事，接着读下去。</h1>
          <p>书架、在线搜索与阅读进度，都跟随你的阅读服务同步。</p>
        </div>
        <button
          class="recent-card"
          type="button"
          :disabled="!readingRecent.bookUrl"
          @click="toDetail(
            readingRecent.bookUrl,
            readingRecent.name,
            readingRecent.author,
            readingRecent.chapterIndex,
            readingRecent.chapterPos,
            readingRecent.isSeachBook,
            true,
          )"
        >
          <span class="recent-icon"><el-icon><Clock /></el-icon></span>
          <span class="recent-copy">
            <small>继续阅读</small>
            <strong>{{ readingRecent.name }}</strong>
            <span>{{ readingRecent.bookUrl ? readingRecent.author : '尚无阅读记录' }}</span>
          </span>
          <el-icon class="recent-arrow"><ArrowRight /></el-icon>
        </button>
      </section>

      <section class="search-panel" aria-label="在线搜索">
        <div class="search-label">
          <span class="search-icon"><el-icon><Search /></el-icon></span>
          <div>
            <h2>发现下一本</h2>
            <p>在已连接的阅读服务中搜索书名或作者</p>
          </div>
        </div>
        <div class="search-controls">
          <el-input
            id="book-search"
            v-model="searchWord"
            class="search-input"
            placeholder="搜索书名、作者……"
            clearable
            @keyup.enter="searchBook"
          />
          <el-button class="search-submit" type="primary" :disabled="!searchWord.trim()" @click="searchBook">
            <el-icon><Search /></el-icon><span>搜索</span>
          </el-button>
        </div>
        <div class="search-note">
          <span><kbd>Enter</kbd> 开始搜索</span>
          <span v-if="isSearching">搜索结果会先显示在这里，添加后进入书架</span>
          <span v-else>在线结果可直接试读，首次打开时会同步到书架</span>
        </div>
      </section>

      <section class="bookshelf-region" ref="shelfWrapper" aria-live="polite">
        <div class="section-heading">
          <div>
            <h2>{{ isSearching ? '搜索结果' : '我的书架' }}</h2>
            <span>{{ isSearching ? '在线书源' : `${shelf.length} 本藏书` }}</span>
          </div>
          <a class="project-link" href="https://github.com/gedoor/legado_web_bookshelf" target="_blank" rel="noreferrer">
            <img :src="githubUrl" alt="" /><span>Web 项目</span><el-icon><ArrowRight /></el-icon>
          </a>
        </div>

        <div v-if="searchError" class="inline-error" role="alert">
          <el-icon><WarningFilled /></el-icon>
          <span>{{ searchError }}</span>
          <el-button text @click="searchBook">重试</el-button>
        </div>
        <div v-if="!isLoading && books.length === 0 && !searchError" class="empty-state">
          <div class="empty-mark"><el-icon><Reading /></el-icon></div>
          <h3>{{ isSearching ? '没有找到匹配书籍' : searchWord.trim() ? '书架中没有匹配藏书' : '书架还空着' }}</h3>
          <p>{{ isSearching ? '换个关键词，或检查书源是否可用。' : searchWord.trim() ? '点击搜索按钮，继续查找在线书源。' : '先搜索一本书，或者连接阅读服务加载已有藏书。' }}</p>
          <el-button v-if="!isSearching" type="primary" @click="setLegadoRetmoteUrl">连接阅读服务</el-button>
        </div>
        <book-items
          v-else
          :books="books"
          :isSearch="isSearching"
          @bookClick="handleBookClick"
        />
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import {
  ArrowRight,
  Clock,
  Collection,
  Connection,
  DataLine,
  Moon,
  Reading,
  Search,
  Sunny,
  WarningFilled,
} from '@element-plus/icons-vue'
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
.shelf-app {
  min-height: 100vh;
  min-height: 100dvh;
  color: var(--app-ink);
  background: var(--app-bg);
}

.app-header {
  min-height: 74px;
  display: flex;
  align-items: center;
  gap: 38px;
  padding: 0 clamp(20px, 4vw, 64px);
  border-bottom: 1px solid var(--app-line);
  background: var(--app-panel);
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  min-width: max-content;
  color: var(--app-ink);
  text-decoration: none;
}

.brand-mark,
.search-icon,
.recent-icon,
.empty-mark {
  display: grid;
  place-items: center;
  color: var(--app-accent-strong);
  background: var(--app-accent-soft);
}

.brand-mark {
  width: 36px;
  height: 36px;
  border-radius: 11px;
  font-size: 19px;
}

.brand-wordmark {
  display: flex;
  flex-direction: column;
  gap: 1px;
  font-size: 17px;
  font-weight: 750;
  letter-spacing: -0.02em;
}

.brand-wordmark span {
  color: var(--app-ink-muted);
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.13em;
}

.main-nav,
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.main-nav {
  flex: 1;
}

.nav-link,
.theme-toggle {
  min-height: 42px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px solid transparent;
  border-radius: 11px;
  padding: 0 13px;
  color: var(--app-ink-soft);
  background: transparent;
  font: inherit;
  font-size: 13px;
  font-weight: 650;
  text-decoration: none;
  cursor: pointer;
}

.nav-link:hover,
.theme-toggle:hover:not(:disabled) {
  color: var(--app-ink);
  background: var(--app-panel-muted);
}

.nav-link.is-active {
  color: var(--app-accent-strong);
  background: var(--app-accent-soft);
}

.connection-button {
  min-width: 190px;
  min-height: 46px;
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid var(--app-line);
  border-radius: 13px;
  padding: 5px 11px;
  color: var(--app-ink);
  background: var(--app-panel-raised);
  text-align: left;
  cursor: pointer;
}

.connection-button:hover:not(:disabled) {
  border-color: var(--app-accent-line);
}

.connection-button:disabled,
.theme-toggle:disabled {
  cursor: wait;
  opacity: 0.62;
}

.connection-indicator {
  width: 9px;
  height: 9px;
  flex: none;
  border-radius: 50%;
  background: var(--app-warning);
  box-shadow: 0 0 0 4px var(--app-warning-glow);
}

.connection-primary .connection-indicator {
  background: var(--app-success);
  box-shadow: 0 0 0 4px var(--app-success-glow);
}

.connection-danger .connection-indicator {
  background: var(--app-danger);
  box-shadow: 0 0 0 4px var(--app-danger-glow);
}

.connection-copy {
  min-width: 0;
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
}

.connection-copy small {
  color: var(--app-ink-muted);
  font-size: 10px;
}

.connection-copy strong {
  overflow: hidden;
  font-size: 11px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shelf-main {
  width: min(100% - 48px, 1320px);
  margin: 0 auto;
  padding: 42px 0 72px;
}

.shelf-intro {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(270px, 350px);
  align-items: center;
  gap: 36px;
  margin-bottom: 30px;
}

.intro-copy h1 {
  max-width: 680px;
  margin: 0;
  color: var(--app-ink);
  font-size: 32px;
  font-weight: 760;
  letter-spacing: -0.03em;
  line-height: 1.22;
}

.intro-copy p {
  max-width: 65ch;
  margin: 12px 0 0;
  color: var(--app-ink-muted);
  font-size: 14px;
  line-height: 1.7;
}

.recent-card {
  min-width: 0;
  min-height: 92px;
  display: flex;
  align-items: center;
  gap: 13px;
  border: 1px solid var(--app-line);
  border-radius: 15px;
  padding: 14px 16px;
  color: var(--app-ink);
  background: var(--app-panel);
  box-shadow: var(--app-shadow-soft);
  text-align: left;
  cursor: pointer;
}

.recent-card:hover:not(:disabled) {
  border-color: var(--app-accent-line);
  background: var(--app-panel-raised);
}

.recent-card:disabled {
  cursor: default;
}

.recent-icon {
  width: 38px;
  height: 38px;
  flex: none;
  border-radius: 12px;
  font-size: 18px;
}

.recent-copy {
  min-width: 0;
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 3px;
}

.recent-copy small {
  color: var(--app-ink-muted);
  font-size: 11px;
}

.recent-copy strong,
.recent-copy > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-copy strong {
  font-size: 14px;
}

.recent-copy > span {
  color: var(--app-ink-muted);
  font-size: 11px;
}

.recent-arrow {
  color: var(--app-ink-muted);
}

.search-panel {
  display: grid;
  grid-template-columns: minmax(210px, 0.8fr) minmax(300px, 1.3fr);
  align-items: center;
  column-gap: 28px;
  row-gap: 12px;
  margin-bottom: 34px;
  border: 1px solid var(--app-line);
  border-radius: 17px;
  padding: 20px 22px 14px;
  background: var(--app-panel);
  box-shadow: var(--app-shadow-soft);
}

.search-label {
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-icon {
  width: 42px;
  height: 42px;
  flex: none;
  border-radius: 12px;
  font-size: 19px;
}

.search-label h2,
.search-label p,
.section-heading h2,
.empty-state h3,
.empty-state p {
  margin: 0;
}

.search-label h2 {
  font-size: 15px;
  font-weight: 720;
}

.search-label p {
  margin-top: 4px;
  color: var(--app-ink-muted);
  font-size: 11px;
}

.search-controls {
  display: flex;
  gap: 8px;
}

.search-input {
  min-width: 0;
  flex: 1;
}

.search-submit {
  min-width: 106px;
  height: 42px;
}

.search-note {
  grid-column: 2;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: var(--app-ink-muted);
  font-size: 11px;
}

.search-note kbd {
  border: 1px solid var(--app-line);
  border-radius: 5px;
  padding: 1px 5px;
  color: var(--app-ink-soft);
  background: var(--app-panel-raised);
  font-family: inherit;
}

.bookshelf-region {
  min-height: 280px;
  position: relative;
  border-top: 1px solid var(--app-line);
  padding-top: 22px;
}

.section-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.section-heading > div {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.section-heading h2 {
  font-size: 20px;
  font-weight: 740;
  letter-spacing: -0.02em;
}

.section-heading > div > span {
  color: var(--app-ink-muted);
  font-size: 12px;
}

.project-link {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--app-ink-muted);
  font-size: 11px;
  text-decoration: none;
}

.project-link:hover {
  color: var(--app-accent-strong);
}

.project-link img {
  width: 17px;
  height: 17px;
  opacity: 0.72;
}

.inline-error {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid var(--app-danger-line);
  border-radius: 12px;
  padding: 11px 14px;
  color: var(--app-danger);
  background: var(--app-danger-surface);
  font-size: 13px;
}

.inline-error span {
  flex: 1;
}

.empty-state {
  min-height: 250px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 36px 18px;
  text-align: center;
}

.empty-mark {
  width: 54px;
  height: 54px;
  margin-bottom: 16px;
  border-radius: 16px;
  font-size: 26px;
}

.empty-state h3 {
  color: var(--app-ink);
  font-size: 17px;
  font-weight: 700;
}

.empty-state p {
  max-width: 48ch;
  margin-top: 8px;
  color: var(--app-ink-muted);
  font-size: 13px;
  line-height: 1.6;
}

.empty-state :deep(.el-button) {
  margin-top: 18px;
}

.shelf-app :deep(.el-loading-mask) {
  background: var(--app-loading-backdrop);
  backdrop-filter: blur(2px);
}

@media (max-width: 900px) {
  .app-header {
    gap: 20px;
  }

  .main-nav {
    gap: 2px;
  }

  .nav-link {
    padding: 0 9px;
  }

  .connection-button {
    min-width: 48px;
    justify-content: center;
  }

  .connection-copy {
    display: none;
  }

  .shelf-intro {
    grid-template-columns: minmax(0, 1fr) minmax(240px, 300px);
    gap: 22px;
  }
}

@media (max-width: 680px) {
  .app-header {
    min-height: 64px;
    flex-wrap: wrap;
    gap: 8px 12px;
    padding: 10px 16px 0;
  }

  .brand {
    margin-right: auto;
  }

  .brand-mark {
    width: 32px;
    height: 32px;
  }

  .brand-wordmark {
    font-size: 15px;
  }

  .header-actions {
    gap: 3px;
  }

  .theme-toggle {
    min-height: 40px;
    padding: 0 8px;
  }

  .connection-button {
    min-height: 40px;
    padding: 0 10px;
  }

  .main-nav {
    order: 3;
    width: 100%;
    justify-content: space-around;
    border-top: 1px solid var(--app-line);
    padding: 5px 0 7px;
  }

  .nav-link {
    min-height: 38px;
    flex: 1;
    font-size: 12px;
  }

  .shelf-main {
    width: calc(100% - 32px);
    padding: 28px 0 48px;
  }

  .shelf-intro {
    grid-template-columns: 1fr;
    gap: 20px;
    margin-bottom: 22px;
  }

  .intro-copy h1 {
    font-size: 26px;
  }

  .intro-copy p {
    margin-top: 8px;
    font-size: 13px;
  }

  .recent-card {
    min-height: 78px;
  }

  .search-panel {
    grid-template-columns: 1fr;
    gap: 12px;
    margin-bottom: 26px;
    padding: 16px;
  }

  .search-label p {
    font-size: 11px;
  }

  .search-note {
    grid-column: 1;
    flex-wrap: wrap;
  }

  .section-heading h2 {
    font-size: 18px;
  }
}

@media (max-width: 420px) {
  .shelf-main {
    width: calc(100% - 24px);
  }

  .intro-copy h1 {
    font-size: 23px;
  }

  .search-controls {
    gap: 6px;
  }

  .search-submit {
    min-width: 82px;
    padding: 0 10px;
  }

  .section-heading > div {
    align-items: flex-start;
    flex-direction: column;
    gap: 3px;
  }

  .project-link span {
    display: none;
  }
}
</style>
