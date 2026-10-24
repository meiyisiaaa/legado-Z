package io.legado.app.api.controller


import android.text.TextUtils
import io.legado.app.api.ReturnData
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.help.source.BookSourceImport
import io.legado.app.help.source.SourceHelp
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext

object BookSourceController {

    val sources: ReturnData
        get() {
            val bookSources = appDb.bookSourceDao.all
            val returnData = ReturnData()
            return if (bookSources.isEmpty()) {
                returnData.setErrorMsg("设备源列表为空")
            } else returnData.setData(bookSources)
        }

    suspend fun saveSource(postData: String?): ReturnData {
        val returnData = ReturnData()
        postData ?: return returnData.setErrorMsg("数据不能为空")
        val bookSource = try {
            BookSourceImport.parse(postData, currentCoroutineContext())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return returnData.setErrorMsg(e.localizedMessage ?: "转换源失败")
        }
        if (TextUtils.isEmpty(bookSource.bookSourceName) || TextUtils.isEmpty(bookSource.bookSourceUrl)) {
            returnData.setErrorMsg("源名称和URL不能为空")
        } else {
            appDb.bookSourceDao.insert(bookSource)
            returnData.setData("")
        }
        return returnData
    }

    suspend fun parseBookSource(postData: String?): ReturnData {
        if (postData.isNullOrBlank()) return ReturnData().setErrorMsg("数据不能为空")
        val text = try {
            GSON.fromJsonObject<Map<String, String>>(postData).getOrThrow()["text"]
        } catch (e: Exception) {
            return ReturnData().setErrorMsg(e.localizedMessage ?: "书源内容格式错误")
        }
        if (text.isNullOrBlank()) return ReturnData().setErrorMsg("书源内容不能为空")

        val bookSource = try {
            BookSourceImport.parse(text, currentCoroutineContext())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ReturnData().setErrorMsg(e.localizedMessage ?: "转换源失败")
        }
        if (bookSource.bookSourceName.isBlank() || bookSource.bookSourceUrl.isBlank()) {
            return ReturnData().setErrorMsg("源名称和URL不能为空")
        }
        return ReturnData().setData(bookSource)
    }

    suspend fun saveSources(postData: String?): ReturnData {
        postData ?: return ReturnData().setErrorMsg("数据为空")
        val bookSources = GSON.fromJsonArray<BookSource>(postData).getOrNull()
        if (bookSources.isNullOrEmpty()) {
            return ReturnData().setErrorMsg("转换源失败")
        }
        val normalizedSources = try {
            val coroutineContext = currentCoroutineContext()
            val canonicalized = ArrayList<BookSource>(bookSources.size)
            for (bookSource in bookSources) {
                canonicalized.add(BookSourceImport.canonicalize(bookSource, coroutineContext))
            }
            canonicalized
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ReturnData().setErrorMsg(e.localizedMessage ?: "转换源失败")
        }
        val okSources = normalizedSources.filter { bookSource ->
            if (bookSource.bookSourceName.isNotBlank()
                && bookSource.bookSourceUrl.isNotBlank()
            ) {
                true
            } else {
                false
            }
        }
        appDb.bookSourceDao.insert(*okSources.toTypedArray())
        return ReturnData().setData(okSources)
    }

    fun getSource(parameters: Map<String, List<String>>): ReturnData {
        val url = parameters["url"]?.firstOrNull()
        val returnData = ReturnData()
        if (url.isNullOrEmpty()) {
            return returnData.setErrorMsg("参数url不能为空，请指定源地址")
        }
        val bookSource = appDb.bookSourceDao.getBookSource(url)
            ?: return returnData.setErrorMsg("未找到源，请检查书源地址")
        return returnData.setData(bookSource)
    }

    fun deleteSources(postData: String?): ReturnData {
        kotlin.runCatching {
            GSON.fromJsonArray<BookSource>(postData).getOrThrow().let {
                SourceHelp.deleteBookSources(it)
            }
        }.onFailure {
            return ReturnData().setErrorMsg(it.localizedMessage ?: "数据格式错误")
        }
        return ReturnData().setData("已执行"/*okSources*/)
    }
}
