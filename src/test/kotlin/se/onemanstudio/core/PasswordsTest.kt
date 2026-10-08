package se.onemanstudio.core

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasswordsTest {

    @Test
    fun `new hashes are 2a, cost 12, and verify`() {
        val hash = Passwords.hash("correct horse battery staple")
        assertTrue(hash.startsWith("\$2a\$12\$"), hash)
        assertTrue(Passwords.verify("correct horse battery staple", hash))
        assertFalse(Passwords.verify("correct horse battery stapler", hash))
    }

    @Test
    fun `hashes produced by the previous library (jBCrypt, 2a) still verify`() {
        val legacy = "\$2a\$10\$Ju8SUf4cckwLm1qy08D53eC.bspEBge.3TNvr9ctTVdNTupUZ5qSy"
        assertTrue(Passwords.verify("Xss-check-123", legacy))
        assertFalse(Passwords.verify("wrong", legacy))
    }

    @Test
    fun `htpasswd-style 2y hashes verify now`() {
        val htpasswd = "\$2y\$05\$LM2IGDuX.TiQ0vVKDDKevOkk7BmqTpO3fQT20wBoaBrxEIpCUdk4a"
        assertTrue(Passwords.verify("Xss-check-123", htpasswd))
        assertFalse(Passwords.verify("not-it", htpasswd))
    }

    @Test
    fun `plain text is never treated as a hash and never verifies as one`() {
        assertFalse(Passwords.isBcryptHash("hunter2"))
        assertFalse(Passwords.isBcryptHash("\$2a\$notreally"))
        assertTrue(Passwords.isBcryptHash("\$2b\$12\$abcdefghijklmnopqrstuu"))
        assertFalse(Passwords.verify("hunter2", "hunter2"))
    }
}
