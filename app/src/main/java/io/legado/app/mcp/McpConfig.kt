package io.legado.app.mcp

import android.util.Base64
import android.content.Context
import io.legado.app.help.crypto.KeystoreSecret
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.putPrefInt
import splitties.init.appCtx
import java.security.SecureRandom

object McpConfig {
    const val TOKEN_KEY = "mcpBearerToken"
    private const val ALIAS = "legado_mcp_token"
    private val credentials get() = appCtx.getSharedPreferences("mcp_credentials", Context.MODE_PRIVATE)
    var port: Int
        get() = appCtx.getPrefInt("mcpPort", 1236)
        set(value) { require(value in 1024..65535); appCtx.putPrefInt("mcpPort", value) }

    @Synchronized fun token(): String {
        val stored = credentials.getString(TOKEN_KEY, null)
        return if (stored.isNullOrEmpty()) regenerateToken()
        else KeystoreSecret.decrypt(stored, ALIAS)
    }

    @Synchronized fun regenerateToken(): String {
        val value = Base64.encodeToString(ByteArray(32).also(SecureRandom()::nextBytes),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val encrypted = KeystoreSecret.encrypt(value, ALIAS)
        check(credentials.edit().putString(TOKEN_KEY, encrypted).commit()) { "Unable to save MCP credential" }
        return value
    }
}
