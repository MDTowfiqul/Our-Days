package app.ourdays.billing

import android.app.Activity
import app.ourdays.data.PremiumEntitlement

/**
 * Buying premium. Only the Play flavor has a real implementation
 * ([PlayPremiumBilling]); the other flavors return [NoPremiumBilling]
 * through their own [PremiumBillingProvider], so no Play Billing code is
 * ever compiled into the GitHub or F-Droid builds.
 */
interface PremiumBilling {
    /** Premium already owned on this device, null = none. */
    suspend fun queryOwned(): PremiumEntitlement?

    /**
     * Starts the purchase flow. Returns the entitlement, null when the user
     * cancels. Throws on errors (e.g. product missing in Play Console).
     */
    suspend fun launchPurchase(activity: Activity): PremiumEntitlement?
}

/** Flavor without Play Billing: no purchase possible, code unlock only. */
object NoPremiumBilling : PremiumBilling {
    override suspend fun queryOwned(): PremiumEntitlement? = null

    override suspend fun launchPurchase(activity: Activity): PremiumEntitlement? =
        throw java.io.IOException("Play Billing is not available in this flavor")
}
