package se.onemanstudio.api.models

import kotlinx.serialization.Serializable

/**
 * Generic paginated response wrapper for list endpoints
 */
@Serializable
data class PaginatedResponse<T>(
    val data: List<T>,
    val total: Long,
    val page: Int,
    val limit: Int,
    val totalPages: Int
)
