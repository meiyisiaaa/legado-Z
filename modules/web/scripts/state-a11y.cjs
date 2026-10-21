/*
 * State and accessibility acceptance harness.
 * Requires an existing Playwright runtime; set PLAYWRIGHT_MODULE when `playwright`
 * is not resolvable from this package.
 */
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')

const root = path.resolve(__dirname, '../../..')
const outputDir = path.join(root, 'build/ui-redesign-validation/web-a11y')
fs.mkdirSync(outputDir, { recursive: true })
const webAssets = path.join(root, 'app/src/main/assets/web')
const origin = 'http://127.0.0.1:8080'
const websocketOrigin = 'ws://127.0.0.1:8081'
const checks = []
const requests = []
const browserErrors = []
const httpFailures = []
const screenshots = []
const expectedNetworkFailures = []
const queues = new Map()
const gates = new Map()
let searchMode = 'respond'

const config = () => ({
  theme: 0,
  font: 0,
  fontSize: 18,
  readWidth: 800,
  infiniteLoading: false,
  customFontName: '',
  jumpDuration: 1000,
  spacing: { paragraph: 1, line: 0.8, letter: 0 },
})

const shelfBooks = [
  {
    bookUrl: 'https://books.example/nebula', name: '星海拾荒者', author: '林间行舟',
    origin: 'https://source.example', originName: '星港书源',
    tocUrl: 'https://books.example/nebula/toc', totalChapterNum: 128,
    durChapterIndex: 0, durChapterPos: 120, durChapterTime: 1781892000000,
    durChapterTitle: '第十九章 远航之前', latestChapterTitle: '第一百二十八章 回声',
    latestChapterTime: 1781900000000, lastCheckTime: 1781900000000,
    intro: '在边境星域寻找失落航路的旅人，意外收到一段来自旧时代的信号。',
    coverUrl: '', wordCount: 520000, type: 0, originOrder: 0, syncTime: 0,
  },
]
const searchResult = {
  ...shelfBooks[0],
  bookUrl: 'https://books.example/search-result',
  name: '星海拾荒者·搜索结果',
  respondTime: 320,
  chapterWordCount: 520000,
  time: 1781900000000,
}
const source = {
  bookSourceUrl: 'https://source.example', bookSourceName: '星港书源',
  bookSourceGroup: '科幻', bookSourceType: 0, bookUrlPattern: '', header: '',
  searchUrl: 'https://source.example/search?q={{key}}',
  ruleSearch: { bookList: '.result', name: '.title@text', checkKeyWord: '星海' },
  ruleBookInfo: { name: 'h1@text', author: '.author@text' },
  ruleToc: { chapterList: '.chapter a', chapterName: 'a@text', chapterUrl: 'a@href' },
  ruleContent: { content: '#content@html' }, enabled: true, enabledExplore: true,
  enabledSearch: true, weight: 0, respondTime: 0, lastUpdateTime: 0, customOrder: 0,
}
const chapters = [
  { url: 'https://books.example/nebula/1', title: '第一章 失联的信号', isVolume: false, baseUrl: 'https://books.example/nebula/', bookUrl: shelfBooks[0].bookUrl, index: 0, isVip: false, isPay: false },
  { url: 'https://books.example/nebula/2', title: '第二章 夜航', isVolume: false, baseUrl: 'https://books.example/nebula/', bookUrl: shelfBooks[0].bookUrl, index: 1, isVip: false, isPay: false },
  { url: 'https://books.example/nebula/3', title: '第三章 远航之前', isVolume: false, baseUrl: 'https://books.example/nebula/', bookUrl: shelfBooks[0].bookUrl, index: 2, isVip: false, isPay: false },
]

const success = data => ({ isSuccess: true, errorMsg: '', data })
const failure = errorMsg => ({ isSuccess: false, errorMsg, data: null })

function check(name, ok, detail = '') {
  checks.push({ name, ok: Boolean(ok), detail })
  if (!ok) console.error(`CHECK_FAILED ${name}${detail ? `: ${detail}` : ''}`)
}

function queue(endpoint, ...modes) {
  queues.set(endpoint, [...(queues.get(endpoint) || []), ...modes])
}

function nextMode(endpoint) {
  const modes = queues.get(endpoint) || []
  const mode = modes.shift() || 'success'
  queues.set(endpoint, modes)
  return mode
}

function gate(name) {
  let release
  const promise = new Promise(resolve => { release = resolve })
  gates.set(name, promise)
  return () => {
    release()
    gates.delete(name)
  }
}

async function respond(route, endpoint, payload, errorMsg = `模拟${endpoint}业务失败`) {
  const mode = nextMode(endpoint)
  const record = { endpoint, mode, url: route.request().url() }
  const request = route.request()
  if (request.method() !== 'GET') {
    try { record.body = request.postDataJSON() } catch { record.body = null }
  }
  requests.push(record)
  if (mode === 'network') {
    expectedNetworkFailures.push(record)
    await route.abort('failed')
    return
  }
  if (mode === 'hold-business') await gates.get(endpoint)
  const body = mode === 'business' || mode === 'hold-business'
    ? failure(errorMsg)
    : success(payload)
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
}

function contentType(file) {
  return ({
    '.css': 'text/css; charset=utf-8', '.html': 'text/html; charset=utf-8',
    '.js': 'text/javascript; charset=utf-8', '.json': 'application/json; charset=utf-8',
    '.md': 'text/markdown; charset=utf-8', '.png': 'image/png', '.svg': 'image/svg+xml',
    '.woff': 'font/woff', '.woff2': 'font/woff2', '.ttf': 'font/ttf', '.ico': 'image/x-icon',
  })[path.extname(file).toLowerCase()] || 'application/octet-stream'
}

async function mockRequest(route) {
  const request = route.request()
  const url = new URL(request.url())
  const pathname = decodeURIComponent(url.pathname)

  if (pathname.startsWith('/help/') || pathname.startsWith('/uploadBook/')) {
    const file = path.resolve(webAssets, `.${pathname}`)
    const relative = path.relative(webAssets, file)
    if (!relative || relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
      await route.fulfill({ status: 404, body: 'Not found' })
      return
    }
    await route.fulfill({ path: file, contentType: contentType(file) })
    return
  }

  if (pathname === '/cover') {
    await route.fulfill({ status: 200, contentType: 'image/svg+xml', body: '<svg xmlns="http://www.w3.org/2000/svg" width="160" height="220"><rect width="160" height="220" fill="#137f80"/></svg>' })
    return
  }

  if (pathname.endsWith('/getReadConfig')) return respond(route, 'getReadConfig', JSON.stringify(config()), '模拟阅读配置失败')
  if (pathname.endsWith('/getBookshelf')) return respond(route, 'getBookshelf', shelfBooks, '模拟书架加载失败')
  if (pathname.endsWith('/getBookSources')) return respond(route, 'getBookSources', [source])
  if (pathname.endsWith('/getChapterList')) return respond(route, 'getChapterList', chapters, '模拟目录加载失败')
  if (pathname.endsWith('/getBookContent')) {
    const chapterIndex = Number(url.searchParams.get('index') || 0)
    const text = `<h2>${chapters[chapterIndex]?.title || '章节'}</h2><p>远方的星图正在缓慢亮起。</p><p>林舟停下脚步，听见设备中传来一声清晰的回响。</p>`
    return respond(route, 'getBookContent', text, '模拟章节内容失败')
  }
  if (pathname.endsWith('/saveBook')) return respond(route, 'saveBook', '', '模拟加入书架失败')
  if (pathname.endsWith('/saveBookSource')) return respond(route, 'saveBookSource', '', '模拟源保存失败')
  if (pathname.endsWith('/deleteBookSources')) return respond(route, 'deleteBookSources', '', '模拟批量删除失败')
  if (pathname.endsWith('/saveReadConfig') || pathname.endsWith('/saveBookProgress')) {
    const record = { endpoint: pathname.split('/').pop(), mode: 'success', url: request.url() }
    requests.push(record)
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(success('')) })
    return
  }
  if (url.origin === origin) {
    const base = path.join(webAssets, 'vue')
    const file = path.resolve(base, pathname === '/' ? 'index.html' : `.${pathname}`)
    const relative = path.relative(base, file)
    if (relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
      await route.fulfill({ status: 404, body: 'Not found' })
    } else {
      await route.fulfill({ path: file, contentType: contentType(file) })
    }
    return
  }
  await route.continue()
}

async function setViewport(page, width) {
  await page.setViewportSize({ width, height: width < 500 ? 844 : 900 })
  await page.waitForTimeout(60)
}

async function screenshot(page, name) {
  const output = path.join(outputDir, `${name}.png`)
  await page.screenshot({ path: output, fullPage: true, animations: 'disabled' })
  screenshots.push(path.basename(output))
}

async function openShelf(page, width, suffix) {
  await setViewport(page, width)
  await page.goto(`${origin}/?a11y=${suffix}#/`, { waitUntil: 'domcontentloaded' })
  await page.locator('.index-wrapper').waitFor({ timeout: 8000 })
}

async function openReader(page, width, suffix) {
  await setViewport(page, width)
  await page.goto(`${origin}/?a11y=${suffix}#/chapter`, { waitUntil: 'domcontentloaded' })
  await page.locator('.chapter-wrapper').waitFor({ timeout: 8000 })
}

async function assertBusyCleared(page, name, selector) {
  await page.waitForFunction(selector => {
    const element = document.querySelector(selector)
    return element && element.getAttribute('aria-busy') !== 'true' && !element.disabled
  }, selector, { timeout: 5000 })
  check(name, true)
}

async function testShelfAndSearch(page, width) {
  // Cold, empty store: exercise network rejection at 390 px and HTTP 200 false at 1280 px.
  queue('getReadConfig', 'success')
  queue('getBookshelf', width === 390 ? 'network' : 'business', 'success')
  await openShelf(page, width, `shelf-retry-${width}`)
  const shelfError = page.getByRole('alert').filter({ hasText: '书架加载失败' })
  await shelfError.waitFor({ state: 'visible', timeout: 7000 })
  check(`shelf-${width}-cold-load-failure-visible`, await shelfError.isVisible())
  check(`shelf-${width}-cold-load-empty-state-not-faked`, await page.locator('.book-card').count() === 0)
  await screenshot(page, `shelf-${width}-load-error`)
  await shelfError.getByRole('button', { name: '重试' }).click()
  await page.getByRole('button', { name: /打开《星海拾荒者/ }).waitFor({ timeout: 7000 })
  check(`shelf-${width}-retry-loads-books`, await page.locator('.book-card').count() === 1 && await shelfError.count() === 0)

  await page.locator('.search-input input').fill('星海')
  searchMode = 'respond'
  await page.getByRole('button', { name: '在线搜索' }).click()
  await page.getByRole('button', { name: /打开《星海拾荒者·搜索结果/ }).waitFor({ timeout: 7000 })
  check(`search-${width}-result-visible-before-save`, await page.locator('.book-card').count() === 2)

  const releaseSave = gate('saveBook')
  queue('saveBook', 'hold-business')
  await page.getByRole('button', { name: /打开《星海拾荒者·搜索结果/ }).click()
  await page.waitForFunction(() => document.querySelector('.shelf-wrapper')?.getAttribute('aria-busy') === 'true', null, { timeout: 4000 })
  const savingResult = page.getByRole('button', { name: /正在将《星海拾荒者·搜索结果》加入书架/ })
  check(`search-${width}-save-busy-announced`, await savingResult.isDisabled() && await savingResult.getAttribute('aria-busy') === 'true')
  const otherResult = page.getByRole('button', { name: /打开《远方航线/ })
  check(`search-${width}-other-results-disabled-during-save`, await otherResult.isDisabled())
  const pendingSaveCount = requests.filter(item => item.endpoint === 'saveBook').length
  await otherResult.evaluate(element => element.dispatchEvent(new MouseEvent('click', { bubbles: true })))
  await page.waitForTimeout(100)
  check(`search-${width}-concurrent-save-rejected`, requests.filter(item => item.endpoint === 'saveBook').length === pendingSaveCount)
  releaseSave()
  await page.getByText('模拟加入书架失败').waitFor({ state: 'visible', timeout: 5000 })
  await assertBusyCleared(page, `search-${width}-business-failure-clears-busy`, '.shelf-wrapper')
  const resultAfterBusinessFailure = page.getByRole('button', { name: /打开《星海拾荒者·搜索结果/ })
  check(`search-${width}-business-failure-keeps-result`, await page.locator('.book-card').count() === 2 && (await resultAfterBusinessFailure.innerText()).includes('搜索结果'))
  check(`search-${width}-business-failure-result-control-unbusy`, await resultAfterBusinessFailure.isEnabled() && await resultAfterBusinessFailure.getAttribute('aria-busy') !== 'true')
  await screenshot(page, `search-${width}-save-error`)

  await page.locator('.el-message--error').first().waitFor({ state: 'hidden', timeout: 6000 }).catch(() => {})
  queue('saveBook', 'network')
  await page.getByRole('button', { name: /打开《星海拾荒者·搜索结果/ }).click()
  await page.getByText('加入书架失败，请检查阅读服务后重试。').waitFor({ state: 'visible', timeout: 5000 })
  await assertBusyCleared(page, `search-${width}-network-failure-clears-busy`, '.shelf-wrapper')
  check(`search-${width}-network-failure-keeps-result`, await page.locator('.book-card').count() === 2 && (await page.getByRole('button', { name: /打开《星海拾荒者·搜索结果/ }).innerText()).includes('搜索结果'))
  const resultAfterNetworkFailure = page.getByRole('button', { name: /打开《星海拾荒者·搜索结果/ })
  check(`search-${width}-network-failure-result-control-unbusy`, await resultAfterNetworkFailure.isEnabled() && await resultAfterNetworkFailure.getAttribute('aria-busy') !== 'true')
}

async function testReaderConfigAndContent(page, width) {
  // 200/isSuccess=false and a rejected request must both surface a retry path.
  queue('getReadConfig', 'business', 'success')
  queue('getChapterList', 'success')
  await openReader(page, width, `config-business-${width}`)
  const configError = page.locator('.catalog-error[role="alert"]')
  await configError.waitFor({ state: 'visible', timeout: 7000 })
  check(`reader-${width}-config-business-error-visible`, (await configError.innerText()).includes('阅读配置加载失败'))
  await screenshot(page, `reader-${width}-config-error`)
  await configError.getByRole('button', { name: '重试' }).click()
  await page.getByRole('heading', { name: '第一章 失联的信号', exact: true }).waitFor({ timeout: 7000 })
  check(`reader-${width}-config-business-retry-recovers`, await configError.count() === 0)

  queue('getReadConfig', 'network', 'success')
  queue('getChapterList', 'success')
  await openReader(page, width, `config-network-${width}`)
  const networkConfigError = page.locator('.catalog-error[role="alert"]')
  await networkConfigError.waitFor({ state: 'visible', timeout: 7000 })
  check(`reader-${width}-config-network-error-visible`, await networkConfigError.isVisible())
  await networkConfigError.getByRole('button', { name: '重试' }).click()
  await page.getByRole('heading', { name: '第一章 失联的信号', exact: true }).waitFor({ timeout: 7000 })
  check(`reader-${width}-config-network-retry-recovers`, await networkConfigError.count() === 0)

  queue('getReadConfig', 'success')
  queue('getChapterList', 'business', 'success')
  await openReader(page, width, `catalog-retry-${width}`)
  const catalogError = page.locator('.catalog-error[role="alert"]')
  await catalogError.waitFor({ state: 'visible', timeout: 7000 })
  check(`reader-${width}-catalog-business-error-visible`, (await catalogError.innerText()).includes('目录或阅读配置加载失败'))
  await catalogError.getByRole('button', { name: '重试' }).click()
  await page.getByRole('heading', { name: '第一章 失联的信号', exact: true }).waitFor({ timeout: 7000 })
  check(`reader-${width}-catalog-retry-recovers`, await catalogError.count() === 0)

  queue('getReadConfig', 'success')
  queue('getChapterList', 'success')
  queue('getBookContent', 'business', 'success', 'network', 'success')
  await openReader(page, width, `content-retry-${width}`)
  const contentError = page.getByRole('alert').filter({ hasText: '第一章 失联的信号' }).last()
  await contentError.waitFor({ state: 'visible', timeout: 7000 })
  check(`reader-${width}-content-business-error-visible`, (await contentError.innerText()).includes('模拟章节内容失败'))
  await screenshot(page, `reader-${width}-content-error`)
  await contentError.getByRole('button', { name: '重试加载第一章 失联的信号' }).click()
  await page.getByText('林舟停下脚步，听见设备中传来一声清晰的回响。').waitFor({ timeout: 7000 })
  check(`reader-${width}-content-business-retry-recovers`, await page.getByRole('alert').count() === 0)

  // A second chapter load verifies network rejection and retry independently of HTTP 200 false.
  if (width < 776) await page.locator('.chapter-wrapper').click({ position: { x: 120, y: 130 } })
  await page.getByRole('button', { name: '下一章' }).click()
  const networkContentError = page.getByRole('alert').filter({ hasText: '第二章 夜航' }).last()
  await networkContentError.waitFor({ state: 'visible', timeout: 7000 })
  check(`reader-${width}-content-network-error-visible`, (await networkContentError.innerText()).includes('检查阅读服务'))
  await networkContentError.getByRole('button', { name: '重试加载第二章 夜航' }).click()
  await page.getByText('林舟停下脚步，听见设备中传来一声清晰的回响。').last().waitFor({ timeout: 7000 })
  check(`reader-${width}-content-network-retry-recovers`, await networkContentError.count() === 0)
}

async function testReaderKeyboardAndTheme(page, width) {
  queue('getReadConfig', 'success')
  queue('getChapterList', 'success')
  queue('getBookContent', 'success', 'success')
  const contentRequestStart = requests.filter(item => item.endpoint === 'getBookContent').length
  await openReader(page, width, `keyboard-${width}`)
  await page.getByRole('heading', { name: '第一章 失联的信号', exact: true }).waitFor({ timeout: 7000 })
  if (width < 776) await page.locator('.chapter-wrapper').click({ position: { x: 120, y: 130 } })
  const toolBounds = await page.locator('.tool-icon').evaluateAll(elements => elements.map(element => { const rect = element.getBoundingClientRect(); return { width: rect.width, height: rect.height, bottom: rect.bottom } }))
  check(`reader-${width}-controls-at-least-44px-and-within-viewport`, toolBounds.every(rect => rect.width >= 44 && rect.height >= 44 && rect.bottom <= (width < 500 ? 844 : 900)), JSON.stringify(toolBounds))
  await page.getByRole('button', { name: '打开章节目录' }).click()
  const firstChapter = page.getByRole('button', { name: '第一章 失联的信号', exact: true })
  await firstChapter.waitFor({ state: 'visible', timeout: 5000 })
  check(`reader-${width}-catalog-current-announced`, await firstChapter.getAttribute('aria-current') === 'true')
  const secondChapter = page.getByRole('button', { name: '第二章 夜航', exact: true })
  await secondChapter.focus()
  await page.keyboard.press('Enter')
  await page.waitForFunction(() => document.querySelector('.chapter .content')?.innerText.includes('第二章 夜航'), null, { timeout: 7000 })
  await page.getByRole('button', { name: '打开章节目录' }).click()
  const currentSecond = page.getByRole('button', { name: '第二章 夜航', exact: true })
  await currentSecond.waitFor({ state: 'visible', timeout: 5000 })
  check(`reader-${width}-catalog-enter-selects-current`, await currentSecond.getAttribute('aria-current') === 'true')
  check(`reader-${width}-catalog-enter-loads-chapter`, requests.slice(contentRequestStart).some(item => item.endpoint === 'getBookContent' && new URL(item.url).searchParams.get('index') === '1'))

  // Close the catalog and open settings using the documented accessible control.
  await page.keyboard.press('Escape').catch(() => {})
  await currentSecond.waitFor({ state: 'hidden', timeout: 3000 })
  check(`reader-${width}-catalog-escape-restores-focus`, await page.getByRole('button', { name: '打开章节目录' }).evaluate(element => element === document.activeElement))
  if (await page.getByRole('button', { name: '打开章节目录' }).count()) {
    await page.getByRole('button', { name: '阅读设置' }).click()
  }
  const settings = page.locator('.settings-wrapper')
  await settings.waitFor({ state: 'visible', timeout: 5000 })
  const themeButton = page.getByRole('button', { name: '夜间阅读主题' })
  check(`reader-${width}-theme-starts-unpressed`, await themeButton.getAttribute('aria-pressed') === 'false')
  await themeButton.click()
  await page.locator('.chapter-wrapper.night').waitFor({ timeout: 5000 })
  check(`reader-${width}-theme-pressed-state-updates`, await themeButton.getAttribute('aria-pressed') === 'true')

  const fontInput = page.getByRole('textbox', { name: '自定义字体名称' })
  await fontInput.fill('方向键测试字体')
  const contentRequestsBeforeArrows = requests.filter(item => item.endpoint === 'getBookContent').length
  await fontInput.focus()
  await page.keyboard.press('ArrowLeft')
  await page.keyboard.press('ArrowRight')
  await page.keyboard.press('ArrowUp')
  await page.keyboard.press('ArrowDown')
  await page.waitForTimeout(120)
  check(`reader-${width}-font-input-arrows-do-not-switch-chapter`, requests.filter(item => item.endpoint === 'getBookContent').length === contentRequestsBeforeArrows)
  check(`reader-${width}-font-input-retains-focus`, await fontInput.evaluate(element => element === document.activeElement))
  await screenshot(page, `reader-${width}-theme-and-font-settings`)
  const fontSave = settings.getByRole('button', { name: '保存', exact: true })
  await fontSave.click()
  const fontDialog = page.getByRole('dialog').filter({ hasText: '已经安装在您的设备上的字体' })
  await fontDialog.waitFor({ state: 'visible', timeout: 3000 })
  const requestsBeforeDialogArrows = requests.filter(item => item.endpoint === 'getBookContent').length
  await page.keyboard.press('ArrowLeft')
  await page.keyboard.press('ArrowRight')
  check(`reader-${width}-dialog-arrows-do-not-turn-page`, requests.filter(item => item.endpoint === 'getBookContent').length === requestsBeforeDialogArrows)
  await page.keyboard.press('Escape')
  await fontDialog.waitFor({ state: 'hidden', timeout: 3000 })
  check(`reader-${width}-nested-escape-preserves-settings`, await settings.isVisible() && await fontSave.evaluate(element => element === document.activeElement))
  await page.keyboard.press('Escape')
  await settings.waitFor({ state: 'hidden', timeout: 3000 })
  check(`reader-${width}-settings-escape-restores-focus`, await page.getByRole('button', { name: '阅读设置' }).evaluate(element => element === document.activeElement))
}

async function testSourceEditor(page, width) {
  queue('getReadConfig', 'success')
  queue('getBookSources', 'success')
  await setViewport(page, width)
  await page.goto(`${origin}/?editorRoute=bookSource&a11y=source-${width}#/bookSource`, { waitUntil: 'domcontentloaded' })
  await page.locator('.editor').waitFor({ timeout: 8000 })
  if (width < 1024) {
    await page.locator('#editor-panel-tools').click()
    await page.waitForFunction(() => document.querySelector('#editor-panel-tools')?.getAttribute('aria-selected') === 'true', null, { timeout: 3000 })
    await page.waitForTimeout(380)
  }
  const sourceTabs = page.locator('#source-tools [role="tab"]')
  await sourceTabs.filter({ hasText: '源列表' }).click()
  await page.getByRole('button', { name: '拉取源', exact: true }).click()
  const library = page.locator('.source-library')
  await library.getByText('星港书源', { exact: true }).waitFor({ timeout: 7000 })
  const checkbox = page.getByRole('checkbox', { name: /星港书源/ })
  await checkbox.waitFor({ state: 'attached', timeout: 5000 })
  await checkbox.focus()
  await page.keyboard.press('Space')
  check(`source-${width}-checkbox-independently-selects`, await checkbox.isChecked() && await page.getByRole('button', { name: /删除所选/ }).isEnabled())
  await page.keyboard.press('Tab')
  const editButton = page.getByRole('button', { name: '编辑星港书源' })
  check(`source-${width}-tab-moves-from-checkbox-to-edit`, await editButton.evaluate(element => element === document.activeElement))
  await page.keyboard.press('Enter')
  check(`source-${width}-edit-does-not-clear-checkbox`, await checkbox.isChecked() && await editButton.getAttribute('aria-current') === 'true')

  const deleteButton = page.getByRole('button', { name: /删除所选/ })
  const deleteRequestsBeforeCancel = requests.filter(item => item.endpoint === 'deleteBookSources').length
  await deleteButton.click()
  const cancelDialog = page.locator('.el-message-box')
  await cancelDialog.waitFor({ state: 'visible', timeout: 4000 })
  check(`source-${width}-delete-disabled-during-confirmation`, await deleteButton.isDisabled())
  await cancelDialog.getByRole('button', { name: '取消' }).click()
  await cancelDialog.waitFor({ state: 'hidden', timeout: 4000 })
  check(`source-${width}-delete-cancel-keeps-selection`, await checkbox.isChecked() && await deleteButton.isEnabled())
  check(`source-${width}-delete-cancel-sends-no-request`, requests.filter(item => item.endpoint === 'deleteBookSources').length === deleteRequestsBeforeCancel)

  queue('deleteBookSources', 'business')
  await deleteButton.click()
  const businessDialog = page.locator('.el-message-box')
  await businessDialog.getByRole('button', { name: '删除', exact: true }).click()
  await page.getByText('模拟批量删除失败').waitFor({ state: 'visible', timeout: 5000 })
  check(`source-${width}-delete-business-failure-keeps-selection`, await checkbox.isChecked() && await deleteButton.isEnabled() && await library.getByText('星港书源', { exact: true }).count() === 1)
  await screenshot(page, `source-${width}-delete-error`)

  await page.locator('.el-message--error').first().waitFor({ state: 'hidden', timeout: 6000 }).catch(() => {})
  queue('deleteBookSources', 'network')
  await deleteButton.click()
  const networkDialog = page.locator('.el-message-box')
  await networkDialog.getByRole('button', { name: '删除', exact: true }).click()
  await page.locator('.el-message--error').filter({ hasText: /Network Error|模拟批量删除失败/ }).waitFor({ state: 'visible', timeout: 5000 })
  check(`source-${width}-delete-network-failure-keeps-selection`, await checkbox.isChecked() && await deleteButton.isEnabled() && await library.getByText('星港书源', { exact: true }).count() === 1)

  const saveButton = page.getByRole('button', { name: '保存源', exact: true })
  const releaseSave = gate('saveBookSource')
  queue('saveBookSource', 'hold-business')
  await saveButton.click()
  await page.waitForFunction(() => {
    const button = [...document.querySelectorAll('button')].find(item => item.getAttribute('aria-label') === '保存源')
    return button?.disabled === true && button.getAttribute('aria-busy') === 'true'
  }, null, { timeout: 4000 })
  releaseSave()
  await page.getByText(/源《星港书源》保存失败/).waitFor({ state: 'visible', timeout: 5000 })
  check(`source-${width}-business-save-failure-visible-and-busy-clears`, await saveButton.isEnabled() && await saveButton.getAttribute('aria-busy') !== 'true' && await page.locator('.el-message--error').filter({ hasText: '模拟源保存失败' }).count() > 0)

  await page.locator('.el-message--error').first().waitFor({ state: 'hidden', timeout: 6000 }).catch(() => {})
  queue('saveBookSource', 'network')
  await saveButton.click()
  await page.locator('.el-message--error').filter({ hasText: /Network Error/ }).waitFor({ state: 'visible', timeout: 5000 })
  check(`source-${width}-network-save-failure-visible-and-busy-clears`, await saveButton.isEnabled() && await saveButton.getAttribute('aria-busy') !== 'true')
}

async function run() {
  const browser = await chromium.launch({ headless: true })
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, colorScheme: 'light' })
  await context.addInitScript(apiOrigin => {
    localStorage.setItem('remoteUrl', apiOrigin)
    localStorage.setItem('legado_web_hotkeys', JSON.stringify(Array.from({ length: 9 }, () => [])))
    localStorage.setItem('tabName', 'editTab')
    sessionStorage.setItem('bookUrl', 'https://books.example/nebula')
    sessionStorage.setItem('bookName', '星海拾荒者')
    sessionStorage.setItem('bookAuthor', '林间行舟')
    sessionStorage.setItem('chapterIndex', '0')
    sessionStorage.setItem('chapterPos', '0')
    sessionStorage.setItem('isSeachBook', 'false')
  }, origin)
  await context.route('**/*', mockRequest)
  const page = await context.newPage()
  page.on('response', response => {
    if (response.status() >= 400) httpFailures.push({ status: response.status(), url: response.url() })
  })
  page.on('pageerror', error => browserErrors.push(error.message))
  page.on('console', message => {
    if (message.type() === 'error' && !/Failed to load resource/.test(message.text())) browserErrors.push(message.text())
  })
  page.routeWebSocket(`${websocketOrigin}/searchBook`, socket => {
    socket.onMessage(message => {
      let payload
      try { payload = JSON.parse(message) } catch { payload = message }
      requests.push({ endpoint: 'searchBook', method: 'WS', payload })
      if (searchMode === 'respond') {
        socket.send(JSON.stringify([searchResult, { ...searchResult, bookUrl: 'https://books.example/other-result', name: '远方航线' }]))
        setTimeout(() => { try { socket.close() } catch {} }, 80)
      }
    })
  })

  try {
    for (const width of [320, 390, 1280]) {
      await testShelfAndSearch(page, width)
      await testReaderConfigAndContent(page, width)
      await testReaderKeyboardAndTheme(page, width)
      await testSourceEditor(page, width)
    }
    check('no-browser-page-errors', browserErrors.length === 0, JSON.stringify(browserErrors))
    check('no-unexpected-http-errors', httpFailures.length === 0, JSON.stringify(httpFailures))
    const report = {
      testedAt: new Date().toISOString(),
      viewportWidths: [320, 390, 1280],
      checks,
      failed: checks.filter(item => !item.ok),
      expectedNetworkFailures: expectedNetworkFailures.map(({ endpoint, url }) => ({ endpoint, url })),
      browserErrors,
      httpFailures,
      screenshots,
      boundaries: [
        'HTTP API and search WebSocket calls used deterministic Playwright route fixtures; this does not prove real Android persistence or provider behavior.',
        'Playwright keyboard events verify DOM focus and page key handling only; TalkBack and physical-device keyboard behavior remain in root device acceptance.',
      ],
    }
    fs.writeFileSync(path.join(outputDir, 'report.json'), JSON.stringify(report, null, 2), 'utf8')
    console.log(JSON.stringify({ checks: checks.length, failed: report.failed, screenshots, expectedNetworkFailures: report.expectedNetworkFailures.length }, null, 2))
    if (report.failed.length) process.exitCode = 1
  } finally {
    await context.close()
    await browser.close()
  }
}

run().catch(error => {
  console.error(error.stack || error)
  process.exitCode = 1
})
