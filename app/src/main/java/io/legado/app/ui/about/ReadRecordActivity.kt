package io.legado.app.ui.about

import android.os.Bundle
import androidx.appcompat.widget.SearchView
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.data.appDb
import io.legado.app.data.entities.ReadRecordShow
import io.legado.app.databinding.ActivityReadRecordBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.compose.LocalLegadoMotion
import io.legado.app.ui.widget.compose.legadoPressFeedback
import io.legado.app.utils.applyTint
import io.legado.app.utils.cnCompare
import io.legado.app.utils.getInt
import io.legado.app.utils.putInt
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class ReadRecordActivity : BaseActivity<ActivityReadRecordBinding>() {

    private var readRecords by mutableStateOf<List<ReadRecordShow>>(emptyList())
    private var totalReadingTime by mutableStateOf("0秒")
    private var isLoading by mutableStateOf(true)
    private var loadError by mutableStateOf(false)
    private var readRecordJob: Job? = null
    private var totalReadingTimeLoaded = false
    private var sortMode
        get() = LocalConfig.getInt("readRecordSort")
        set(value) {
            LocalConfig.putInt("readRecordSort", value)
        }
    private val searchView: SearchView by lazy {
        binding.titleBar.findViewById(R.id.search_view)
    }

    override val binding by viewBinding(ActivityReadRecordBinding::inflate)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initView()
        initData(refreshTotalTime = true)
    }

    override fun onCompatCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.book_read_record, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: android.view.Menu): Boolean {
        menu.findItem(R.id.menu_enable_record)?.isChecked = AppConfig.enableReadRecord
        when (sortMode) {
            1 -> menu.findItem(R.id.menu_sort_read_long)?.isChecked = true
            2 -> menu.findItem(R.id.menu_sort_read_time)?.isChecked = true
            else -> menu.findItem(R.id.menu_sort_name)?.isChecked = true
        }
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatOptionsItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_sort_name -> {
                sortMode = 0
                item.isChecked = true
                initData(searchView.query?.toString())
            }

            R.id.menu_sort_read_long -> {
                sortMode = 1
                item.isChecked = true
                initData(searchView.query?.toString())
            }

            R.id.menu_sort_read_time -> {
                sortMode = 2
                item.isChecked = true
                initData(searchView.query?.toString())
            }

            R.id.menu_enable_record -> {
                AppConfig.enableReadRecord = !item.isChecked
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initView() {
        initSearchView()
        binding.composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        binding.composeView.setContent {
            LegadoTheme {
                ReadRecordContent(
                    records = readRecords,
                    totalReadingTime = totalReadingTime,
                    loading = isLoading,
                    loadError = loadError,
                    onRetry = { initData(searchView.query?.toString(), refreshTotalTime = true) },
                    onClearAll = ::confirmClearAll,
                    onOpen = ::openRecord,
                    onDelete = ::confirmDelete
                )
            }
        }
    }

    private fun initSearchView() {
        searchView.applyTint(primaryTextColor)
        searchView.isSubmitButtonEnabled = true
        searchView.queryHint = getString(R.string.search)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                searchView.clearFocus()
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                initData(newText)
                return false
            }
        })
    }

    private fun initData(searchKey: String? = null, refreshTotalTime: Boolean = false) {
        readRecordJob?.cancel()
        if (refreshTotalTime) totalReadingTimeLoaded = false
        val loadTotalTime = !totalReadingTimeLoaded
        isLoading = true
        loadError = false
        val requestedSortMode = sortMode
        readRecordJob = lifecycleScope.launch {
            try {
                val (records, allTime) = withContext(IO) {
                    val dao = appDb.readRecordDao
                    val records = dao.search(searchKey.orEmpty()).let { rows ->
                        when (requestedSortMode) {
                            1 -> rows.sortedByDescending { it.readTime }
                            2 -> rows.sortedByDescending { it.lastRead }
                            else -> rows.sortedWith { left, right ->
                                left.bookName.cnCompare(right.bookName)
                            }
                        }
                    }
                    records to if (loadTotalTime) dao.allTime else null
                }
                if (coroutineContext.isActive) {
                    readRecords = records
                    allTime?.let {
                        totalReadingTime = formatDuring(it)
                        totalReadingTimeLoaded = true
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (coroutineContext.isActive) loadError = true
            } finally {
                if (coroutineContext.isActive) isLoading = false
            }
        }
    }

    private fun openRecord(record: ReadRecordShow) {
        lifecycleScope.launch {
            val book = withContext(IO) {
                appDb.bookDao.findByName(record.bookName).firstOrNull()
            }
            if (book == null) {
                SearchActivity.start(this@ReadRecordActivity, record.bookName)
            } else {
                startActivityForBook(book)
            }
        }
    }

    private fun confirmClearAll() {
        alert(R.string.delete, R.string.sure_del) {
            yesButton {
                appDb.readRecordDao.clear()
                initData(refreshTotalTime = true)
            }
            noButton()
        }
    }

    private fun confirmDelete(record: ReadRecordShow) {
        alert(R.string.delete) {
            setMessage(getString(R.string.sure_del_any, record.bookName))
            yesButton {
                appDb.readRecordDao.deleteByName(record.bookName)
                initData(searchView.query?.toString(), refreshTotalTime = true)
            }
            noButton()
        }
    }

    private fun formatDuring(mss: Long): String {
        val days = mss / (1000 * 60 * 60 * 24)
        val hours = mss % (1000 * 60 * 60 * 24) / (1000 * 60 * 60)
        val minutes = mss % (1000 * 60 * 60) / (1000 * 60)
        val seconds = mss % (1000 * 60) / 1000
        val d = if (days > 0) "${days}天" else ""
        val h = if (hours > 0) "${hours}小时" else ""
        val m = if (minutes > 0) "${minutes}分钟" else ""
        val s = if (seconds > 0) "${seconds}秒" else ""
        return "$d$h$m$s".ifBlank { "0秒" }
    }
}

@Composable
internal fun ReadRecordContent(
    records: List<ReadRecordShow>,
    totalReadingTime: String,
    loading: Boolean,
    loadError: Boolean,
    onRetry: () -> Unit,
    onClearAll: () -> Unit,
    onOpen: (ReadRecordShow) -> Unit,
    onDelete: (ReadRecordShow) -> Unit
) {
    val motion = LocalLegadoMotion.current
    val loadingDescription = stringResource(R.string.loading)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.all_read_time),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = totalReadingTime,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TextButton(
                    onClick = onClearAll,
                    enabled = !loading && records.isNotEmpty()
                ) {
                    Text(stringResource(R.string.clear))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            when {
                loading -> Column(Modifier.fillMaxSize()) {
                    if (motion.enabled) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(loadingDescription,
                            modifier = Modifier.padding(32.dp).semantics { contentDescription = loadingDescription },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                loadError -> Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.error_get_data),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.retry))
                    }
                }
                records.isEmpty() -> Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.read_record_empty),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)
                ) {
                    items(records, key = { it.bookName }) { record ->
                        ReadRecordRow(record, onOpen, onDelete,
                            modifier = Modifier.animateItem(fadeInSpec = tween(motion.state),
                                fadeOutSpec = tween(motion.state), placementSpec = tween(motion.state)))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadRecordRow(
    record: ReadRecordShow,
    onOpen: (ReadRecordShow) -> Unit,
    onDelete: (ReadRecordShow) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactions = remember { MutableInteractionSource() }
    val clearLabel = stringResource(R.string.clear)
    val date = if (record.lastRead > 0) {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(record.lastRead)
    } else {
        ""
    }
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .legadoPressFeedback(interactions)
                .clickable(interactionSource = interactions, indication = LocalIndication.current,
                    role = Role.Button) { onOpen(record) }
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = record.bookName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.reading_time_tag) + " ${formatReadTime(record.readTime)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (date.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.last_read_time_tag) + " $date",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(
                onClick = { onDelete(record) },
                modifier = Modifier.heightIn(min = 48.dp)
                    .semantics { contentDescription = "$clearLabel ${record.bookName}" }
            ) {
                Text(clearLabel)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private fun formatReadTime(mss: Long): String {
    val days = mss / (1000 * 60 * 60 * 24)
    val hours = mss % (1000 * 60 * 60 * 24) / (1000 * 60 * 60)
    val minutes = mss % (1000 * 60 * 60) / (1000 * 60)
    val seconds = mss % (1000 * 60) / 1000
    val d = if (days > 0) "${days}天" else ""
    val h = if (hours > 0) "${hours}小时" else ""
    val m = if (minutes > 0) "${minutes}分钟" else ""
    val s = if (seconds > 0) "${seconds}秒" else ""
    return "$d$h$m$s".ifBlank { "0秒" }
}
