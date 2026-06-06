package party.jml.partyboi.email

import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.br
import kotlinx.html.p
import party.jml.partyboi.auth.User

object EmailTemplates {
    fun emailVerification(recipient: User, verificationCode: String, instanceName: String, hostName: String) =
        EmailMessage.build(
            recipient = requireNotNull(recipient.email) { "Cannot send verification email to user without email address" },
            subject = "Verify your $instanceName email address",
        ) {
            p { +"Hi, ${recipient.name}!" }
            p { +"Thanks for signing up to $instanceName! Verify your email address with the link below:" }
            p {
                a(href = "$hostName/verify/${recipient.id}/$verificationCode") {
                    +"\uD83D\uDC49 Verify email"
                }
            }
            p { +"If you didn't request this, you can safely ignore this message." }
            signature(instanceName)
        }

    fun passwordReset(recipient: String, resetCode: String, instanceName: String, hostName: String) =
        EmailMessage.build(
            recipient = recipient,
            subject = "Reset your $instanceName password",
        ) {
            p { +"Hi!" }
            p { +"We got a request to reset your password. Create a new one with the link below:" }
            p {
                a(href = "$hostName/reset-password/$resetCode") {
                    +"\uD83D\uDC49 Reset password"
                }
            }
            p { +"This link expires in 30 minutes. If you didn't request this, you can safely ignore this email." }
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