<template>
  <div
    class="settings-wrapper"
    :style="popupTheme"
    :class="{ night: isNight, day: !isNight }"
  >
    <div class="settings-title">设置</div>
    <div class="setting-list">
      <ul>
        <li class="theme-list">
          <i>阅读主题</i>
          <button
            class="theme-item"
            type="button"
            v-for="(themeColor, index) in themeColors"
            :key="index"
            :style="themeColor"
            ref="themes"
            @click="setTheme(index)"
            :class="{ selected: theme == index }"
            :aria-label="index === 6 ? '夜间阅读主题' : `阅读主题 ${index + 1}`"
            :aria-pressed="theme === index"
            ><el-icon><Moon v-if="index === 6" /><Sunny v-else /></el-icon></button
          >
        </li>
        <li class="font-list">
          <i>正文字体</i>
          <button
            class="font-item"
            v-for="(font, index) in fonts"
            :key="index"
            type="button"
            :class="{ selected: selectedFont == index }"
            :aria-pressed="selectedFont === index"
            @click="setFont(index)"
            >{{ font }}</button
          >
        </li>
        <li class="font-list">
          <i>自定字体</i>
          <el-tooltip effect="dark" content="自定义的字体名称" placement="top">
            <input
              type="text"
              class="font-item font-item-input"
              v-model="customFontName"
              placeholder="请输入自定义的字体名称"
              aria-label="自定义字体名称"
            />
          </el-tooltip>

          <el-popover
            placement="top"
            width="270"
            role="dialog"
            aria-labelledby="custom-font-dialog-label"
            trigger="click"
            v-model:visible="customFontSavePopVisible"
            @before-enter="focusCustomFontDialog"
          >
            <div ref="customFontDialogContent" tabindex="-1" @keydown.esc.stop.prevent="closeCustomFontDialog">
            <span id="custom-font-dialog-label" class="sr-only">保存自定义字体</span>
            <p>
              已经安装在您的设备上的字体请确认输入的字体名称完整无误，或者从网络下载字体。
            </p>
            <div style="text-align: right; margin: 0">
              <el-button
                size="small"
                plain
                @click="closeCustomFontDialog"
                >取消</el-button
              >
              <el-button type="primary" size="small" @click="setCustomFont()"
                >确定</el-button
              >
              <el-button type="primary" size="small" @click="loadFontFromURL()"
                >网络下载</el-button
              >
            </div>
            </div>
            <template #reference>
              <button
                ref="customFontTrigger"
                type="button"
                class="font-item"
                :aria-expanded="customFontSavePopVisible"
                aria-haspopup="dialog"
              >保存</button>
            </template>
          </el-popover>
        </li>
        <li class="font-size">
          <i>字体大小</i>
          <div class="resize">
            <button class="less" type="button" aria-label="减小字体" :disabled="fontSize <= 12" @click="lessFontSize">
              <em class="iconfont" aria-hidden="true">&#58966;</em>
            </button>
            <b aria-hidden="true"></b>
            <span class="lang">{{ fontSize }}</span>
            <b aria-hidden="true"></b>
            <button class="more" type="button" aria-label="增大字体" :disabled="fontSize >= 48" @click="moreFontSize">
              <em class="iconfont" aria-hidden="true">&#58976;</em>
            </button>
          </div>
        </li>
        <li class="letter-spacing">
          <i>字距</i>
          <div class="resize">
            <button class="less" type="button" aria-label="缩小字距" @click="lessLetterSpacing">
              <em class="iconfont" aria-hidden="true">&#58966;</em>
            </button>
            <b aria-hidden="true"></b>
            <span class="lang">{{ spacing.letter.toFixed(2) }}</span>
            <b aria-hidden="true"></b>
            <button class="more" type="button" aria-label="增大字距" @click="moreLetterSpacing">
              <em class="iconfont" aria-hidden="true">&#58976;</em>
            </button>
          </div>
        </li>
        <li class="line-spacing">
          <i>行距</i>
          <div class="resize">
            <button class="less" type="button" aria-label="缩小行距" @click="lessLineSpacing">
              <em class="iconfont" aria-hidden="true">&#58966;</em>
            </button>
            <b aria-hidden="true"></b>
            <span class="lang">{{ spacing.line.toFixed(1) }}</span>
            <b aria-hidden="true"></b>
            <button class="more" type="button" aria-label="增大行距" @click="moreLineSpacing">
              <em class="iconfont" aria-hidden="true">&#58976;</em>
            </button>
          </div>
        </li>
        <li class="paragraph-spacing">
          <i>段距</i>
          <div class="resize">
            <button class="less" type="button" aria-label="缩小段距" @click="lessParagraphSpacing">
              <em class="iconfont" aria-hidden="true">&#58966;</em>
            </button>
            <b aria-hidden="true"></b>
            <span class="lang">{{ spacing.paragraph.toFixed(1) }}</span>
            <b aria-hidden="true"></b>
            <button class="more" type="button" aria-label="增大段距" @click="moreParagraphSpacing">
              <em class="iconfont" aria-hidden="true">&#58976;</em>
            </button>
          </div>
        </li>
        <li class="read-width" v-if="!store.miniInterface">
          <i>页面宽度</i>
          <div class="resize">
            <button class="less" type="button" aria-label="缩小页面宽度" :disabled="readWidth <= 640" @click="lessReadWidth">
              <em class="iconfont" aria-hidden="true">&#58965;</em>
            </button>
            <b aria-hidden="true"></b>
            <span class="lang">{{ readWidth }}</span>
            <b aria-hidden="true"></b>
            <button class="more" type="button" aria-label="增大页面宽度" :disabled="!canIncreaseReadWidth" @click="moreReadWidth">
              <em class="iconfont" aria-hidden="true">&#58975;</em>
            </button>
          </div>
        </li>
        <li class="paragraph-spacing">
          <i>翻页速度</i>
          <div class="resize">
            <button class="less" type="button" aria-label="缩短翻页时间" :disabled="jumpDuration === 0" @click="lessJumpDuration">
              <em class="iconfont" aria-hidden="true">&#xe625;</em>
            </button>
            <b aria-hidden="true"></b>
            <span class="lang">{{ jumpDuration }}</span>
            <b aria-hidden="true"></b>
            <button class="more" type="button" aria-label="延长翻页时间" @click="moreJumpDuration">
              <em class="iconfont" aria-hidden="true">&#xe626;</em>
            </button>
          </div>
        </li>
        <li class="infinite-loading">
          <i>无限加载</i>
          <button
            type="button"
            class="infinite-loading-item"
            :key="0"
            :class="{ selected: infiniteLoading == false }"
            :aria-pressed="infiniteLoading === false"
            @click="setInfiniteLoading(false)"
            >关闭</button
          >
          <button
            type="button"
            class="infinite-loading-item"
            :key="1"
            :class="{ selected: infiniteLoading == true }"
            :aria-pressed="infiniteLoading === true"
            @click="setInfiniteLoading(true)"
            >开启</button
          >
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import '../assets/fonts/popfont.css'
import '../assets/fonts/iconfont.css'
import settings from '../config/themeConfig'
import { Moon, Sunny } from '@element-plus/icons-vue'
import API from '@api'
import { useDebounceFn } from '@vueuse/shared'

const store = useBookStore()
const saveConfigDebounce = useDebounceFn(async () => {
  const { data } = await API.saveReadConfig(store.config)
  if (!data.isSuccess) ElMessage.error(data.errorMsg || '阅读设置保存失败')
}, 500)
//阅读界面设置改变时保存同步配置
watch(
  () => store.config,
  () => {
    // axios reports request failures globally; prevent the debounced promise from becoming unhandled.
    void saveConfigDebounce().catch(() => {})
  },
  {
    deep: 2, //深度为2
  },
)

//主题颜色
const theme = computed(() => store.theme)
const isNight = computed(() => store.isNight)
const themeColors = [
  {
    background: '#e8efee',
  },
  {
    background: 'rgba(245, 234, 204, 0.8)',
  },
  {
    background: 'rgba(230, 242, 230, 0.8)',
  },
  {
    background: 'rgba(228, 241, 245, 0.8)',
  },
  {
    background: 'rgba(245, 228, 228, 0.8)',
  },
  {
    background: 'rgba(224, 224, 224, 0.8)',
  },
  {
    background: '#182528',
  },
]
const popupTheme = computed(() => {
  return {
    background: settings.themes[theme.value].popup,
    '--settings-visible-height': `${visibleViewportHeight.value}px`,
  }
})
const visibleViewportHeight = ref(
  window.visualViewport?.height ?? window.innerHeight,
)
const viewportWidth = ref(window.innerWidth)
const updateVisibleViewportHeight = () => {
  visibleViewportHeight.value =
    window.visualViewport?.height ?? window.innerHeight
  viewportWidth.value = window.innerWidth
}
onMounted(() => {
  updateVisibleViewportHeight()
  window.addEventListener('resize', updateVisibleViewportHeight, {
    passive: true,
  })
  window.visualViewport?.addEventListener('resize', updateVisibleViewportHeight, {
    passive: true,
  })
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', updateVisibleViewportHeight)
  window.visualViewport?.removeEventListener(
    'resize',
    updateVisibleViewportHeight,
  )
})
const setTheme = (theme: number) => {
  store.config.theme = theme
}

//预置字体
const fonts = ref(['雅黑', '宋体', '楷书'])
const setFont = (font: number) => {
  store.config.font = font
}
const selectedFont = computed(() => {
  return store.config.font
})
//自定义字体
const customFontName = ref(store.config.customFontName)
const customFontSavePopVisible = ref(false)
const customFontTrigger = ref<HTMLButtonElement>()
const customFontDialogContent = ref<HTMLDivElement>()
const focusCustomFontDialog = () => nextTick(() => customFontDialogContent.value?.focus())
const closeCustomFontDialog = () => {
  customFontSavePopVisible.value = false
  nextTick(() => customFontTrigger.value?.focus())
}
watch(() => store.readSettingsVisible, visible => {
  if (!visible) customFontSavePopVisible.value = false
})
const setCustomFont = () => {
  closeCustomFontDialog()
  store.config.font = -1
  store.config.customFontName = customFontName.value
}
// 加载网络字体
const loadFontFromURL = () => {
  customFontSavePopVisible.value = false
  ElMessageBox.prompt('请输入 字体网络链接', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPattern: /^https?:.+$/,
    inputErrorMessage: 'url 形式不正确',
    beforeClose: (action, instance, done) => {
      if (action === 'confirm') {
        instance.confirmButtonLoading = true
        instance.confirmButtonText = '下载中……'
        // instance.inputValue
        const url = instance.inputValue
        if (typeof FontFace !== 'function') {
          ElMessage.error('浏览器不支持FontFace')
          return done()
        }
        const fontface = new FontFace(customFontName.value, `url("${url}")`)
        document.fonts.add(fontface)
        fontface
          .load()
          //API.getBookShelf()
          .then(function () {
            instance.confirmButtonLoading = false
            ElMessage.info('字体加载成功！')
            setCustomFont()
            done()
          })
          .catch(function () {
            instance.confirmButtonLoading = false
            instance.confirmButtonText = '确定'
            ElMessage.error('下载失败，请检查您输入的 url')
          })
      } else {
        done()
      }
    },
  })
}

//字体大小
const fontSize = computed(() => {
  return store.config.fontSize
})
const moreFontSize = () => {
  if (store.config.fontSize < 48) store.config.fontSize += 2
}
const lessFontSize = () => {
  if (store.config.fontSize > 12) store.config.fontSize -= 2
}

//字 行 段落间距
const spacing = computed(() => {
  return store.config.spacing
})
const lessLetterSpacing = () => {
  store.config.spacing.letter -= 0.01
}
const moreLetterSpacing = () => {
  store.config.spacing.letter += 0.01
}
const lessLineSpacing = () => {
  store.config.spacing.line -= 0.1
}
const moreLineSpacing = () => {
  store.config.spacing.line += 0.1
}
const lessParagraphSpacing = () => {
  store.config.spacing.paragraph -= 0.1
}
const moreParagraphSpacing = () => {
  store.config.spacing.paragraph += 0.1
}

//页面宽度
const readWidth = computed(() => {
  return store.config.readWidth
})
const canIncreaseReadWidth = computed(
  () => readWidth.value + 160 + 136 <= viewportWidth.value,
)
const moreReadWidth = () => {
  // 此时会截断页面
  if (store.config.readWidth + 160 + 2 * 68 > window.innerWidth) return
  store.config.readWidth += 160
}
const lessReadWidth = () => {
  if (store.config.readWidth > 640) store.config.readWidth -= 160
}

//翻页速度
const jumpDuration = computed(() => {
  return store.config.jumpDuration
})
const moreJumpDuration = () => {
  store.config.jumpDuration += 100
}
const lessJumpDuration = () => {
  if (store.config.jumpDuration === 0) return
  store.config.jumpDuration -= 100
}

//无限加载
const infiniteLoading = computed(() => {
  return store.config.infiniteLoading
})
const setInfiniteLoading = (loading: boolean) => {
  store.config.infiniteLoading = loading
}
</script>

<style lang="scss" scoped>
:deep(.iconfont) {
  font-family: iconfont;
  font-style: normal;
}

.settings-wrapper {
  user-select: none;
  margin: -13px;
  font-family: Inter, 'Noto Sans SC', 'Microsoft YaHei', system-ui, sans-serif;
  /*   width: 478px;
  height: 350px; */
  text-align: left;
  padding: 40px 0 40px 24px;
  background: #ede7da url('../assets/imgs/themes/popup_1.png') repeat;

  .settings-title {
    font-size: 18px;
    line-height: 22px;
    margin-bottom: 28px;
    font-weight: 400;
  }

  .setting-list {
    max-height: calc(70vh - 50px);
    overflow: auto;

    ul {
      list-style: none outside none;
      margin: 0;
      padding: 0;

      li {
        list-style: none outside none;

        i {
          font:
            12px / 16px Inter,
            'Noto Sans SC',
            'Microsoft YaHei',
            system-ui,
            sans-serif;
          display: inline-block;
          min-width: 48px;
          margin-right: 16px;
          vertical-align: middle;
          color: var(--app-ink-muted);
        }

        .theme-item {
          line-height: 32px;
          width: 34px;
          height: 34px;
          margin-right: 16px;
          margin-top: 5px;
          border-radius: 100%;
          display: inline-block;
          cursor: pointer;
          text-align: center;
          vertical-align: middle;
          padding: 0;
          color: var(--app-ink-muted);
          font: inherit;

          .iconfont {
            display: none;
          }

          .el-icon {
            vertical-align: middle;
          }
        }

        .selected {
          color: var(--app-accent-strong);
          box-shadow: 0 0 0 2px var(--app-accent-glow);

          .iconfont {
            display: inline;
          }
        }
      }

      .font-list,
      .infinite-loading {
        margin-top: 28px;

        .font-item,
        .infinite-loading-item {
          width: 78px;
          height: 34px;
          cursor: pointer;
          margin-right: 16px;
          border-radius: 2px;
          text-align: center;
          vertical-align: middle;
          display: inline-block;
          font-size: 14px;
          font-family: inherit;
          line-height: 34px;
        }
        button.font-item,
        button.infinite-loading-item {
          color: var(--app-ink-soft);
          font: inherit;
          font-size: 14px;
          line-height: 34px;
          cursor: pointer;
          appearance: none;
        }
        .font-item-input {
          width: 168px;
          color: var(--app-ink);
          cursor: text;
        }
        .selected {
          color: #ed4259;
          border: 1px solid #ed4259;
        }

        .font-item:hover,
        .infinite-loading-item:hover {
          border: 1px solid #ed4259;
          color: #ed4259;
        }
      }

      .font-size,
      .read-width,
      .letter-spacing,
      .line-spacing,
      .paragraph-spacing {
        margin-top: 28px;

        .resize {
          display: grid;
          grid-template-columns: minmax(44px, 1fr) 1px minmax(56px, 1.1fr) 1px minmax(44px, 1fr);
          width: 274px;
          height: 44px;
          vertical-align: middle;
          border-radius: 2px;

          > button,
          > .lang {
            box-sizing: border-box;
            min-width: 44px;
            min-height: 44px;
            margin: 0;
            border: 0;
            padding: 0;
            display: flex;
            align-items: center;
            justify-content: center;
          }

          > button {
            color: inherit;
            background: transparent;
            font: inherit;
            cursor: pointer;
            appearance: none;
          }

          > button:disabled {
            cursor: not-allowed;
            opacity: .48;
          }

          > button em {
            font-style: normal;
          }

          > .lang {
            color: var(--app-ink-soft);
            font-weight: 400;
            font-family: inherit;
            text-align: center;
            white-space: nowrap;
          }

          .less:hover:not(:disabled),
          .more:hover:not(:disabled) {
            color: #ed4259;
          }

          .lang {
            color: var(--app-ink-soft);
            font-weight: 400;
          }

          b {
            display: block;
            width: 1px;
            height: 20px;
            background: var(--app-line);
          }
        }
      }
    }
  }
}

.night {
  :deep(.theme-item) {
    border: 1px solid #666;
  }

  :deep(.selected) {
    border: 1px solid #666;
  }

  :deep(.font-list),
  .infinite-loading {
    .font-item,
    .infinite-loading-item {
      border: 1px solid #666;
      background: rgba(45, 45, 45, 0.5);
    }
  }

  :deep(.resize) {
    border: 1px solid #666;
    background: rgba(45, 45, 45, 0.5);

    b {
      border-right: 1px solid #666;
    }
  }
}

.day {
  :deep(.theme-item) {
    border: 1px solid #e5e5e5;
  }

  :deep(.selected) {
    border: 1px solid #ed4259;
  }

  :deep(.font-list),
  .infinite-loading {
    .font-item,
    .infinite-loading-item {
      background: rgba(255, 255, 255, 0.5);
      border: 1px solid rgba(0, 0, 0, 0.1);
    }
  }

  :deep(.resize) {
    border: 1px solid #e5e5e5;
    background: rgba(255, 255, 255, 0.5);

    b {
      border-right: 1px solid #e5e5e5;
    }
  }
}

@media screen and (max-width: 500px) {
  .settings-wrapper i {
    display: flex !important;
    flex-wrap: wrap;
    padding-bottom: 5px !important;
  }

  .settings-wrapper {
    padding-left: 16px;

    :deep(.setting-list) {
      max-height: calc(78vh - 50px);
      max-height: min(
        calc(78vh - 50px),
        calc(var(--settings-visible-height, 100vh) - 220px)
      );
      overflow-y: auto;
      overscroll-behavior-y: contain;
      -webkit-overflow-scrolling: touch;
    }

    :deep(.resize) {
      width: min(274px, calc(100vw - 64px)) !important;
      display: grid !important;
      grid-template-columns: minmax(44px, 1fr) 1px minmax(56px, 1.1fr) 1px minmax(44px, 1fr);
      height: 44px;
      align-items: center;
      box-sizing: border-box;
    }

    :deep(.resize > button),
    :deep(.resize > .lang) {
      width: auto !important;
      min-width: 44px;
      min-height: 44px;
    }
  }
}
</style>
