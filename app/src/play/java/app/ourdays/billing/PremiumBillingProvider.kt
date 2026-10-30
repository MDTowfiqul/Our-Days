package app.ourdays.billing

import android.content.Context

/** Play flavor: real Play Billing. */
object PremiumBillingProvider {
    fun create(context: Context): PremiumBilling = PlayPremiumBilling(context)
}
