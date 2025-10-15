package com.tituspeterson.capacitor.cloversdk;

import android.accounts.Account;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Typeface;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResult;

import com.clover.sdk.util.CloverAccount;
import com.clover.sdk.util.Platform2;
import com.clover.sdk.v1.Intents;
import com.clover.sdk.v1.printer.Category;
import com.clover.sdk.v1.printer.Printer;
import com.clover.sdk.v1.printer.PrinterConnector;
import com.clover.sdk.v1.printer.job.PrintJobsConnector;
import com.clover.sdk.v1.printer.job.ViewPrintJob;
import com.clover.sdk.v3.payments.Credit;
import com.clover.sdk.v3.payments.Payment;
import com.clover.sdk.v3.payments.api.CreditRequestIntentBuilder;
import com.clover.sdk.v3.payments.api.PaymentRequestIntentBuilder;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.ActivityCallback;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@CapacitorPlugin(name = "CloverAndroidSDK")
public class CloverAndroidSDKPlugin extends Plugin {

    private static final String TAG = "CapacitorCloverSDK";
    private static final String BARCODE_BROADCAST = "com.clover.BarcodeBroadcast";

    private static volatile String remoteApplicationId = null;

    private final BarcodeReceiver barcodeReceiver = new BarcodeReceiver();
    private boolean barcodeRegistered = false;

    private PluginCall pendingBarcodeCall;
    private PluginCall pendingSaleCall;
    private PluginCall pendingRefundCall;

    @Override
    protected void handleOnStop() {
        super.handleOnStop();
        unregisterBarcodeScanner();
    }

    @Override
    protected void handleOnDestroy() {
        super.handleOnDestroy();
        unregisterBarcodeScanner();
        pendingBarcodeCall = null;
        pendingSaleCall = null;
        pendingRefundCall = null;
    }

    @PluginMethod
    public void getNewUID(PluginCall call) {
        JSObject result = new JSObject();
        result.put("uid", UUID.randomUUID().toString());
        call.resolve(result);
    }

    @PluginMethod
    public void setRemoteApplicationId(PluginCall call) {
        String applicationId = call.getString("remoteApplicationId");
        if (applicationId == null || applicationId.trim().isEmpty()) {
            call.reject("The 'remoteApplicationId' parameter is required.");
            return;
        }
        remoteApplicationId = applicationId.trim();
        JSObject result = new JSObject();
        result.put("remoteApplicationId", remoteApplicationId);
        call.resolve(result);
    }

    @PluginMethod
    public void isClover(PluginCall call) {
        JSObject result = new JSObject();
        result.put("isClover", Platform2.isClover());
        call.resolve(result);
    }

    @PluginMethod
    public void startScan(PluginCall call) {
        if (pendingBarcodeCall != null) {
            call.reject("A barcode scan is already in progress.");
            return;
        }

        Context context = getContext();
        if (context == null) {
            call.reject("Context unavailable.");
            return;
        }

        pendingBarcodeCall = call;
        call.setKeepAlive(true);
        registerBarcodeScanner();
        getBridge().executeOnMainThread(() -> {
            try {
                new com.clover.sdk.v3.scanner.BarcodeScanner(context.getApplicationContext())
                    .startScan(getBarcodeSettings(true));
            } catch (Exception e) {
                Log.e(TAG, "Failed to start barcode scan", e);
                PluginCall pending = pendingBarcodeCall;
                pendingBarcodeCall = null;
                stopBarcodeScanner();
                if (pending != null) {
                    pending.reject("Failed to start barcode scan: " + e.getMessage());
                }
            }
        });
    }

    @PluginMethod
    public void printTextReceipt(PluginCall call) {
        String receipt = call.getString("receipt");
        if (receipt == null) {
            call.reject("The 'receipt' parameter is required.");
            return;
        }

        AppCompatActivity activity = getActivity();
        if (activity == null) {
            call.reject("Activity unavailable.");
            return;
        }

        AppCompatActivity finalActivity = activity;
        String finalReceipt = receipt;
        new Thread(() -> {
            PrinterConnector printerConnector = null;
            try {
                Account account = CloverAccount.getAccount(finalActivity);
                printerConnector = new PrinterConnector(finalActivity, account, null);
                List<Printer> printers = printerConnector.getPrinters(Category.RECEIPT);
                if (printers == null || printers.isEmpty()) {
                    getBridge().executeOnMainThread(() ->
                        call.reject("No receipt printer found.")
                    );
                    return;
                }

                Printer preferredPrinter = printers.get(0);

                CountDownLatch viewReady = new CountDownLatch(1);
                AtomicReference<TextView> viewRef = new AtomicReference<>();
                getBridge().executeOnMainThread(() -> {
                    TextView view = new TextView(finalActivity);
                    view.setText(finalReceipt);
                    view.setTextColor(Color.BLACK);
                    view.setTextScaleX(0.76f);
                    view.setTextSize(10);
                    view.setTypeface(Typeface.MONOSPACE);
                    viewRef.set(view);
                    viewReady.countDown();
                });

                if (!viewReady.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out preparing receipt view.");
                }

                int width = printerConnector.getPrinterTypeDetails(preferredPrinter).getNumDotsWidth();
                ViewPrintJob job = new ViewPrintJob.Builder()
                    .view(viewRef.get(), width)
                    .build();
                PrintJobsConnector jobsConnector = new PrintJobsConnector(finalActivity);
                jobsConnector.print(job);

                getBridge().executeOnMainThread(() -> {
                    JSObject result = new JSObject();
                    result.put("success", true);
                    call.resolve(result);
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.e(TAG, "Failed to prepare receipt view", e);
                getBridge().executeOnMainThread(() ->
                    call.reject("Failed to print receipt: " + e.getMessage())
                );
            } catch (Exception e) {
                Log.e(TAG, "Failed to print receipt", e);
                getBridge().executeOnMainThread(() ->
                    call.reject("Failed to print receipt: " + e.getMessage())
                );
            } finally {
                if (printerConnector != null) {
                    try {
                        printerConnector.disconnect();
                    } catch (Exception ignored) {
                        // ignore disconnect issues
                    }
                }
            }
        }).start();
    }

    @PluginMethod
    public void takePayment(PluginCall call) {
        if (!ensureCloverDevice(call)) {
            return;
        }
        if (pendingSaleCall != null) {
            call.reject("A payment request is already in progress.");
            return;
        }

        Long amount = getAmountInCents(call);
        if (amount == null || amount <= 0) {
            call.reject("The 'amount' parameter (in cents) is required and must be positive.");
            return;
        }

        String uid = call.getString("uid");
        if (uid == null || uid.trim().isEmpty()) {
            uid = UUID.randomUUID().toString();
        }

        AppCompatActivity activity = getActivity();
        if (activity == null) {
            call.reject("Activity unavailable.");
            return;
        }

        pendingSaleCall = call;
        call.setKeepAlive(true);

        String finalUid = uid;
        long finalAmount = amount;
        AppCompatActivity finalActivity = activity;
        getBridge().executeOnMainThread(() -> {
            try {
                Intent intent = new PaymentRequestIntentBuilder(finalUid, finalAmount)
                    .externalReferenceId(finalUid)
                    .build(finalActivity);
                startActivityForResult(call, intent, "paymentResult");
            } catch (Exception e) {
                Log.e(TAG, "Failed to initiate payment", e);
                pendingSaleCall = null;
                call.reject("Failed to start payment intent: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void refund(PluginCall call) {
        if (!ensureCloverDevice(call)) {
            return;
        }
        if (pendingRefundCall != null) {
            call.reject("A refund request is already in progress.");
            return;
        }

        Long amount = getAmountInCents(call);
        if (amount == null || amount <= 0) {
            call.reject("The 'amount' parameter (in cents) is required and must be positive.");
            return;
        }

        String uid = call.getString("uid");
        if (uid == null || uid.trim().isEmpty()) {
            call.reject("The 'uid' parameter is required for refunds.");
            return;
        }

        AppCompatActivity activity = getActivity();
        if (activity == null) {
            call.reject("Activity unavailable.");
            return;
        }

        pendingRefundCall = call;
        call.setKeepAlive(true);

        long finalAmount = amount;
        String finalUid = uid;
        AppCompatActivity finalActivity = activity;
        getBridge().executeOnMainThread(() -> {
            try {
                Intent intent = new CreditRequestIntentBuilder(finalUid, finalAmount)
                    .build(finalActivity);
                startActivityForResult(call, intent, "refundResult");
            } catch (Exception e) {
                Log.e(TAG, "Failed to initiate credit", e);
                pendingRefundCall = null;
                call.reject("Failed to start refund intent: " + e.getMessage());
            }
        });
    }

    @ActivityCallback
    private void paymentResult(PluginCall call, ActivityResult result) {
        pendingSaleCall = null;
        if (call == null) {
            Log.w(TAG, "paymentResult received with null call");
            return;
        }
        Intent data = result.getData();
        Log.d(TAG, "paymentResult code=" + result.getResultCode() + ", data=" + data);
        handlePaymentActivityResult(call, result.getResultCode(), data);
    }

    @ActivityCallback
    private void refundResult(PluginCall call, ActivityResult result) {
        pendingRefundCall = null;
        if (call == null) {
            Log.w(TAG, "refundResult received with null call");
            return;
        }
        Intent data = result.getData();
        Log.d(TAG, "refundResult code=" + result.getResultCode() + ", data=" + data);
        handleCreditActivityResult(call, result.getResultCode(), data);
    }

    private void handlePaymentActivityResult(PluginCall call, int resultCode, Intent data) {
        Log.d(TAG, "handlePaymentActivityResult code=" + resultCode + ", data=" + data);
        if (resultCode == Activity.RESULT_OK && data != null) {
            Payment payment = getParcelableExtra(data, Intents.EXTRA_PAYMENT, Payment.class);
            if (payment != null) {
                try {
                    JSObject result = buildResultFromPayment(payment, data);
                    call.resolve(result);
                    return;
                } catch (JSONException e) {
                    call.reject("Failed to parse payment response: " + e.getMessage());
                    return;
                }
            }
        }

        String message = data != null ? data.getStringExtra(Intents.EXTRA_FAILURE_MESSAGE) : null;
        String reason = data != null ? data.getStringExtra(PaymentRequestIntentBuilder.Response.REASON) : null;
        boolean cancelled = resultCode == Activity.RESULT_CANCELED && (message == null || message.isEmpty());

        JSObject errorData = new JSObject();
        boolean hasDetails = false;
        if (reason != null) {
            errorData.put("reason", reason);
            hasDetails = true;
        }
        if (message != null) {
            errorData.put("message", message);
            hasDetails = true;
        }

        call.reject(
            message != null ? message : (cancelled ? "Payment cancelled." : "Payment failed."),
            cancelled ? "PAYMENT_CANCELLED" : "PAYMENT_FAILED",
            hasDetails ? errorData : null
        );
    }

    private void handleCreditActivityResult(PluginCall call, int resultCode, Intent data) {
        Log.d(TAG, "handleCreditActivityResult code=" + resultCode + ", data=" + data);
        if (resultCode == Activity.RESULT_OK && data != null) {
            Credit credit = getParcelableExtra(data, Intents.EXTRA_CREDIT, Credit.class);
            if (credit != null) {
                try {
                    JSObject result = buildResultFromCredit(credit, data);
                    call.resolve(result);
                    return;
                } catch (JSONException e) {
                    call.reject("Failed to parse refund response: " + e.getMessage());
                    return;
                }
            }
        }

        String message = data != null ? data.getStringExtra(Intents.EXTRA_FAILURE_MESSAGE) : null;
        boolean cancelled = resultCode == Activity.RESULT_CANCELED && (message == null || message.isEmpty());

        JSObject errorData = null;
        if (message != null) {
            errorData = new JSObject();
            errorData.put("message", message);
        }

        call.reject(
            message != null ? message : (cancelled ? "Refund cancelled." : "Refund failed."),
            cancelled ? "REFUND_CANCELLED" : "REFUND_FAILED",
            errorData
        );
    }

    private boolean ensureCloverDevice(PluginCall call) {
        if (!Platform2.isClover()) {
            call.reject("This device is not a Clover device.");
            return false;
        }
        return true;
    }

    private Long getAmountInCents(PluginCall call) {
        Long amount = call.getLong("amount");
        if (amount != null) {
            return amount;
        }
        Double amountDouble = call.getDouble("amount");
        if (amountDouble != null) {
            return Math.round(amountDouble);
        }
        return null;
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerBarcodeScanner() {
        Context context = getContext();
        if (context != null && !barcodeRegistered) {
            context.registerReceiver(barcodeReceiver, new IntentFilter(BARCODE_BROADCAST));
            barcodeRegistered = true;
        }
    }

    private void unregisterBarcodeScanner() {
        Context context = getContext();
        if (context != null && barcodeRegistered) {
            try {
                context.unregisterReceiver(barcodeReceiver);
            } catch (IllegalArgumentException ignored) {
                // Receiver already unregistered.
            }
            barcodeRegistered = false;
        }
    }

    private Bundle getBarcodeSettings(final boolean enabled) {
        Bundle extras = new Bundle();
        extras.putBoolean(Intents.EXTRA_START_SCAN, enabled);
        extras.putBoolean(Intents.EXTRA_SHOW_PREVIEW, enabled);
        extras.putBoolean(Intents.EXTRA_SHOW_MERCHANT_PREVIEW, enabled);
        extras.putBoolean(Intents.EXTRA_LED_ON, false);
        return extras;
    }

    private void stopBarcodeScanner() {
        Context context = getContext();
        if (context != null) {
            getBridge().executeOnMainThread(() ->
                new com.clover.sdk.v3.scanner.BarcodeScanner(context.getApplicationContext())
                    .stopScan(getBarcodeSettings(false))
            );
        }
        unregisterBarcodeScanner();
    }

    private class BarcodeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            String action = intent.getAction();
            if (BARCODE_BROADCAST.equals(action)) {
                String barcode = intent.getStringExtra("Barcode");
                stopBarcodeScanner();
                PluginCall call = pendingBarcodeCall;
                pendingBarcodeCall = null;
                if (call != null) {
                    if (barcode != null) {
                        JSObject result = new JSObject();
                        result.put("barcode", barcode);
                        call.resolve(result);
                    } else {
                        call.reject("Barcode payload missing.");
                    }
                }
            }
        }
    }

    private JSObject buildResultFromPayment(Payment payment, Intent data) throws JSONException {
        JSObject result = new JSObject();
        JSONObject paymentJson = payment.getJSONObject();
        result.put("payment", JSObject.fromJSONObject(paymentJson));

        addStringExtra(result, "enteredReceiptValue", data, PaymentRequestIntentBuilder.Response.ENTERED_RECEIPT_VALUE);
        addStringExtra(result, "receiptDeliveryType", data, PaymentRequestIntentBuilder.Response.RECEIPT_DELIVERY_TYPE);
        addStringExtra(result, "receiptDeliveryStatus", data, PaymentRequestIntentBuilder.Response.RECEIPT_DELIVERY_STATUS);
        addStringExtra(result, "tokenType", data, PaymentRequestIntentBuilder.Response.TOKEN_TYPE);
        addStringExtra(result, "token", data, PaymentRequestIntentBuilder.Response.TOKEN);
        addStringExtra(result, "reason", data, PaymentRequestIntentBuilder.Response.REASON);

        if (data != null && data.hasExtra(PaymentRequestIntentBuilder.Response.OPTED_INTO_MARKETING)) {
            result.put("optedIntoMarketing", data.getBooleanExtra(PaymentRequestIntentBuilder.Response.OPTED_INTO_MARKETING, false));
        }
        if (data != null && data.hasExtra(PaymentRequestIntentBuilder.Response.CHANGE_DUE)) {
            result.put("changeDue", data.getLongExtra(PaymentRequestIntentBuilder.Response.CHANGE_DUE, 0L));
        }

        return result;
    }

    private JSObject buildResultFromCredit(Credit credit, Intent data) throws JSONException {
        JSObject result = new JSObject();
        JSONObject creditJson = credit.getJSONObject();
        result.put("credit", JSObject.fromJSONObject(creditJson));

        addStringExtra(result, "enteredReceiptValue", data, CreditRequestIntentBuilder.Response.ENTERED_RECEIPT_VALUE);
        addStringExtra(result, "receiptDeliveryType", data, CreditRequestIntentBuilder.Response.RECEIPT_DELIVERY_TYPE);
        addStringExtra(result, "receiptDeliveryStatus", data, CreditRequestIntentBuilder.Response.RECEIPT_DELIVERY_STATUS);
        if (data != null && data.hasExtra(CreditRequestIntentBuilder.Response.OPTED_INTO_MARKETING)) {
            result.put("optedIntoMarketing", data.getBooleanExtra(CreditRequestIntentBuilder.Response.OPTED_INTO_MARKETING, false));
        }

        return result;
    }

    private void addStringExtra(JSObject target, String alias, Intent intent, String key) {
        if (intent != null) {
            String value = intent.getStringExtra(key);
            if (value != null) {
                target.put(alias, value);
            }
        }
    }

    private <T extends Parcelable> T getParcelableExtra(Intent intent, String key, Class<T> clazz) {
        if (intent == null) {
            return null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(key, clazz);
        } else {
            return intent.getParcelableExtra(key);
        }
    }

}
