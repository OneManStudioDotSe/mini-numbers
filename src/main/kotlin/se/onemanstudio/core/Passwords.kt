package se.onemanstudio.core

import com.password4j.BcryptFunction
import com.password4j.Password
import com.password4j.types.Bcrypt

/**
 * Password hashing behind one door (password4j, Apache-2.0).
 *
 * New hashes are BCrypt `$2a$` with cost 12, byte-compatible with the jBCrypt hashes
 * already in databases and `.env` files. Verification accepts the `$2a$`, `$2b$` and
 * `$2y$` variants, so hashes produced by Apache `htpasswd -B` work too.
 */
object Passwords {
    private const val COST = 12
    private val bcrypt = BcryptFunction.getInstance(Bcrypt.A, COST)
    private val bcryptShape = Regex("^\\$2[abxy]\\$\\d{2}\\$")

    fun hash(plain: String): String = Password.hash(plain).with(bcrypt).result

    /**
     * Cost and variant are read from the hash itself (`getInstanceFromHash`), so hashes of any
     * cost or variant verify, not only the ones this object produces. `withBcrypt()` would use
     * the library defaults instead and reject everything else.
     */
    fun verify(plain: String, hashed: String): Boolean =
        isBcryptHash(hashed) && Password.check(plain, hashed).with(BcryptFunction.getInstanceFromHash(hashed))

    /** True when the string looks like a BCrypt hash rather than a plain-text password. */
    fun isBcryptHash(value: String): Boolean = bcryptShape.containsMatchIn(value)
}
