package io.legado.app.ui.book.read.config

import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.lib.theme.getSecondaryTextColor
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.dialogs.selector
import io.legado.app.model.CacheBook
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.page.provider.LayoutProgressListener
import io.legado.app.service.RoleTtsSystemEngine
import io.legado.app.service.RoleTtsSystemVoiceCatalog
import io.legado.app.utils.applyTint
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
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

    private val binding by viewBinding(io.legado.app.databinding.DialogRoleTtsBinding::bind)
    private lateinit var book: Book
    private var rolesConfig = RoleTtsRolesConfig()
    private var generationJob: Job? = null
    private var progressDialog: AlertDialog? = null
    private var progressChapter: TextView? = null
    private var progressDetail: TextView? = null
    private var progressBar: ProgressBar? = null

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
        val background = requireContext().bottomBackground
        val backgroundIsLight = ColorUtils.isColorLight(background)
        val primaryTextColor = requireContext().getPrimaryTextColor(backgroundIsLight)
        val secondaryTextColor = requireContext().getSecondaryTextColor(backgroundIsLight)
        binding.roleTtsRoot.setBackgroundColor(background)
        binding.roleTtsTitle.setTextColor(primaryTextColor)
        binding.roleTtsSummary.setTextColor(secondaryTextColor)
        binding.roleTtsRolesHeading.setTextColor(primaryTextColor)
        binding.roleTtsEnabled.setTextColor(primaryTextColor)
        val accent = ThemeStore.accentColor(requireContext())
        binding.roleTtsEnabled.applyTint(accent, !backgroundIsLight)
        binding.roleTtsAddRole.setTextColor(accent)
        binding.roleTtsGenerate.applyTint(accent, !backgroundIsLight)
        binding.roleTtsEnabled.isChecked = book.config.roleTtsEnabled
        binding.roleTtsEnabled.setOnCheckedChangeListener { _, enabled ->
            RoleTtsConfigStore.setEnabled(book, enabled)
            ReadAloud.upReadAloudClass()
        }
        binding.roleTtsAddRole.setOnClickListener { promptRoleName() }
        binding.roleTtsGenerate.setOnClickListener { showChapterPicker() }
        renderRoles()
    }

    override fun onDismiss(dialog: DialogInterface) {
        generationJob?.cancel()
        progressDialog?.dismiss()
        progressDialog = null
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

    private fun renderRoles() {
        val container = binding.roleTtsRoleList
        container.removeAllViews()
        rolesConfig.roles.forEach { role ->
            val row = TextView(requireContext()).apply {
                val aliasText = role.aliases.takeIf { it.isNotEmpty() }?.joinToString("、")
                val voiceBinding = role.binding
                val bindingText = when (voiceBinding?.provider) {
                    RoleTtsVoiceBinding.Provider.SYSTEM -> getString(
                        R.string.role_tts_system_voice_label,
                        "${voiceBinding?.engineId.orEmpty()} · ${voiceBinding?.voiceName.orEmpty()}"
                    )
                    RoleTtsVoiceBinding.Provider.HTTP -> getString(
                        R.string.role_tts_http_voice_label,
                        appDb.httpTTSDao.get(voiceBinding?.httpTtsId ?: -1L)?.name.orEmpty()
                    )
                    null -> getString(R.string.role_tts_unbound)
                }
                text = buildString {
                    append(role.name)
                    if (!aliasText.isNullOrBlank()) append("\n").append(getString(R.string.role_tts_add_alias)).append("：").append(aliasText)
                    append("\n").append(bindingText)
                }
                textSize = 14f
                val backgroundIsLight = ColorUtils.isColorLight(requireContext().bottomBackground)
                setTextColor(requireContext().getPrimaryTextColor(backgroundIsLight))
                setPadding(14.dpToPx(), 10.dpToPx(), 12.dpToPx(), 10.dpToPx())
                minHeight = 52.dpToPx()
                isClickable = true
                isFocusable = true
                setBackgroundResource(android.R.drawable.list_selector_background)
                contentDescription = text
                setOnClickListener { showRoleActions(role) }
            }
            container.addView(
                row,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }
    }

    private fun showRoleActions(role: BookTtsRole) {
        val actions = arrayOf(
            getString(R.string.role_tts_rename),
            getString(R.string.role_tts_add_alias),
            getString(R.string.role_tts_bind_voice)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(role.name)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> promptRoleName(role)
                    1 -> promptAlias(role)
                    2 -> chooseBinding(role)
                }
            }
            .show()
            .applyTint()
    }

    private fun promptRoleName(role: BookTtsRole? = null) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
            setText(role?.name.orEmpty())
            setSelection(text.length)
            hint = getString(R.string.role_tts_create_role)
        }
        val builder = AlertDialog.Builder(requireContext())
            .setTitle(if (role == null) R.string.role_tts_create_role else R.string.role_tts_rename)
            .setView(input)
        if (role != null) builder.setMessage(R.string.role_tts_rename_prompt)
        builder
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) return@setPositiveButton
                runCatching {
                    rolesConfig = if (role == null) {
                        RoleTtsRoleRegistry.create(name, UUID.randomUUID().toString(), rolesConfig)
                    } else {
                        RoleTtsRoleRegistry.renameOrMerge(rolesConfig, role.id, name)
                    }
                    persistRoles()
                }.onFailure { requireContext().toastOnUi(it.localizedMessage ?: getString(R.string.role_tts_role_duplicate)) }
            }
            .show()
            .applyTint()
    }

    private fun promptAlias(role: BookTtsRole) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
            hint = getString(R.string.role_tts_alias_prompt)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.role_tts_add_alias)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                runCatching {
                    rolesConfig = RoleTtsRoleRegistry.addAlias(rolesConfig, role.id, input.text.toString())
                    persistRoles()
                }.onFailure { requireContext().toastOnUi(it.localizedMessage ?: getString(R.string.role_tts_role_duplicate)) }
            }
            .show()
            .applyTint()
    }

    private fun chooseBinding(role: BookTtsRole) {
        val options = listOf(
            getString(R.string.role_tts_follow_narrator),
            getString(R.string.role_tts_system_voice),
            getString(R.string.role_tts_http_voice),
            getString(R.string.role_tts_clear_voice)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.role_tts_bind_voice) + " · " + role.name)
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0, 3 -> updateBinding(role.id, null)
                    1 -> chooseSystemEngine(role)
                    2 -> chooseHttpProfile(role)
                }
            }
            .show()
            .applyTint()
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
            requireContext().selector(
                getString(R.string.role_tts_system_voice),
                engines.map { it.label }
            ) { _, index -> enumerateVoices(role, engines[index]) }
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
            requireContext().selector(
                engine.label,
                voices.map { "${it.voiceName} · ${it.localeTag}" }
            ) { _, index ->
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
        requireContext().selector(
            getString(R.string.role_tts_http_voice),
            profiles.map { it.name }
        ) { _, index ->
            val profile = profiles[index]
            updateBinding(
                role.id,
                RoleTtsVoiceBinding(RoleTtsVoiceBinding.Provider.HTTP, httpTtsId = profile.id)
            )
        }
    }

    private fun updateBinding(roleId: String, voice: RoleTtsVoiceBinding?) {
        rolesConfig = rolesConfig.copy(
            roles = rolesConfig.roles.map { role -> if (role.id == roleId) role.copy(binding = voice) else role }
        )
        persistRoles()
    }

    private fun persistRoles() {
        RoleTtsConfigStore.save(book, rolesConfig)
        renderRoles()
    }

    private fun showChapterPicker() {
        val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
        if (chapters.isEmpty()) {
            requireContext().toastOnUi(getString(R.string.role_tts_no_chapters))
            return
        }
        val checked = BooleanArray(chapters.size)
        val labels = chapters.map { "${it.index + 1}. ${it.title}" }.toTypedArray()
        val picker = AlertDialog.Builder(requireContext())
            .setTitle(R.string.role_tts_chapter_picker)
            .setMultiChoiceItems(labels, checked) { dialog, which, isChecked ->
                checked[which] = isChecked
                (dialog as? AlertDialog)?.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled = checked.any { it }
            }
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.role_tts_generate_selected) { _, _ ->
                val selected = chapters.filterIndexed { index, _ -> checked[index] }
                if (selected.isEmpty()) {
                    requireContext().toastOnUi(getString(R.string.role_tts_no_chapter_selected))
                } else {
                    beginGeneration(selected)
                }
            }
            .create()
        picker.setOnShowListener {
            picker.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled = false
        }
        picker.show()
        picker.applyTint()
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
        val dialog = createProgressDialog()
        progressDialog = dialog
        dialog.show()
        dialog.applyTint()
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
                            progressDetail?.text = getString(R.string.role_tts_failed_chapter, chapter.title)
                        }
                    }
                }
                withContext(Main) {
                    progressDialog?.dismiss()
                    progressDialog = null
                    requireContext().toastOnUi(getString(R.string.role_tts_generation_done, completed, failed))
                }
            } catch (_: CancellationException) {
                withContext(Main) {
                    progressDialog?.dismiss()
                    progressDialog = null
                    if (isAdded) requireContext().toastOnUi(getString(R.string.role_tts_cancelled))
                }
            } finally {
                generationJob = null
            }
        }
    }

    private fun createProgressDialog(): AlertDialog {
        val context = requireContext()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 8.dpToPx(), 24.dpToPx(), 8.dpToPx())
        }
        progressChapter = TextView(context).apply {
            textSize = 15f
            setTextColor(context.getColor(R.color.primaryText))
        }
        progressDetail = TextView(context).apply {
            textSize = 13f
            setTextColor(context.getColor(R.color.secondaryText))
            setPadding(0, 8.dpToPx(), 0, 4.dpToPx())
        }
        progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 0
            contentDescription = getString(R.string.role_tts_generating)
        }
        content.addView(progressChapter)
        content.addView(progressDetail)
        content.addView(progressBar)
        return AlertDialog.Builder(context)
            .setTitle(R.string.role_tts_generating)
            .setView(content)
            .setCancelable(true)
            .setPositiveButton(R.string.role_tts_cancel_generation, null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                        generationJob?.cancel()
                        progressDetail?.text = getString(R.string.role_tts_cancelled)
                        getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = false
                    }
                }
                setOnCancelListener { generationJob?.cancel() }
                setOnDismissListener {
                    if (generationJob?.isActive == true) generationJob?.cancel()
                    if (progressDialog === this) progressDialog = null
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
        if (!isAdded || progressDialog?.isShowing != true) return
        progressChapter?.text = getString(R.string.role_tts_progress_chapter, index + 1, count, chapter.title)
        progressDetail?.text = getString(R.string.role_tts_progress_chunks, completedChunks, totalChunks)
        val fraction = (index + completedChunks.toFloat() / totalChunks.coerceAtLeast(1)) / count.coerceAtLeast(1)
        progressBar?.progress = (fraction * progressBar!!.max).toInt().coerceIn(0, progressBar!!.max)
        val range = if (selected.size > 1) {
            "${selected.first().index + 1}–${selected.last().index + 1}"
        } else {
            "${selected.first().index + 1}"
        }
        progressDialog?.setMessage(range)
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
