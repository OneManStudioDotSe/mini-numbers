package se.onemanstudio.setup

import se.onemanstudio.config.ConfigLoader
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * One-time token that proves whoever submits the setup wizard can read the server's log (or its
 * environment). Without it, the first stranger to reach `/setup` on a fresh install would choose
 * the admin password. Taken from `SETUP_TOKEN` when set, otherwise generated at every start and
 * printed in the log.
 */
object SetupToken {
    private const val TOKEN_BYTES = 16

    val value: String by lazy {
        ConfigLoader.setupToken() ?: ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
    }

    fun matches(given: String?): Boolean =
        given != null && MessageDigest.isEqual(given.trim().toByteArray(), value.toByteArray())
}

/**
 * Why `POST /setup/api/save` must be refused, or null when it may go ahead. The wizard only ever
 * creates the first configuration: once one exists it is closed, also when services failed to start
 * with it (otherwise a failed boot would let anyone replace the admin password and the server salt).
 */
internal fun setupSaveRefusal(configExists: Boolean, tokenMatches: Boolean): String? = when {
    configExists -> "Setup already completed. Use server access for config changes."
    !tokenMatches -> "Wrong or missing setup token. It is printed in the server log at startup."
    else -> null
}
