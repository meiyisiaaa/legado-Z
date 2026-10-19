package io.legado.app.ai.tts

import io.legado.app.help.tts.RoleTtsRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RoleTtsRoleRegistryTest {

    @Test
    fun `renaming to an existing role merges aliases and keeps an id redirect`() {
        val sourceBinding = RoleTtsVoiceBinding(
            RoleTtsVoiceBinding.Provider.HTTP,
            httpTtsId = 42L
        )
        val original = RoleTtsRolesConfig(
            roles = listOf(
                BookTtsRole("old-id", "旧名", listOf("旧称"), sourceBinding),
                BookTtsRole("target-id", "新名", listOf("别称"))
            )
        )

        val merged = RoleTtsRoleRegistry.renameOrMerge(original, "old-id", "新名")

        assertEquals(listOf("old-id"), merged.roles.map { it.id })
        assertEquals("新名", merged.roles.single().name)
        assertEquals(setOf("旧名", "旧称", "别称"), merged.roles.single().aliases.toSet())
        assertEquals(sourceBinding, merged.roles.single().binding)
        assertEquals("old-id", RoleTtsRoleRegistry.resolveId(merged, "target-id"))
        assertEquals("old-id", RoleTtsRoleRegistry.find(merged, "target-id")?.id)
    }

    @Test
    fun `renamed narrator remains discoverable by its canonical alias and keeps its voice binding`() {
        val narratorBinding = RoleTtsVoiceBinding(
            RoleTtsVoiceBinding.Provider.HTTP,
            httpTtsId = 42L
        )
        val config = RoleTtsRolesConfig(
            roles = listOf(BookTtsRole("narrator-id", "旁白", binding = narratorBinding))
        )

        val renamed = RoleTtsRoleRegistry.renameOrMerge(config, "narrator-id", "讲述者")
        val narrator = RoleTtsRoleRegistry.findByNameOrAlias(renamed, "旁白")

        assertEquals("narrator-id", narrator?.id)
        assertEquals("讲述者", narrator?.name)
        assertEquals(narratorBinding, narrator?.binding)
        assertEquals(narratorBinding, RoleTtsRouter.bindingFor(
            renamed,
            narrator?.id.orEmpty(),
            validSystemVoices = emptySet(),
            validHttpTtsIds = setOf(42L),
            narratorBinding = null
        ))
    }

    @Test
    fun `stale voice binding falls back without changing the stored role`() {
        val stale = RoleTtsVoiceBinding(
            provider = RoleTtsVoiceBinding.Provider.SYSTEM,
            engineId = "missing.engine",
            voiceName = "missing"
        )
        val narrator = RoleTtsVoiceBinding(RoleTtsVoiceBinding.Provider.SYSTEM, "engine", "voice")
        val config = RoleTtsRolesConfig(listOf(BookTtsRole("hero", "主角", binding = stale)))

        val resolved = RoleTtsRouter.bindingFor(
            config,
            "hero",
            validSystemVoices = setOf("engine\u0000voice"),
            validHttpTtsIds = emptySet(),
            narratorBinding = narrator
        )

        assertEquals(narrator, resolved)
        assertEquals(stale, config.roles.single().binding)
        assertNull(RoleTtsRouter.bindingFor(config, "unknown", emptySet(), emptySet(), null))
    }

    @Test
    fun `renaming to another role alias is rejected`() {
        val config = RoleTtsRolesConfig(
            roles = listOf(
                BookTtsRole("one", "主角"),
                BookTtsRole("two", "同伴", aliases = listOf("伙伴"))
            )
        )

        val result = runCatching { RoleTtsRoleRegistry.renameOrMerge(config, "one", "伙伴") }

        assertFalse(result.isSuccess)
    }
}
