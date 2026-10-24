package io.legado.app.help.tts

import io.legado.app.ai.tts.BookTtsRole
import io.legado.app.ai.tts.RoleTtsRolesConfig
import io.legado.app.ai.tts.RoleTtsRoleRegistry
import io.legado.app.ai.tts.RoleTtsVoiceBinding
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.model.ReadBook
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject

object RoleTtsConfigStore {
    fun load(book: Book): RoleTtsRolesConfig =
        GSON.fromJsonObject<RoleTtsRolesConfig>(book.config.roleTtsConfigJson).getOrNull()
            ?: RoleTtsRolesConfig()

    fun save(book: Book, config: RoleTtsRolesConfig) {
        val json = GSON.toJson(config)
        appDb.bookDao.updateRoleTtsConfig(book.bookUrl, json)
        book.config.roleTtsConfigJson = json
        ReadBook.book?.takeIf { it.bookUrl == book.bookUrl }?.config?.roleTtsConfigJson = json
    }

    fun setEnabled(book: Book, enabled: Boolean) {
        appDb.bookDao.updateRoleTtsEnabled(book.bookUrl, enabled)
        book.config.roleTtsEnabled = enabled
        ReadBook.book?.takeIf { it.bookUrl == book.bookUrl }?.config?.roleTtsEnabled = enabled
    }
}

/** Binding resolution never mutates persisted config; stale references fall back to narrator. */
object RoleTtsRouter {
    fun bindingFor(
        config: RoleTtsRolesConfig,
        roleId: String,
        validSystemVoices: Set<String>,
        validHttpTtsIds: Set<Long>,
        narratorBinding: RoleTtsVoiceBinding?
    ): RoleTtsVoiceBinding? {
        val role = RoleTtsRoleRegistry.find(config, roleId) ?: return narratorBinding
        val binding = role.binding ?: return narratorBinding
        val valid = when (binding.provider) {
            RoleTtsVoiceBinding.Provider.SYSTEM ->
                !binding.engineId.isNullOrBlank() && !binding.voiceName.isNullOrBlank() &&
                    "${binding.engineId}\u0000${binding.voiceName}" in validSystemVoices

            RoleTtsVoiceBinding.Provider.HTTP -> binding.httpTtsId?.let(validHttpTtsIds::contains) == true
        }
        return if (valid) binding else narratorBinding
    }
}
