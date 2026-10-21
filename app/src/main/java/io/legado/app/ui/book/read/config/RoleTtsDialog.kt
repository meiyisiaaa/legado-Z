package io.legado.app.ui.book.read.config

import android.content.DialogInterface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.ai.AiConfig
import io.legado.app.ai.client.OpenAiClient
import io.legado.app.ai.tts.BookTtsRole
import io.legado.app.ai.tts.RoleTtsRolesConfig
import io.legado.app.ai.tts.RoleTtsRoleRegistry
import io.legado.app.ai.tts.RoleTtsVoiceBinding
import io.legado.app.ai.tts.StoryboardEngine
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.tts.RoleTtsCacheManager
import io.legado.app.help.tts.RoleTtsConfigStore
import io.legado.app.help.http.okHttpClient
import io.legado.app.model.CacheBook
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.page.provider.LayoutProgressListener
import io.legado.app.service.RoleTtsSystemEngine
import io.legado.app.service.RoleTtsSystemVoiceCatalog
import io.legado.app.utils.toastOnUi
import io.legado.app.ui.widget.compose.LegadoTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class RoleTtsDialog : BaseDialogFragment(R.layout.dialog_role_tts) {

    private lateinit var book: Book
    private var rolesConfig by mutableStateOf(RoleTtsRolesConfig())
    private var enabled by mutableStateOf(false)
    private var activeRoleId by mutableStateOf<String?>(null)
    private var inputTitle by mutableStateOf<Int?>(null)
    private var inputMessage by mutableStateOf<Int?>(null)
    private var inputValue by mutableStateOf("")
    private var inputRoleId by mutableStateOf<String?>(null)
    private var inputIsAlias by mutableStateOf(false)
    private var selectionTitle by mutableStateOf<String?>(null)
    private var selectionItems by mutableStateOf<List<String>>(emptyList())
    private var selectionAction by mutableStateOf<((Int) -> Unit)?>(null)
    private var chapters by mutableStateOf<List<BookChapter>>(emptyList())
    private var selectedChapterIndices by mutableStateOf(setOf<Int>())
    private var showChapterPicker by mutableStateOf(false)
    private var showGenerationProgress by mutableStateOf(false)
    private var progressChapter by mutableStateOf("")
    private var progressDetail by mutableStateOf("")
    private var progressValue by mutableFloatStateOf(0f)
    private var generationJob: Job? = null

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawableResource(R.color.background)
            decorView.setPadding(0, 0, 0, 0)
            val attributes = attributes
            attributes.dimAmount = 0f
            attributes.gravity = Gravity.BOTTOM
            this.attributes = attributes
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val currentBook = ReadBook.book
        if (currentBook == null) {
            dismissAllowingStateLoss()
            return
        }
        book = currentBook
        rolesConfig = RoleTtsConfigStore.load(book)
        ensureNarratorRole()
        enabled = book.config.roleTtsEnabled
        view.findViewById<ComposeView>(R.id.legado_compose_host).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LegadoTheme {
                    RoleTtsContent(
                        enabled = enabled,
                        roles = roleItems(),
                        onEnabledChange = { value ->
                            enabled = value
                            RoleTtsConfigStore.setEnabled(book, value)
                            ReadAloud.upReadAloudClass()
                        },
                        onRoleClick = { activeRoleId = it },
                        onAddRole = { promptRoleName() },
                        onGenerate = { showChapterPicker() }
                    )
                    renderDialogs()
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        generationJob?.cancel()
        showGenerationProgress = false
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        generationJob?.cancel()
        super.onDestroyView()
    }

    private fun ensureNarratorRole() {
        val narratorName = getString(R.string.role_tts_role_narrator)
        val narrator = RoleTtsRoleRegistry.findByNameOrAlias(rolesConfig, narratorName)
        if (narrator == null) {
            rolesConfig = RoleTtsRoleRegistry.create(
                narratorName, UUID.randomUUID().toString(), rolesConfig
            )
            RoleTtsConfigStore.save(book, rolesConfig)
        }
    }

    private fun roleItems(): List<RoleTtsRoleItem> = rolesConfig.roles.map { role ->
        val aliasText = role.aliases.takeIf { it.isNotEmpty() }
            ?.joinToString("、")
            ?.let { "${getString(R.string.role_tts_add_alias)}：$it" }
        val voice = role.binding
        val bindingText = when (voice?.provider) {
            RoleTtsVoiceBinding.Provider.SYSTEM -> getString(
                R.string.role_tts_system_voice_label,
                "${voice?.engineId.orEmpty()} · ${voice?.voiceName.orEmpty()}"
            )
            RoleTtsVoiceBinding.Provider.HTTP -> getString(
                R.string.role_tts_http_voice_label,
                appDb.httpTTSDao.get(voice?.httpTtsId ?: -1L)?.name.orEmpty()
            )
            null -> getString(R.string.role_tts_unbound)
        }
        RoleTtsRoleItem(
            id = role.id,
            name = role.name,
            detail = listOfNotNull(aliasText, bindingText).joinToString("\n")
        )
    }

    @androidx.compose.runtime.Composable
    private fun renderDialogs() {
        val activeRole = rolesConfig.roles.firstOrNull { it.id == activeRoleId }
        if (activeRole != null) {
            ReaderConfigChoiceDialog(
                title = activeRole.name,
                options = listOf(
                    getString(R.string.role_tts_rename),
                    getString(R.string.role_tts_add_alias),
                    getString(R.string.role_tts_bind_voice)
                ),
                onDismiss = { activeRoleId = null },
                onSelect = { index ->
                    activeRoleId = null
                    when (index) {
                        0 -> promptRoleName(activeRole)
                        1 -> promptAlias(activeRole)
                        2 -> chooseBinding(activeRole)
                    }
                }
            )
        }
        val title = inputTitle
        if (title != null) {
            AlertDialog(
                onDismissRequest = { inputTitle = null },
                title = { Text(stringResource(title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        inputMessage?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
                        OutlinedTextField(
                            value = inputValue,
                            onValueChange = { inputValue = it },
                            modifier = Modifier.fillMaxWidth().testTag("role_tts_input"),
                            singleLine = true,
                            label = { Text(stringResource(if (inputIsAlias) R.string.role_tts_alias_prompt else R.string.role_tts_create_role)) }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = ::submitInput) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { inputTitle = null }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
        val selection = selectionTitle
        if (selection != null) {
            ReaderConfigChoiceDialog(
                title = selection,
                options = selectionItems,
                onDismiss = { clearSelection() },
                onSelect = { index ->
                    val action = selectionAction
                    clearSelection()
                    action?.invoke(index)
                }
            )
        }
        if (showChapterPicker) {
            AlertDialog(
                onDismissRequest = { showChapterPicker = false },
                title = { Text(stringResource(R.string.role_tts_chapter_picker)) },
                text = {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(chapters, key = { index, chapter -> "${chapter.index}:$index" }) { index, chapter ->
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = index in selectedChapterIndices,
                                        role = Role.Checkbox
                                    ) { checked ->
                                        selectedChapterIndices = if (checked) {
                                            selectedChapterIndices + index
                                        } else selectedChapterIndices - index
                                    }
                                    .testTag("role_tts_chapter_${chapter.index}"),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = index in selectedChapterIndices,
                                    onCheckedChange = null
                                )
                                Text("${chapter.index + 1}. ${chapter.title}")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = selectedChapterIndices.isNotEmpty(),
                        modifier = Modifier.testTag("role_tts_generate_selected"),
                        onClick = {
                            val selected = chapters.filterIndexed { index, _ -> index in selectedChapterIndices }
                            showChapterPicker = false
                            if (selected.isEmpty()) {
                                requireContext().toastOnUi(getString(R.string.role_tts_no_chapter_selected))
                            } else beginGeneration(selected)
                        }
                    ) { Text(stringResource(R.string.role_tts_generate_selected)) }
                },
                dismissButton = {
                    TextButton(onClick = { showChapterPicker = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
        if (showGenerationProgress) {
            AlertDialog(
                onDismissRequest = { generationJob?.cancel() },
                title = { Text(stringResource(R.string.role_tts_generating)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(progressChapter)
                        Text(progressDetail, style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(progress = { progressValue }, modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    TextButton(onClick = { generationJob?.cancel() }) {
                        Text(stringResource(R.string.role_tts_cancel_generation))
                    }
                }
            )
        }
    }

    private fun promptRoleName(role: BookTtsRole? = null) {
        inputTitle = if (role == null) R.string.role_tts_create_role else R.string.role_tts_rename
        inputMessage = if (role == null) null else R.string.role_tts_rename_prompt
        inputValue = role?.name.orEmpty()
        inputRoleId = role?.id
        inputIsAlias = false
    }

    private fun promptAlias(role: BookTtsRole) {
        inputTitle = R.string.role_tts_add_alias
        inputMessage = null
        inputValue = ""
        inputRoleId = role.id
        inputIsAlias = true
    }

    private fun submitInput() {
        val value = inputValue.trim()
        if (value.isBlank() && !inputIsAlias) return
        runCatching {
            rolesConfig = if (inputIsAlias) {
                RoleTtsRoleRegistry.addAlias(rolesConfig, inputRoleId.orEmpty(), value)
            } else {
                val roleId = inputRoleId
                if (roleId == null) RoleTtsRoleRegistry.create(value, UUID.randomUUID().toString(), rolesConfig)
                else RoleTtsRoleRegistry.renameOrMerge(rolesConfig, roleId, value)
            }
            persistRoles()
            inputTitle = null
        }.onFailure {
            requireContext().toastOnUi(it.localizedMessage ?: getString(R.string.role_tts_role_duplicate))
        }
    }

    private fun chooseBinding(role: BookTtsRole) {
        setSelection(
            getString(R.string.role_tts_bind_voice) + " · " + role.name,
            listOf(
                getString(R.string.role_tts_follow_narrator),
                getString(R.string.role_tts_system_voice),
                getString(R.string.role_tts_http_voice),
                getString(R.string.role_tts_clear_voice)
            )
        ) { which ->
            when (which) {
                0, 3 -> updateBinding(role.id, null)
                1 -> chooseSystemEngine(role)
                2 -> chooseHttpProfile(role)
            }
        }
    }

    private fun chooseSystemEngine(role: BookTtsRole) {
        lifecycleScope.launch {
            val engines = runCatching { RoleTtsSystemVoiceCatalog.enumerateEngines(requireContext()) }
                .getOrElse { emptyList() }
            if (!isAdded) return@launch
            if (engines.isEmpty()) {
                requireContext().toastOnUi(getString(R.string.role_tts_no_system_voices))
                return@launch
            }
            setSelection(getString(R.string.role_tts_system_voice), engines.map { it.label }) { index ->
                enumerateVoices(role, engines[index])
            }
        }
    }

    private fun enumerateVoices(role: BookTtsRole, engine: RoleTtsSystemEngine) {
        lifecycleScope.launch {
            val voices = runCatching {
                RoleTtsSystemVoiceCatalog.enumerate(requireContext(), engine.id)
            }.getOrElse { emptyList() }
            if (!isAdded) return@launch
            if (voices.isEmpty()) {
                requireContext().toastOnUi(getString(R.string.role_tts_no_system_voices))
                return@launch
            }
            setSelection(engine.label, voices.map { "${it.voiceName} · ${it.localeTag}" }) { index ->
                val voice = voices[index]
                updateBinding(
                    role.id,
                    RoleTtsVoiceBinding(
                        provider = RoleTtsVoiceBinding.Provider.SYSTEM,
                        engineId = voice.engineId,
                        voiceName = voice.voiceName
                    )
                )
            }
        }
    }

    private fun chooseHttpProfile(role: BookTtsRole) {
        val profiles = appDb.httpTTSDao.all.sortedBy { it.name.lowercase() }
        if (profiles.isEmpty()) {
            requireContext().toastOnUi(getString(R.string.role_tts_no_voice_configs))
            return
        }
        setSelection(getString(R.string.role_tts_http_voice), profiles.map { it.name }) { index ->
            val profile = profiles[index]
            updateBinding(role.id, RoleTtsVoiceBinding(RoleTtsVoiceBinding.Provider.HTTP, httpTtsId = profile.id))
        }
    }

    private fun setSelection(title: String, items: List<String>, onSelect: (Int) -> Unit) {
        selectionTitle = title
        selectionItems = items
        selectionAction = onSelect
    }

    private fun clearSelection() {
        selectionTitle = null
        selectionItems = emptyList()
        selectionAction = null
    }

    private fun updateBinding(roleId: String, voice: RoleTtsVoiceBinding?) {
        rolesConfig = rolesConfig.copy(
            roles = rolesConfig.roles.map { role -> if (role.id == roleId) role.copy(binding = voice) else role }
        )
        persistRoles()
    }

    private fun persistRoles() {
        RoleTtsConfigStore.save(book, rolesConfig)
    }

    private fun showChapterPicker() {
        val availableChapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
        if (availableChapters.isEmpty()) {
            requireContext().toastOnUi(getString(R.string.role_tts_no_chapters))
            return
        }
        chapters = availableChapters
        selectedChapterIndices = emptySet()
        showChapterPicker = true
    }

    private fun beginGeneration(selected: List<BookChapter>) {
        generationJob?.cancel()
        val options = AiConfig.snapshot()
        if (options.endpoint.isBlank() || options.apiKey.isBlank() || options.model.isBlank()) {
            requireContext().toastOnUi(getString(R.string.role_tts_ai_not_configured))
            return
        }
        val roles = rolesConfig.roles.toList()
        val engine = StoryboardEngine(OpenAiClient(okHttpClient))
        val cache = RoleTtsCacheManager(book)
        progressChapter = ""
        progressDetail = ""
        progressValue = 0f
        showGenerationProgress = true
        generationJob = lifecycleScope.launch {
            var completed = 0
            var failed = 0
            try {
                selected.forEachIndexed { selectedIndex, chapter ->
                    currentCoroutineContext().ensureActive()
                    updateProgressChapter(selectedIndex, selected.size, chapter, selected, 0, 1)
                    try {
                        val source = loadChapterText(chapter)
                        currentCoroutineContext().ensureActive()
                        val storyboard = engine.create(
                            options = options,
                            chapterIndex = chapter.index,
                            text = source,
                            roles = roles
                        ) { chunk, total ->
                            lifecycleScope.launch(Main) {
                                updateProgressChapter(selectedIndex, selected.size, chapter, selected, chunk, total)
                            }
                        }
                        currentCoroutineContext().ensureActive()
                        withContext(IO) {
                            currentCoroutineContext().ensureActive()
                            cache.saveStoryboard(storyboard, source, options, roles)
                        }
                        completed++
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        failed++
                        AppLog.put("本章分镜失败，播放时按普通旁白处理：${chapter.title}\n${error.localizedMessage}", error)
                        withContext(Main) {
                            progressDetail = getString(R.string.role_tts_failed_chapter, chapter.title)
                        }
                    }
                }
                withContext(Main) {
                    showGenerationProgress = false
                    requireContext().toastOnUi(getString(R.string.role_tts_generation_done, completed, failed))
                }
            } catch (_: CancellationException) {
                withContext(Main) {
                    showGenerationProgress = false
                    if (isAdded) requireContext().toastOnUi(getString(R.string.role_tts_cancelled))
                }
            } finally {
                generationJob = null
            }
        }
    }

    private fun updateProgressChapter(
        index: Int,
        count: Int,
        chapter: BookChapter,
        selected: List<BookChapter>,
        completedChunks: Int,
        totalChunks: Int
    ) {
        if (!isAdded || !showGenerationProgress) return
        progressChapter = getString(R.string.role_tts_progress_chapter, index + 1, count, chapter.title)
        progressDetail = getString(R.string.role_tts_progress_chunks, completedChunks, totalChunks)
        val fraction = (index + completedChunks.toFloat() / totalChunks.coerceAtLeast(1)) / count.coerceAtLeast(1)
        progressValue = fraction.coerceIn(0f, 1f)
        val range = if (selected.size > 1) {
            "${selected.first().index + 1}–${selected.last().index + 1}"
        } else {
            "${selected.first().index + 1}"
        }
        progressDetail = "${progressDetail} · $range"
    }

    private suspend fun loadChapterText(chapter: BookChapter): String {
        val raw = withContext(IO) {
            BookHelp.getContent(book, chapter) ?: run {
                val source = appDb.bookSourceDao.getBookSource(book.origin)
                    ?: throw IllegalStateException(getString(R.string.role_tts_load_chapter_failed, chapter.title))
                CacheBook.getOrCreate(source, book).downloadAwait(chapter)
            }
        }
        currentCoroutineContext().ensureActive()
        if (raw.startsWith("获取正文失败") || raw.startsWith("加载正文失败")) {
            throw IllegalStateException(raw)
        }

        val processor = ContentProcessor.get(book)
        val title = chapter.getDisplayTitle(
            processor.getTitleReplaceRules(),
            book.getUseReplaceRule(),
            replaceBook = book.toReplaceBook()
        )
        val content = processor.getContent(book, chapter, raw, includeTitle = false)
        val textChapter = ChapterProvider.getTextChapterAsync(
            ReadBook,
            book,
            chapter,
            title,
            content,
            ReadBook.simulatedChapterSize
        )
        val layoutReady = CompletableDeferred<Unit>()
        textChapter.setProgressListener(object : LayoutProgressListener {
            override fun onLayoutCompleted() {
                layoutReady.complete(Unit)
            }

            override fun onLayoutException(e: Throwable) {
                layoutReady.completeExceptionally(e)
            }
        })
        if (textChapter.isCompleted) layoutReady.complete(Unit)
        return try {
            layoutReady.await()
            currentCoroutineContext().ensureActive()
            textChapter.getNeedReadAloud(0, false, 0)
        } finally {
            textChapter.cancelLayout()
        }
    }
}
