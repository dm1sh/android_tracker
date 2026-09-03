package dm1sh.android_tracker.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin, server-URL-agnostic wrapper around the Ktor HttpClient.
 * The base URL is supplied per-call so that the configured server
 * URL can change at runtime without rebuilding the client.
 */
@Singleton
class TrackerApi @Inject constructor(
    private val client: HttpClient
) {

    suspend fun pushUsageEvents(baseUrl: String, request: UsageBatchRequest): BatchResponse {
        val response = client.post("$baseUrl/api/v1/usage-events/batch") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return response.body()
    }

    suspend fun pushDeviceMetrics(baseUrl: String, request: MetricsBatchRequest): BatchResponse {
        val response = client.post("$baseUrl/api/v1/device-metrics/batch") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return response.body()
    }

    suspend fun health(baseUrl: String): HealthResponse {
        return client.get("$baseUrl/api/v1/health").body()
    }
}
