package me.piebridge.billing;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.RemoteException;
import android.widget.Toast;

import com.android.vending.billing.IInAppBillingService;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import me.piebridge.forcestopgb.BuildConfig;
import me.piebridge.forcestopgb.R;
import me.piebridge.prevent.ui.UILog;

/**
 * Created by thom on 15/10/11.
 */
public abstract class DonateActivity extends Activity {

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == DonateUtils.REQUEST_CODE && resultCode == RESULT_OK) {
            String data = intent.getStringExtra("INAPP_PURCHASE_DATA");
            String signature = intent.getStringExtra("INAPP_DATA_SIGNATURE");
            if (DonateUtils.verify(data, signature)) {
                onDonatedOnUi();
            } else {
                Toast.makeText(this, R.string.play_verify_error, Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean checkDonate(IInAppBillingService service) {
        try {
            Bundle sku = new Bundle();
            ArrayList<String> skuList = new ArrayList<String>(); // NOSONAR
            skuList.add(DonateUtils.ITEM_ID);
            sku.putStringArrayList("ITEM_ID_LIST", skuList);
            Bundle skuDetails = service.getSkuDetails(DonateUtils.API_VERSION, getPackageName(), DonateUtils.ITEM_TYPE, sku);
            if (skuDetails.getInt("RESPONSE_CODE") != 0) {
                UILog.e("response code: " + skuDetails.getInt("RESPONSE_CODE"));
                return false;
            }
            List<String> detailsList = skuDetails.getStringArrayList("DETAILS_LIST");
            if (DonateUtils.isEmpty(detailsList)) {
                UILog.e("cannot find sku details");
                return false;
            }
            for (String details : detailsList) {
                JSONObject object = new JSONObject(details);
                if (DonateUtils.ITEM_ID.equals(object.get("productId"))) {
                    return true;
                }
            }
            UILog.e("cannot find sku for " + DonateUtils.ITEM_ID);
        } catch (RemoteException e) {
            UILog.d("cannot get sku details", e);
        } catch (JSONException e) {
            UILog.d("cannot get json", e);
        }
        return false;
    }

    private boolean donate(IInAppBillingService service) {
        try {
            Bundle bundle = service.getBuyIntent(DonateUtils.API_VERSION, getPackageName(), DonateUtils.ITEM_ID,
                    DonateUtils.ITEM_TYPE, DonateUtils.ITEM_ID);
            PendingIntent intent = bundle.getParcelable("BUY_INTENT");
            if (intent != null) {
                startIntentSenderForResult(intent.getIntentSender(), DonateUtils.REQUEST_CODE, new Intent(), 0, 0, 0);
                return true;
            } else {
                UILog.e("cannot get buy intent");
            }
        } catch (RemoteException e) {
            UILog.d("cannot get buy intent", e);
        } catch (IntentSender.SendIntentException e) {
            UILog.d("cannot start buy intent", e);
        }
        return false;
    }

    protected void donateViaPlay() {
        Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
        serviceIntent.setPackage("com.android.vending");
        bindService(serviceIntent, new DonateService(this) {
            @Override
            public void onAvailable(IInAppBillingService service) {
                boolean donating = false;
                if (!BuildConfig.DEBUG || checkDonate(service)) {
                    donating = donate(service);
                }
                if (!donating) {
                    onDonateFailedOnUi();
                }
            }

            @Override
            protected boolean isBillingSupported() {
                return true;
            }
        }, Context.BIND_AUTO_CREATE);
    }

    private void onDonateFailedOnUi() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onDonateFailed();
            }
        });
    }

    protected void checkDonate() {
        Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
        serviceIntent.setPackage("com.android.vending");
        bindService(serviceIntent, new DonateService(this) {
            @Override
            public void onAvailable(IInAppBillingService service) {
                if (isDonated()) {
                    onDonatedOnUi();
                } else {
                    onAvailableOnUi();
                }
            }
        }, Context.BIND_AUTO_CREATE);
    }

    void onAvailableOnUi() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onAvailable();
            }
        });
    }

    void onUnavailableOnUi() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onUnavailable();
            }
        });
    }

    private void onDonatedOnUi() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onDonated();
            }
        });
    }

    protected void onAvailable() {

    }

    protected void onUnavailable() {

    }

    protected void onDonated() {

    }

    protected void onDonateFailed() {

    }

}
