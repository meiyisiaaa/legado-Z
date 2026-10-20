<template>
  <div class="books-wrapper">
    <div class="book-grid">
      <button
        class="book-card"
        v-for="book in books"
        :key="book.bookUrl"
        type="button"
        :aria-label="`打开《${book.name}》${book.author ? `，作者${book.author}` : ''}`"
        @click="handleClick(book)"
      >
        <span class="cover-frame">
          <img
            class="cover"
            :src="getCover(book)"
            :key="book.coverUrl"
            @error="handleCoverError"
            :alt="`${book.name} 封面`"
            loading="lazy"
          />
          <span class="cover-placeholder">封面暂不可用</span>
        </span>
        <span class="book-info">
          <span class="book-name">{{ book.name }}</span>
          <span class="book-byline">
            <span class="author">{{ book.author || '作者未知' }}</span>
            <span class="tags" v-if="isSearch && book.kind">
              <el-tag
                v-for="tag in book.kind?.split(',').slice(0, 2)"
                :key="tag"
                effect="plain"
                size="small"
              >
                {{ tag }}
              </el-tag>
            </span>
            <span class="update-info" v-if="!isSearch">
              <span>共{{ (book as Book).totalChapterNum || 0 }}章</span>
              <span class="metadata-separator" aria-hidden="true"></span>
              <span>{{ dateFormat((book as Book).lastCheckTime) }}</span>
            </span>
          </span>
          <span class="intro" v-if="isSearch">{{ book.intro || '暂无简介' }}</span>
          <span class="dur-chapter" v-if="!isSearch">
            <span class="meta-label">读到</span>{{ (book as Book).durChapterTitle || '尚未开始' }}
          </span>
          <span class="last-chapter">
            <span class="meta-label">最新</span>{{ book.latestChapterTitle || '暂无最新章节' }}
          </span>
        </span>
        <span class="card-arrow" aria-hidden="true"><el-icon><ArrowRight /></el-icon></span>
      </button>
    </div>
  </div>
</template>
<script setup lang="ts">
import { ArrowRight } from '@element-plus/icons-vue'
import type { Book, SeachBook } from '@/book'
import { dateFormat, isLegadoUrl } from '../utils/utils'
import API from '@api'
const props = defineProps<{
  books: Array<Book | SeachBook>
  isSearch: boolean
}>()

const emit = defineEmits(['bookClick'])
const handleClick = (book: Book | SeachBook) => emit('bookClick', book)
const getCover = ({ bookUrl, coverUrl }: Book | SeachBook) => {
  if (coverUrl === undefined) return API.getProxyCoverUrl(bookUrl)
  return isLegadoUrl(coverUrl) ? API.getProxyCoverUrl(coverUrl) : coverUrl
}
const handleCoverError = (evt: Event) => {
  const target = evt.target as HTMLImageElement
  if (target.dataset.proxyAttempted === 'true') {
    target.closest('.cover-frame')?.classList.add('is-missing')
    return
  }
  target.dataset.proxyAttempted = 'true'
  target.src = API.getProxyCoverUrl(target.getAttribute('src') || target.src)
}
</script>

<style lang="scss" scoped>
.books-wrapper {
  width: 100%;
}

.book-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 340px), 1fr));
  gap: 12px;
}

.book-card {
  min-width: 0;
  min-height: 158px;
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 15px;
  border: 1px solid var(--app-line);
  border-radius: 15px;
  padding: 15px 34px 15px 15px;
  color: var(--app-ink);
  background: var(--app-panel);
  box-shadow: none;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.book-card:hover {
  z-index: 1;
  border-color: var(--app-accent-line);
  background: var(--app-panel-raised);
  box-shadow: var(--app-shadow-soft);
}

.book-card:active {
  background: var(--app-panel-muted);
}

.cover-frame {
  width: 82px;
  height: 118px;
  flex: none;
  position: relative;
  display: grid;
  place-items: center;
  overflow: hidden;
  border: 1px solid var(--app-line);
  border-radius: 9px;
  background: var(--app-panel-muted);
}

.cover {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.cover-placeholder {
  display: none;
  padding: 8px;
  color: var(--app-ink-muted);
  font-size: 10px;
  line-height: 1.5;
  text-align: center;
}

.cover-frame.is-missing .cover {
  display: none;
}

.cover-frame.is-missing .cover-placeholder {
  display: block;
}

.book-info {
  min-width: 0;
  min-height: 118px;
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: space-between;
  gap: 7px;
}

.book-name,
.author,
.intro,
.dur-chapter,
.last-chapter {
  overflow: hidden;
  text-overflow: ellipsis;
}

.book-name {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  color: var(--app-ink);
  font-size: 15px;
  font-weight: 730;
  line-height: 1.4;
}

.book-byline {
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--app-ink-muted);
  font-size: 11px;
}

.author {
  min-width: 0;
  white-space: nowrap;
}

.update-info {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

.metadata-separator {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--app-ink-muted);
}

.tags {
  display: inline-flex;
  flex: none;
  gap: 4px;
}

.tags :deep(.el-tag) {
  border-radius: 6px;
  padding-inline: 5px;
  color: var(--app-accent-strong);
  background: var(--app-accent-soft);
  line-height: 18px;
}

.intro,
.dur-chapter,
.last-chapter {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
  line-clamp: 1;
  color: var(--app-ink-muted);
  font-size: 11px;
  line-height: 1.45;
}

.intro {
  -webkit-line-clamp: 2;
  line-clamp: 2;
}

.meta-label {
  margin-right: 5px;
  color: var(--app-ink-soft);
  font-weight: 650;
}

.card-arrow {
  position: absolute;
  top: 50%;
  right: 13px;
  color: var(--app-ink-muted);
  opacity: 0;
  transform: translate(-3px, -50%);
  transition:
    opacity var(--app-duration-state) ease-out,
    transform var(--app-duration-state) ease-out;
}

.book-card:hover .card-arrow,
.book-card:focus-visible .card-arrow {
  opacity: 1;
  transform: translate(0, -50%);
}

@media (max-width: 540px) {
  .book-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 8px;
  }

  .book-card {
    min-height: 132px;
    gap: 12px;
    border-radius: 12px;
    padding: 12px 30px 12px 12px;
  }

  .cover-frame {
    width: 70px;
    height: 102px;
  }

  .book-info {
    min-height: 102px;
    gap: 5px;
  }

  .book-name {
    font-size: 14px;
  }

  .update-info {
    gap: 4px;
    font-size: 10px;
  }
}
.books-wrapper{width:100%;min-width:0;overflow:visible}
.book-grid{grid-template-columns:repeat(auto-fill,minmax(min(100%,350px),1fr));justify-content:start;gap:4px}
.book-card{min-height:142px;gap:14px;border-color:transparent;border-radius:6px;padding:12px 10px;background:transparent;box-shadow:none}
.book-card:hover,.book-card:focus-visible{border-color:var(--app-line);background:var(--app-panel);box-shadow:none}
.cover-frame{width:84px;height:112px;border-radius:3px}
.cover-placeholder{padding:0 2px;color:var(--app-ink-muted);font-size:10px;line-height:1.2;white-space:nowrap}
.book-info{min-height:112px;gap:5px}
.book-name{font-size:15px;font-weight:680;line-height:1.42}
.book-byline{font-size:11px}
.intro,.dur-chapter,.last-chapter{font-size:11px;line-height:1.45}
.card-arrow{display:none}
@media(max-width:750px){.book-grid{grid-template-columns:minmax(0,1fr);gap:0}.book-card{min-height:132px;border-radius:0;border-bottom-color:var(--app-line);padding:10px 4px}.book-card:first-child{border-top:1px solid var(--app-line)}.cover-frame{width:72px;height:104px}.book-info{min-height:104px}.book-name{font-size:14px}}
@media(max-width:360px){.book-card{gap:9px}.cover-frame{width:64px;height:96px}.book-info{min-height:96px}.book-byline,.update-info{gap:3px;font-size:10px}}
</style>
