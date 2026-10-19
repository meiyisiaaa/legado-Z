package io.legado.app.ai.tts

/** Pure book-local identity operations; callers persist the resulting config atomically. */
object RoleTtsRoleRegistry {

    fun renameOrMerge(config: RoleTtsRolesConfig, roleId: String, requestedName: String): RoleTtsRolesConfig {
        val name = requestedName.trim()
        require(name.isNotEmpty()) { "角色名不能为空" }
        if (config.roles.none { it.id == roleId }) return config
        require(config.roles.none { role ->
            role.id != roleId && role.aliases.any { normalize(it) == normalize(name) }
        }) { "角色名称或别名已存在" }
        val target = config.roles.firstOrNull {
            it.id != roleId && normalize(it.name) == normalize(name)
        }
        val roles = config.roles.filterNot { target != null && it.id == target.id }.map { role ->
            if (role.id != roleId) role else if (target == null) {
                role.copy(name = name, aliases = uniqueNames(role.aliases + role.name, name))
            } else {
                role.copy(
                    name = name,
                    aliases = uniqueNames(
                        role.aliases + role.name + target.name + target.aliases,
                        name
                    ),
                    binding = role.binding ?: target.binding
                )
            }
        }
        val merged = config.roleIdAliases.toMutableMap()
        if (target != null) {
            merged[target.id] = roleId
            merged.replaceAll { _, survivor -> resolveId(merged, survivor) }
        }
        return config.copy(roles = roles, roleIdAliases = merged)
    }

    fun addAlias(config: RoleTtsRolesConfig, roleId: String, requestedAlias: String): RoleTtsRolesConfig {
        val alias = requestedAlias.trim()
        require(alias.isNotEmpty()) { "别名不能为空" }
        require(config.roles.none { role ->
            role.id != roleId && (normalize(role.name) == normalize(alias) || role.aliases.any { normalize(it) == normalize(alias) })
        }) { "别名已属于其他角色" }
        return config.copy(roles = config.roles.map { role ->
            if (role.id != roleId || normalize(role.name) == normalize(alias) || role.aliases.any { normalize(it) == normalize(alias) }) {
                role
            } else {
                role.copy(aliases = uniqueNames(role.aliases + alias, role.name))
            }
        })
    }

    fun create(name: String, newId: String, config: RoleTtsRolesConfig): RoleTtsRolesConfig {
        val normalized = normalize(name)
        require(normalized.isNotEmpty()) { "角色名不能为空" }
        require(config.roles.none { normalize(it.name) == normalized || it.aliases.any { alias -> normalize(alias) == normalized } }) {
            "角色名称或别名已存在"
        }
        require(config.roles.none { it.id == newId }) { "角色 ID 已存在" }
        return config.copy(roles = config.roles + BookTtsRole(id = newId, name = name.trim()))
    }

    fun resolveId(config: RoleTtsRolesConfig, id: String): String = resolveId(config.roleIdAliases, id)

    fun find(config: RoleTtsRolesConfig, id: String): BookTtsRole? {
        val resolved = resolveId(config, id)
        return config.roles.firstOrNull { it.id == resolved }
    }

    fun findByNameOrAlias(config: RoleTtsRolesConfig, nameOrAlias: String): BookTtsRole? {
        val normalized = normalize(nameOrAlias)
        if (normalized.isEmpty()) return null
        return config.roles.firstOrNull { role ->
            normalize(role.name) == normalized || role.aliases.any { normalize(it) == normalized }
        }
    }

    private fun resolveId(aliases: Map<String, String>, id: String): String {
        var current = id
        val visited = HashSet<String>()
        while (visited.add(current)) current = aliases[current] ?: return current
        return id
    }

    private fun uniqueNames(values: List<String>, keepOut: String): List<String> {
        val seen = hashSetOf(normalize(keepOut))
        return values.map(String::trim).filter { it.isNotEmpty() && seen.add(normalize(it)) }
    }

    private fun normalize(value: String): String = value.trim().lowercase()
}
