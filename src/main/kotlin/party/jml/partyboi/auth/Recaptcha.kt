package party.jml.partyboi.auth

import arrow.core.left
import arrow.core.right
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.html.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import party.jml.partyboi.AppServices
import party.jml.partyboi.RecaptchaConfig
import party.jml.partyboi.Service
import party.jml.partyboi.data.BotDetected
import party.jml.partyboi.system.AppResult

class RecaptchaService(app: AppServices) : Service(app) {
    private val client = HttpClient(CIO) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                }
            )
        }
    }

    suspend fun verify(action: String, token: String?): AppResult<Unit> {
        val config = app.config.recaptcha

        if (config == null) {
            if (token == null) {
                return Unit.right()
            }
            throw IllegalStateException("No recaptcha config")
        }

        if (token == null) {
            throw IllegalStateException("Recaptcha token can't be null")
        }

        log.info("Verify recaptcha response: $token")
        val response =
            client.post("https://recaptchaenterprise.googleapis.com/v1/projects/${config.projectId}/assessments?key=${config.apiKey}") {
                contentType(ContentType.Application.Json)
                setBody(
                    RecaptchaRequest(
                        RecaptchaEvent(
                            token = token,
                            expectedAction = action,
                            siteKey = config.siteKey,
                        )
                    )
                )
            }
        val responseBody: RecaptchaResponse = response.body()
        log.info("Recaptcha for '$action': $responseBody")

        return if (responseBody.riskAnalysis.score >= 0.5) {
            log.info("Recaptcha passed")
            Unit.right()
        } else {
            log.warn("Recaptcha failed")
            BotDetected("You failed reCAPTCHA").left()
        }
    }
}

@Serializable
data class RecaptchaRequest(
    val event: RecaptchaEvent
)

@Serializable
data class RecaptchaEvent(
    val token: String,
    val expectedAction: String,
    val siteKey: String,
)

@Serializable
data class RecaptchaResponse(
    val riskAnalysis: RiskAnalysis
)

@Serializable
data class RiskAnalysis(
    val score: Double,
    val reasons: List<String>
)

fun FlowContent.recaptchaSubmitButton(formId: String, label: String, config: RecaptchaConfig) {
    val callbackName = "onSubmit_$formId"
    script(src = "https://www.google.com/recaptcha/api.js") {
        attributes["async"] = "async"
        attributes["defer"] = "defer"
    }
    script {
        unsafe { raw("function $callbackName(token) { document.getElementById(\"$formId\").submit(); }") }
    }
    footer {
        button(classes = "g-recaptcha submit") {
            attributes["data-sitekey"] = config.siteKey
            attributes["data-callback"] = callbackName
            attributes["data-action"] = formId
            attributes["role"] = "submit"
            +label
        }
    }
}