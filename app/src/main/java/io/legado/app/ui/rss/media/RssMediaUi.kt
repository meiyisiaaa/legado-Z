package io.legado.app.ui.rss.media

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.appDb
import io.legado.app.help.glide.ImageLoader
import io.legado.app.help.glide.OkHttpModelLoader
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.model.rss.media.RssMediaCandidate
import io.legado.app.model.rss.media.RssMediaDownloads
import io.legado.app.model.rss.media.RssMediaResolver
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.compose.LegadoTopAppBar
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.ColorUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.bumptech.glide.request.RequestOptions
import android.text.format.Formatter

object RssMediaUi {
    fun show(activity: FragmentActivity, articles: List<RssArticle>, html: String? = null,
             baseUrl: String? = null, captured: List<RssMediaCandidate> = emptyList()) {
        if (activity.supportFragmentManager.isStateSaved) return
        activity.lifecycleScope.launch {
            val snapshot = withContext(Dispatchers.Default) { mediaSelectionSnapshot(articles, html, baseUrl, captured) }
            if (!activity.supportFragmentManager.isStateSaved) RssMediaDialog().apply {
                arguments = Bundle().apply {
                    putString("articles", GSON.toJson(snapshot.articles))
                    putString("captured", GSON.toJson(snapshot.candidates))
                    putString("imageExclusions", GSON.toJson(snapshot.imageExclusions))
                    putString("base", baseUrl)
                }
            }.showNow(activity.supportFragmentManager, "rssMedia")
        }
    }

    fun showTasks(activity: FragmentActivity) {
        if (activity.supportFragmentManager.isStateSaved) return
        RssMediaDialog().apply { arguments = Bundle().apply { putBoolean("tasks", true) } }
            .showNow(activity.supportFragmentManager, "rssMediaTasks")
    }
}

/** Saved state contains article identities and one copy of each media address. */
internal data class RssMediaSelectionSnapshot(val articles: List<RssArticle>, val candidates: List<RssMediaCandidateSnapshot>,
    val imageExclusions: Map<Int, Set<String>> = emptyMap())
internal data class RssMediaCandidateSnapshot(val articleIndex: Int, val url: String, val kind: String,
    val mimeType: String?, val thumbnail: String?, val size: Long?, val label: String?,
    val headers: Map<String, String>, val requestUrl: String) {
    fun restore(articles: List<RssArticle>): RssMediaCandidate? = articles.getOrNull(articleIndex)?.let {
        RssMediaCandidate(it, url, kind, mimeType, thumbnail, size, label, headers, requestUrl)
    }
}

internal fun mediaSelectionSnapshot(articles: List<RssArticle>, html: String?, baseUrl: String?,
    captured: List<RssMediaCandidate>): RssMediaSelectionSnapshot {
    val compact = articles.map { RssArticle(origin = it.origin, sort = it.sort, title = it.title,
        link = it.link, image = it.image, pubDate = it.pubDate, type = it.type) }
    val found = captured + articles.flatMap { article ->
        RssMediaResolver.extract(article, html.takeIf { articles.size == 1 }.orEmpty(),
            baseUrl ?: io.legado.app.utils.NetworkUtils.getAbsoluteURL(article.origin, article.link))
    }
    val exclusions = articles.mapIndexed { index, article -> index to RssMediaResolver.imageUrlsToExclude(
        article, html.takeIf { articles.size == 1 }.orEmpty(),
        baseUrl ?: io.legado.app.utils.NetworkUtils.getAbsoluteURL(article.origin, article.link))
    }.filter { it.second.isNotEmpty() }.toMap()
    val candidates = found.filter { it.url.startsWith("http://", true) || it.url.startsWith("https://", true) }
        .filterNot { it.kind == "image" && (RssMediaResolver.isAuxiliaryImageUrl(it.url) ||
            it.url in exclusions[articles.indexOf(it.article)].orEmpty()) }
        .distinctBy { it.id }.mapNotNull { candidate ->
        val index = articles.indexOf(candidate.article).takeIf { it >= 0 } ?: return@mapNotNull null
        RssMediaCandidateSnapshot(index, candidate.url, candidate.kind, candidate.mimeType, candidate.thumbnail,
            candidate.size, candidate.label?.take(200), RssMediaSniffer.mediaRequestHeaders(candidate.headers), candidate.requestUrl)
    }
    return RssMediaSelectionSnapshot(compact, candidates, exclusions)
}

class RssMediaDialog : BaseDialogFragment(0) {
    private val candidates = mutableStateListOf<RssMediaCandidate>()
    private val excludedImages = mutableMapOf<RssArticle, MutableSet<String>>()
    private var loading by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var tasksPage by mutableStateOf(false)
    private var sniffing by mutableStateOf(false)
    private var sniffArticle by mutableStateOf<RssArticle?>(null)
    private var manualPlaybackArticles by mutableStateOf(emptyList<RssArticle>())
    private var sniffer by mutableStateOf<RssMediaSniffer?>(null)
    private val articles by lazy {
        GSON.fromJsonObject<List<RssArticle>>(arguments?.getString("articles")).getOrNull().orEmpty()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.RssMediaDialog)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        LegadoComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).navigationBarsPadding()) {
                        LegadoTopAppBar(title = { Text(stringResource(if (tasksPage) R.string.rss_media_tasks else R.string.rss_media_download)) },
                            navigationIcon = { IconButton(onClick = {
                                when {
                                    sniffArticle != null -> { sniffArticle = null; sniffer?.stop() }
                                    tasksPage && arguments?.getBoolean("tasks") != true -> tasksPage = false
                                    else -> dismiss()
                                }
                            }) {
                                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                            } }, actions = { if (!tasksPage) IconButton(onClick = { sniffer?.stop(); sniffArticle = null; sniffing = false; tasksPage = true }) {
                                Icon(painterResource(R.drawable.ic_download_line), stringResource(R.string.rss_media_tasks))
                            } })
                        if (tasksPage) Tasks() else {
                            if (sniffArticle != null) {
                                Text(stringResource(R.string.rss_media_play_hint), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
                            }
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                sniffer?.let { activeSniffer ->
                                    val playing = sniffArticle != null
                                    AndroidView(factory = { activeSniffer.view }, modifier = Modifier.fillMaxSize().alpha(if (playing) 1f else 0f), update = {
                                        it.isEnabled = playing
                                        it.importantForAccessibility = if (playing) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                                    })
                                }
                                if (sniffArticle == null) RssMediaSelectionContent(candidates, loading, sniffing, error,
                                    onRetry = ::resolve, onSniff = ::sniff, onOpen = ::openForPlayback,
                                    onSubmit = ::enqueue, articles = articles, manualPlaybackArticles = manualPlaybackArticles)
                            }
                            if (sniffArticle != null) TextButton(onClick = { sniffArticle = null; sniffer?.stop() }) { Text(stringResource(R.string.close)) }
                        }
                    }
                }
            }
        }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        tasksPage = savedInstanceState?.getBoolean("tasksPage") ?: (arguments?.getBoolean("tasks") == true)
        sniffer = RssMediaSniffer(requireContext(), viewLifecycleOwner.lifecycleScope,
            onCandidates = ::addCandidates, onFinished = {
                sniffing = false
                manualPlaybackArticles = articles.filter { article -> article.type != 1 &&
                    candidates.none { it.article == article && it.kind == "video" } }
            }, onError = { error = it }, onImageExclusions = ::excludeImages)
        viewLifecycleOwner.lifecycleScope.launch { RssMediaDownloads.observeTasks() }
        if (arguments?.getBoolean("tasks") != true) {
            GSON.fromJsonObject<Map<Int, Set<String>>>(arguments?.getString("imageExclusions")).getOrNull()
                .orEmpty().forEach { (index, urls) -> articles.getOrNull(index)?.let { excludeImages(it, urls) } }
            addCandidates(GSON.fromJsonObject<List<RssMediaCandidateSnapshot>>(arguments?.getString("captured"))
                .getOrNull().orEmpty().mapNotNull { it.restore(articles) })
            resolve()
        }
    }

    @Suppress("DEPRECATION")
    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.decorView.setPadding(0, 0, 0, 0)
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = ColorUtils.isColorLight(if (AppConfig.isEInkMode) Color.WHITE else ThemeStore.primaryColor(requireContext()))
                isAppearanceLightNavigationBars = ColorUtils.isColorLight(ThemeStore.backgroundColor(requireContext()))
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("tasksPage", tasksPage)
        super.onSaveInstanceState(outState)
    }
    override fun onStop() { sniffer?.stop(); sniffing = false; super.onStop() }
    override fun onDestroyView() { sniffer?.destroy(); sniffer = null; super.onDestroyView() }

    private fun addCandidates(values: List<RssMediaCandidate>) {
        values.filterNot { it.kind == "image" && (RssMediaResolver.isAuxiliaryImageUrl(it.url) ||
            it.url in excludedImages[it.article].orEmpty()) }.forEach { value ->
            val index = candidates.indexOfFirst { it.id == value.id }
            if (index < 0) candidates += value
            else {
                val old = candidates[index]
                candidates[index] = old.copy(mimeType = old.mimeType ?: value.mimeType,
                    thumbnail = old.thumbnail ?: value.thumbnail, size = old.size ?: value.size,
                    label = old.label ?: value.label, headers = value.headers + old.headers)
            }
        }
    }

    private fun excludeImages(article: RssArticle, urls: Set<String>) {
        if (urls.isEmpty()) return
        excludedImages.getOrPut(article) { mutableSetOf() }.addAll(urls)
        candidates.removeAll { it.kind == "image" && it.article == article && it.url in urls }
    }

    private fun resolve() {
        if (loading) return
        loading = true
        error = null
        viewLifecycleOwner.lifecycleScope.launch {
            val failures = mutableListOf<String>()
            try {
                articles.forEach { article ->
                    try {
                        val restored = withContext(Dispatchers.IO) {
                            appDb.rssArticleDao.get(article.origin, article.link, article.sort)
                                ?: appDb.rssStarDao.get(article.origin, article.link)?.toRssArticle() ?: article
                        }
                        val resolved = RssMediaResolver.resolve(restored, null,
                            arguments?.getString("base").takeIf { articles.size == 1 })
                        excludeImages(restored, resolved.excludedImageUrls)
                        addCandidates(resolved.candidates)
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { failures += "${article.title}: ${e.localizedMessage.orEmpty()}" }
                }
                error = failures.takeIf { it.isNotEmpty() }?.joinToString("\n")
            } finally { loading = false }
        }
    }

    private fun sniff() {
        sniffArticle = null
        if (sniffing) { sniffer?.stop(); sniffing = false }
        else { sniffing = true; sniffer?.scan(articles) }
    }
    private fun openForPlayback(article: RssArticle) { sniffing = false; sniffArticle = article; sniffer?.open(article) }

    private fun enqueue(selected: List<RssMediaCandidate>) {
        val submit = {
            if (isAdded) viewLifecycleOwner.lifecycleScope.launch {
                try { RssMediaDownloads.enqueue(selected); tasksPage = true }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.localizedMessage }
            }
            Unit
        }
        withStoragePermission(submit)
    }

    private fun withStoragePermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            PermissionsCompat.Builder().addPermissions(Permissions.WRITE_EXTERNAL_STORAGE)
                .rationale(R.string.rss_media_storage_permission).onGranted(action)
                .onDenied { error = getString(R.string.rss_media_permission_denied) }
                .onError { error = it.localizedMessage }.request()
        } else action()
    }

    @Composable
    @OptIn(ExperimentalLayoutApi::class)
    private fun Tasks() {
        val tasks by RssMediaDownloads.tasks.collectAsState()
        if (tasks.isEmpty()) MediaMessage(stringResource(R.string.rss_media_tasks_empty), Modifier.fillMaxSize())
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
            items(tasks, key = { it.id }) { task ->
                Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                    Text(task.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(if (task.kind == "video") getString(R.string.rss_media_video) else getString(R.string.rss_media_image), task.label,
                        task.totalBytes.takeIf { it > 0 }?.let { "${Formatter.formatShortFileSize(requireContext(), task.bytesDownloaded)} / ${Formatter.formatShortFileSize(requireContext(), it)}" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val status = task.status
                    Text(mediaStatusLabel(status), style = MaterialTheme.typography.labelMedium,
                        color = if (status == "failed" || status == "export_failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
                    if (status == "downloading" || status == "exporting") {
                        if (status == "downloading" && task.totalBytes > 0) LinearProgressIndicator(progress = { (task.bytesDownloaded.toFloat() / task.totalBytes).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                    task.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                    if (status == "failed" || status == "export_failed") RssMediaTaskRecoveryActions(
                        onRetry = { taskAction(R.string.retry) { RssMediaDownloads.retry(task.id) } },
                        onOpenArticle = { io.legado.app.ui.rss.read.ReadRssActivity.start(requireContext(), true,
                            task.origin, task.title, task.link) })
                    else FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        when (status) {
                            "queued", "downloading" -> TaskButton(R.string.rss_media_pause) { RssMediaDownloads.pause(task.id) }
                            "paused" -> TaskButton(R.string.rss_media_resume) { RssMediaDownloads.resume(task.id) }
                            "cancelled" -> TaskButton(R.string.retry) { RssMediaDownloads.retry(task.id) }
                            "completed" -> TextButton(onClick = {
                                runCatching {
                                    val uri = Uri.parse(task.outputUri)
                                    val mime = requireContext().contentResolver.getType(uri)
                                        ?: if (task.kind == "video") "video/*" else "image/*"
                                    startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                                }
                                    .onFailure { error = it.localizedMessage }
                            }) { Text(stringResource(R.string.open)) }
                        }
                        if (status in listOf("queued", "downloading", "paused", "exporting")) TaskButton(R.string.cancel) { RssMediaDownloads.cancel(task.id) }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        }
    }

    @Composable private fun TaskButton(label: Int, action: suspend () -> Unit) {
        TextButton(onClick = { taskAction(label, action) }) { Text(stringResource(label)) }
    }

    private fun taskAction(label: Int, action: suspend () -> Unit) {
        val runAction = {
            viewLifecycleOwner.lifecycleScope.launch {
                try { action() } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.localizedMessage }
            }
            Unit
        }
        if (label == R.string.retry || label == R.string.rss_media_resume) withStoragePermission(runAction)
        else runAction()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RssMediaTaskRecoveryActions(onRetry: () -> Unit, onOpenArticle: () -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        TextButton(onClick = onOpenArticle) { Text(stringResource(R.string.rss_media_open_article)) }
    }
}

@Composable
internal fun RssMediaSelectionContent(candidates: List<RssMediaCandidate>, loading: Boolean,
    sniffing: Boolean, error: String?, onRetry: () -> Unit, onSniff: () -> Unit,
    onOpen: (RssArticle) -> Unit, onSubmit: (List<RssMediaCandidate>) -> Unit, modifier: Modifier = Modifier,
    articles: List<RssArticle> = candidates.map { it.article }.distinct(),
    manualPlaybackArticles: List<RssArticle> = emptyList()) {
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val valid = candidates.filter { it.id in selected }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.rss_media_selected_count, valid.size), Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = { selected = if (valid.size == candidates.size) emptyList() else candidates.map { it.id } }, enabled = candidates.isNotEmpty()) { Text(stringResource(if (valid.size == candidates.size && candidates.isNotEmpty()) R.string.rss_media_deselect_all else R.string.select_all)) }
        }
        if (loading || sniffing) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(if (sniffing) R.string.rss_media_sniffing else R.string.rss_media_resolving), Modifier.padding(horizontal = 20.dp, vertical = 10.dp).semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            if (error != null) item {
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry, enabled = !loading) { Text(stringResource(R.string.retry)) }
            }
            if (articles.isEmpty() && candidates.isEmpty() && !loading && !sniffing) item {
                Text(stringResource(R.string.rss_media_empty), Modifier.padding(vertical = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            articles.forEachIndexed { index, article ->
                val media = candidates.filter { it.article == article }
                item(key = "heading:${article.origin}:${article.link}") {
                    Column(Modifier.padding(top = if (index == 0) 8.dp else 24.dp, bottom = 8.dp)) {
                        Text(article.title, style = MaterialTheme.typography.titleMedium)
                        if (media.isEmpty() && !loading && !sniffing) Text(stringResource(R.string.rss_media_empty),
                            Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val needsPlayback = !loading && !sniffing && (
                            (media.isEmpty() && articles.size == 1) ||
                                (article in manualPlaybackArticles && media.none { it.kind == "video" }) ||
                                (article.type == 2 && media.none { it.kind == "video" }))
                        if (needsPlayback) TextButton(onClick = { onOpen(article) }, contentPadding = PaddingValues(0.dp)) {
                            Text(stringResource(if (media.isEmpty()) R.string.rss_media_open_detect else R.string.rss_media_open_play))
                        }
                    }
                }
                items(media, key = { it.id }) { candidate ->
                    val checked = candidate.id in selected
                    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox) {
                        selected = if (it) selected + candidate.id else selected - candidate.id
                    }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MediaThumbnail(candidate)
                        Column(Modifier.weight(1f)) {
                            Text(candidate.label?.takeIf { it.isNotBlank() } ?: Uri.parse(candidate.url).lastPathSegment.orEmpty().take(80).ifBlank { stringResource(R.string.rss_media_file) },
                                style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val context = LocalContext.current
                            Text(listOfNotNull(stringResource(if (candidate.kind == "video") R.string.rss_media_video else R.string.rss_media_image),
                                candidate.size?.let { Formatter.formatShortFileSize(context, it) }).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Checkbox(checked, onCheckedChange = null)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (candidates.isNotEmpty() || articles.size > 1 || sniffing) TextButton(onClick = onSniff, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (sniffing) R.string.rss_media_stop_sniff else R.string.rss_media_sniff))
            }
            Button(onClick = { onSubmit(valid) }, enabled = valid.isNotEmpty() && !loading && !sniffing,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.rss_media_download_count, valid.size))
            }
        }
    }
}

@Composable private fun MediaThumbnail(candidate: RssMediaCandidate) {
    val url = candidate.thumbnail ?: candidate.url.takeIf { candidate.kind == "image" }
    if (url == null) Surface(Modifier.size(64.dp), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Box(contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_play_24dp), contentDescription = null) }
    } else AndroidView(factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO } },
        modifier = Modifier.size(64.dp), update = { view ->
            if (view.tag != url) {
                view.tag = url
                ImageLoader.load(view.context, url).apply(RequestOptions().set(OkHttpModelLoader.sourceOriginOption, candidate.article.origin))
                    .placeholder(R.drawable.image_rss_article).error(R.drawable.image_rss_article).into(view)
            }
        })
}

@Composable private fun MediaMessage(text: String, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp).semantics { liveRegion = LiveRegionMode.Polite }) }
}

@Composable private fun mediaStatusLabel(status: String): String = stringResource(when (status) {
    "queued" -> R.string.rss_media_queued
    "downloading" -> R.string.rss_media_downloading
    "paused" -> R.string.rss_media_paused
    "exporting" -> R.string.rss_media_exporting
    "completed" -> R.string.rss_media_completed
    "cancelled" -> R.string.rss_media_cancelled
    else -> R.string.rss_media_failed
})
