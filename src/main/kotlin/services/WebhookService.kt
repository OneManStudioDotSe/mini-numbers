package se.onemanstudio.services

import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import se.onemanstudio.db.WebhookDeliveries
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.Executors
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Delivers webhook payloads with HMAC-SHA256 signatures.
 * Async delivery with automatic retry (3 attempts, exponential backoff).
 */
object WebhookService {
    private val logger = LoggerFactory.getLogger(WebhookService::class.java)
    private val executor = Executors.newFixedThreadPool(2)
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    /**
     * Sign a payload using HMAC-SHA256.
     */
    fun signPayload(payload: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(payload.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /**
     * Queue a webhook delivery for async processing.
     */
    fun deliverAsync(webhookId: UUID, url: String, secret: String, eventType: String, payload: String) {
        executor.submit {
            deliver(webhookId, url, secret, eventType, payload, attempt = 1)
        }
    }

    private fun deliver(webhookId: UUID, url: String, secret: String, eventType: String, payload: String, attempt: Int) {
        val signature = signPayload(payload, secret)

        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("X-MiniNumbers-Signature", "sha256=$signature")
                .header("X-MiniNumbers-Event", eventType)
                .header("User-Agent", "MiniNumbers-Webhook/1.0")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            val success = response.statusCode() in 200..299

            recordDelivery(
                webhookId = webhookId,
                eventType = eventType,
                payload = payload,
                responseCode = response.statusCode(),
                responseBody = response.body()?.take(1000), // Truncate large responses
                attempt = attempt,
                status = if (success) "success" else "failed"
            )

            // Retry on failure (max 3 attempts, exponential backoff)
            if (!success && attempt < 3) {
                logger.warn("Webhook delivery failed (attempt $attempt/3, status ${response.statusCode()}), retrying...")
                Thread.sleep(attempt * 5000L)
                deliver(webhookId, url, secret, eventType, payload, attempt + 1)
            }
        } catch (e: Exception) {
            logger.error("Webhook delivery error (attempt $attempt/3): ${e.message}")
            recordDelivery(
                webhookId = webhookId,
                eventType = eventType,
                payload = payload,
                responseCode = null,
                responseBody = e.message?.take(500),
                attempt = attempt,
                status = "failed"
            )

            if (attempt < 3) {
                Thread.sleep(attempt * 5000L)
                deliver(webhookId, url, secret, eventType, payload, attempt + 1)
            }
        }
    }

    private fun recordDelivery(
        webhookId: UUID,
        eventType: String,
        payload: String,
        responseCode: Int?,
        responseBody: String?,
        attempt: Int,
        status: String
    ) {
        try {
            transaction {
                WebhookDeliveries.insert {
                    it[WebhookDeliveries.id] = UUID.randomUUID()
                    it[WebhookDeliveries.webhookId] = webhookId
                    it[WebhookDeliveries.eventType] = eventType
                    it[WebhookDeliveries.payload] = payload
                    it[WebhookDeliveries.responseCode] = responseCode
                    it[WebhookDeliveries.responseBody] = responseBody
                    it[WebhookDeliveries.attempt] = attempt
                    it[WebhookDeliveries.status] = status
                    if (status == "success") {
                        it[WebhookDeliveries.deliveredAt] = LocalDateTime.now()
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to record webhook delivery: ${e.message}")
        }
    }
}
