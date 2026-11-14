package se.onemanstudio.middleware

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.io.readByteArray
import se.onemanstudio.api.models.ApiError

/** Largest request body accepted anywhere. Every body this server reads is a small JSON document. */
const val MAX_REQUEST_BODY_BYTES = 64 * 1024L

/** Thrown while reading a body that turns out to be larger than [MAX_REQUEST_BODY_BYTES]. */
class PayloadTooLargeException : RuntimeException("Request body too large")

/**
 * Refuse oversized request bodies before they are read into memory. Without this, a handful of
 * large posts to any endpoint (no login needed) exhausts the heap and hangs the server.
 *
 * A declared Content-Length over the limit is answered with 413 at once; a body without one
 * (chunked) is read up to the limit and rejected when it goes over.
 */
fun Application.configureBodyLimit() {
    intercept(ApplicationCallPipeline.Plugins) {
        val declared = call.request.contentLength()
        if (declared != null && declared > MAX_REQUEST_BODY_BYTES) {
            call.respond(HttpStatusCode.PayloadTooLarge, payloadTooLarge())
            finish()
        }
    }
    receivePipeline.intercept(ApplicationReceivePipeline.Before) { body ->
        if (body is ByteReadChannel) {
            val packet = body.readRemaining(MAX_REQUEST_BODY_BYTES + 1)
            if (packet.remaining > MAX_REQUEST_BODY_BYTES) {
                packet.close()
                body.cancel(null)
                throw PayloadTooLargeException()
            }
            proceedWith(ByteReadChannel(packet.readByteArray()))
        }
    }
}

fun payloadTooLarge() = ApiError(
    error = "Request body too large (limit: ${MAX_REQUEST_BODY_BYTES / 1024} KB)",
    code = "PAYLOAD_TOO_LARGE"
)
