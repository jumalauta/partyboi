package party.jml.partyboi.email

import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.br
import kotlinx.html.p
import party.jml.partyboi.auth.User

object EmailTemplates {
    fun emailVerification(recipient: User, verificationCode: String, instanceName: String, hostName: String) =
        EmailMessage.build(
            recipient = recipient.email!!,
            subject = "Verify your email address to $instanceName",
        ) {
            p { +"Hi, ${recipient.name}!" }
            p { +"Thank you for signing up to $instanceName! Please verify your email address by clicking the link below:" }
            p {
                a(href = "$hostName/verify/${recipient.id}/$verificationCode") {
                    +"\uD83D\uDC49 Verify email"
                }
            }
            p { +"If you didn’t request this, you can safely ignore this message." }
            signature(instanceName)
        }

    fun passwordReset(recipient: String, resetCode: String, instanceName: String, hostName: String) =
        EmailMessage.build(
            recipient = recipient,
            subject = "Reset your $instanceName password",
        ) {
            p { +"Hi!" }
            p { +"We received a request to reset your password. You can create a new one by clicking the link below:" }
            p {
                a(href = "$hostName/reset-password/$resetCode") {
                    +"\uD83D\uDC49 Reset password"
                }
            }
            p { +"This link will expire in 30 minutes. If you didn't request a password reset, you can safely ignore this email." }
            signature(instanceName)
        }

    private fun FlowContent.signature(instanceName: String) {
        p {
            +"Thanks,"
            br
            +"The $instanceName team"
        }
    }
}