
import org.mindrot.jbcrypt.BCrypt

fun main() {
    val password = "admin123456"
    val hash = "$2a$12$tiOZgMonlxF8Ubz7CXf./.OMBklOec6JIhT882KpaZpXT4poB6dwS"
    
    val matches = try {
        BCrypt.checkpw(password, hash)
    } catch (e: Exception) {
        println("Error: ${e.message}")
        false
    }
    
    println("Matches: $matches")
}
