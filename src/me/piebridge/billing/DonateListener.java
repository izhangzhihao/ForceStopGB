package me.piebridge.billing;

import com.android.vending.billing.IInAppBillingService;

/**
 * Created by thom on 15/10/12.
 */
public interface DonateListener {
    void onAvailable(IInAppBillingService service);

    void onUnavailable(IInAppBillingService service);
}
