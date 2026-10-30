package app.ourdays.billing

import android.content.Context

/** GitHub flavor: no Play Billing, code unlock only. */
object PremiumBillingProvider {
    fun create(context: Context): PremiumBilling = NoPremiumBilling
}
