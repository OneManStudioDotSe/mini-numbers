package se.onemanstudio.cli

import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import se.onemanstudio.config.ConfigLoader
import se.onemanstudio.core.MIN_PASSWORD_LENGTH
import se.onemanstudio.core.setPassword
import se.onemanstudio.db.DatabaseFactory
import se.onemanstudio.db.Users
import java.io.PrintStream

/**
 * `java -jar mini-numbers.jar reset-password <username>`: sets a new password for any user, for when it is forgotten
 * or has to be rotated. Only someone with a shell on the server can run it, which is the trust boundary.
 * Reads the configuration (.env or environment) like the server does, asks for the password twice (or reads one line
 * from standard input when there is no terminal) and signs that account out everywhere.
 */
object ResetPasswordCommand {
    const val NAME = "reset-password"

    /** @return the process exit code. */
    fun run(
        args: List<String>,
        out: PrintStream = System.out,
        readPassword: (username: String) -> String? = ::readNewPassword,
    ): Int {
        val username = args.firstOrNull()?.trim().orEmpty()
        if (username.isEmpty()) {
            out.println("Usage: $NAME <username>")
            return 2
        }
        val problem = changePassword(username, readPassword)
        if (problem != null) {
            out.println(problem)
            return 1
        }
        out.println("Password changed for $username, and every session of $username was signed out.")
        out.println("If the account was locked after wrong passwords, the lock lifts 15 minutes after the last attempt.")
        return 0
    }

    /** @return why nothing was changed, or null when the password was changed. */
    private fun changePassword(username: String, readPassword: (String) -> String?): String? {
        if (ConfigLoader.isSetupNeeded()) return "Mini Numbers is not configured yet: finish the setup wizard first."
        DatabaseFactory.init(ConfigLoader.load().database)

        val users = transaction { Users.selectAll().map { it[Users.username] } }
        if (username !in users) return "There is no user named '$username'. Users: ${users.joinToString()}"
        val password = readPassword(username)
            ?: return "No password was entered, or the two didn't match. Nothing was changed."
        if (password.length < MIN_PASSWORD_LENGTH) {
            return "The password must be at least $MIN_PASSWORD_LENGTH characters. Nothing was changed."
        }
        setPassword(username, password)
        return null
    }

    /** Hidden input, typed twice, on a terminal; one line of standard input otherwise (`echo … | …`). */
    private fun readNewPassword(username: String): String? {
        val console = System.console() ?: return readlnOrNull()
        val first = console.readPassword("New password for %s: ", username)?.let { String(it) }
        val second = console.readPassword("Repeat it: ")?.let { String(it) }
        return first.takeIf { it == second }
    }
}
