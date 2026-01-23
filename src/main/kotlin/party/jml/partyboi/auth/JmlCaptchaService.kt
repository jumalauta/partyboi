package party.jml.partyboi.auth

import arrow.core.left
import arrow.core.right
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.data.BotDetected
import party.jml.partyboi.system.AppResult

class JmlCaptchaService(app: AppServices) : Service(app) {
    fun verify(user: UserCredentials): AppResult<Unit> {
        if (getScore(user) >= 0.5) {
            log.info("jmlCaptcha passed: ${user.name}")
            return Unit.right()
        } else {
            log.warn("jmlCaptcha failed: ${user.name}")
            return BotDetected("You failed jmlCAPTCHA").left()
        }
    }

    fun getScore(user: UserCredentials): Double =
        calculateScore(
            mapOf(
                isBotLikeName(user.name) to 1.0,
            )
        )


    fun getScore(user: User): Double =
        calculateScore(
            mapOf(
                isBotLikeName(user.name) to 1.0,
            )
        )

    private fun calculateScore(suspicions: Map<Boolean, Double>): Double =
        1.0 - (suspicions.filterKeys { it }.values.sum() / suspicions.values.sum())

    private fun isBotLikeName(name: String): Boolean =
        name.length >= 16 &&
                name.matches(Regex("^[a-zA-Z]+$")) &&
                name.lowercase() != name
}