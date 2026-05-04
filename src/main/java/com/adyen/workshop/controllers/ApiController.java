package com.adyen.workshop.controllers;

import com.adyen.Service;
import com.adyen.model.RequestOptions;
import com.adyen.model.checkout.*;

import com.adyen.model.payment.CancelRequest;
import com.adyen.model.payment.CaptureRequest;
import com.adyen.model.payment.RefundRequest;
import com.adyen.service.checkout.ModificationsApi;
import com.adyen.service.checkout.RecurringApi;
import com.adyen.workshop.Storage;
import com.adyen.workshop.configurations.ApplicationConfiguration;
import com.adyen.service.checkout.PaymentsApi;
import com.adyen.model.checkout.PaymentAmountUpdateRequest;
import com.adyen.service.exception.ApiException;
import org.apache.catalina.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.io.IOException;
import java.util.UUID;

@RestController
public class ApiController {
    private final Logger log = LoggerFactory.getLogger(ApiController.class);

    private final ApplicationConfiguration applicationConfiguration;
    private final PaymentsApi paymentsApi;
    private final RecurringApi recurringApi;
    private final ModificationsApi modificationsApi;

    private Long lastAmount;

    public ApiController(ApplicationConfiguration applicationConfiguration,
                         PaymentsApi paymentsApi,
                         RecurringApi recurringApi,
                         ModificationsApi modificationsApi) {
        this.applicationConfiguration = applicationConfiguration;
        this.paymentsApi = paymentsApi;
        this.recurringApi = recurringApi;
        this.modificationsApi = modificationsApi;
    }

    // Step 0
    @GetMapping("/hello-world")
    public ResponseEntity<String> helloWorld() throws Exception {
        return ResponseEntity.ok().body("This is the 'Hello World' from the workshop - You've successfully finished step 0!");
    }

    // Step 7
    @PostMapping("/api/paymentMethods")
    public ResponseEntity<PaymentMethodsResponse> paymentMethods() throws IOException, ApiException {
        PaymentMethodsRequest paymentMethodsRequest = new PaymentMethodsRequest()
                .merchantAccount(applicationConfiguration.getAdyenMerchantAccount());

        PaymentMethodsResponse response =
                paymentsApi.paymentMethods(paymentMethodsRequest);

        log.info("Payment methods response: {}", response);

        return ResponseEntity.ok().body(response);
    }

    // Step 9 - Implement the /payments call to Adyen.
    @PostMapping("/api/payments")
    public ResponseEntity<PaymentResponse> payments(@RequestBody PaymentRequest body) throws IOException, ApiException {
        RequestOptions requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        PaymentRequest paymentRequest = new PaymentRequest();

        Amount amount = new Amount()
                .currency("EUR")
                .value(9998L);
        paymentRequest.setAmount(amount);
        paymentRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        paymentRequest.setChannel(PaymentRequest.ChannelEnum.WEB);

        AuthenticationData authenticationData = new AuthenticationData();
        authenticationData.setAttemptAuthentication(AuthenticationData.AttemptAuthenticationEnum.ALWAYS);
        paymentRequest.setAuthenticationData(authenticationData);

        paymentRequest.setPaymentMethod(body.getPaymentMethod());
        paymentRequest.setOrigin("https://localhost:8080");
        paymentRequest.setShopperIP("192.168.0.1");
        paymentRequest.setShopperInteraction(PaymentRequest.ShopperInteractionEnum.ECOMMERCE);
        paymentRequest.setBrowserInfo(body.getBrowserInfo());

        BillingAddress billingAddress = new BillingAddress();
        billingAddress.setCity("Amsterdam");
        billingAddress.setCountry("NL");
        billingAddress.setPostalCode("1012KK");
        billingAddress.setStreet("Rokin");
        billingAddress.setHouseNumberOrName("49");
        paymentRequest.setBillingAddress(billingAddress);

        String orderRef = UUID.randomUUID().toString();
        paymentRequest.setReference(orderRef);

        paymentRequest.setReturnUrl("http://localhost:8080/handleShopperRedirect");

        log.info("PaymentsRequest {}", paymentRequest);
        PaymentResponse response = paymentsApi.payments(paymentRequest, requestOptions);
        log.info("PaymentsResponse {}", response);
        return ResponseEntity.ok().body(response);
    }

    // Step 13 - Handle details call (triggered after Native 3DS2 flow)
    @PostMapping("/api/payments/details")
    public ResponseEntity<PaymentDetailsResponse> paymentsDetails(@RequestBody PaymentDetailsRequest detailsRequest) throws IOException, ApiException {
        log.info("PaymentDetailsRequest {}", detailsRequest);
        var response = paymentsApi.paymentsDetails(detailsRequest);
        log.info("PaymentDetailsResponse {}", response);
        return ResponseEntity.ok().body(response);
    }

    // Step 14 - Handle Redirect 3DS2 during payment.
    @GetMapping("/handleShopperRedirect")
    public RedirectView redirect(@RequestParam(required = false) String payload, @RequestParam(required = false) String redirectResult) throws IOException, ApiException {
        var paymentDetailsRequest = new PaymentDetailsRequest();

        PaymentCompletionDetails paymentCompletionDetails = new PaymentCompletionDetails();

        return new RedirectView("/result/error");
    }

    // TOKENIZATION: create token with zero-auth
    @PostMapping("/api/subscription-create")
    public ResponseEntity<PaymentResponse> subscriptionCreate(@RequestBody PaymentRequest body)
            throws IOException, ApiException {

        var paymentRequest = new PaymentRequest();
        paymentRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        paymentRequest.setAmount(new Amount().currency("EUR").value(0L)); // zero-auth
        paymentRequest.setChannel(PaymentRequest.ChannelEnum.WEB);
        paymentRequest.setPaymentMethod(body.getPaymentMethod());
        paymentRequest.setReference("sub-create-" + UUID.randomUUID());
        paymentRequest.setReturnUrl("http://localhost:8080/handleShopperRedirect");
        paymentRequest.setStorePaymentMethod(true);
        paymentRequest.setShopperReference(Storage.SHOPPER_REFERENCE);
        paymentRequest.setShopperInteraction(PaymentRequest.ShopperInteractionEnum.ECOMMERCE);
        paymentRequest.setRecurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.SUBSCRIPTION);

        var requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        log.info("Subscription create request {}", paymentRequest);
        var response = paymentsApi.payments(paymentRequest, requestOptions);
        log.info("Subscription create response {}", response);

        return ResponseEntity.ok(response);
    }

    // TOKENIZATION: charge using stored token
    @PostMapping("/api/subscription-payment")
    public ResponseEntity<?> subscriptionPayment() throws IOException, ApiException {
        if (Storage.STORED_PAYMENT_METHOD_ID == null) {
            log.info("Subscription is missing(deleted)");
            return ResponseEntity.badRequest().body("No stored token found yet. Complete /api/subscription-create and wait for webhook.");
        }

        var paymentRequest = new PaymentRequest();
        paymentRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        paymentRequest.setAmount(new Amount().currency("EUR").value(500L)); // €5.00 monthly
        paymentRequest.setReference("sub-charge-" + UUID.randomUUID());
        paymentRequest.setShopperReference(Storage.SHOPPER_REFERENCE);
        paymentRequest.setShopperInteraction(PaymentRequest.ShopperInteractionEnum.CONTAUTH);
        paymentRequest.setRecurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.SUBSCRIPTION);
        paymentRequest.setPaymentMethod(
                new CheckoutPaymentMethod(
                        new StoredPaymentMethodDetails().storedPaymentMethodId(Storage.STORED_PAYMENT_METHOD_ID)
                )
        );

        var requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        log.info("Subscription payment request {}", paymentRequest);
        PaymentResponse response;
        try {
            response = paymentsApi.payments(paymentRequest, requestOptions);
        } catch (ApiException e) {
            log.error("Subscription payment failed: {}", e.getError());
            return ResponseEntity.badRequest().body("Subscription payment failed: " + e.getError().getMessage());
        }
        log.info("Subscription payment response {}", response);

        return ResponseEntity.ok(response);
    }

    // Workshop-level cancel: delete locally stored token
    @PostMapping("/api/subscription-cancel")
    public ResponseEntity<String> subscriptionCancel() throws IOException, ApiException {
        if (Storage.STORED_PAYMENT_METHOD_ID == null) {
            return ResponseEntity.badRequest().body("No stored token found.");
        }

        String token = Storage.STORED_PAYMENT_METHOD_ID;

        recurringApi.deleteTokenForStoredPaymentDetails(
                token,
                Storage.SHOPPER_REFERENCE,
                applicationConfiguration.getAdyenMerchantAccount()
        );

//        Optional, decided to comment this in order to have error from Adyen
//        Storage.STORED_PAYMENT_METHOD_ID = null;

        log.info("Deleted stored payment method from Adyen: {}", token);

        return ResponseEntity.ok("Subscription token deleted from Adyen.");
    }

    @PostMapping("/api/preauthorisation")
    public ResponseEntity<PaymentResponse> preauthorisation(@RequestBody PaymentRequest body)
            throws IOException, ApiException {

        var paymentRequest = new PaymentRequest();

        paymentRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        paymentRequest.setAmount(new Amount().currency("EUR").value(10000L));
        lastAmount = 10000L;
        paymentRequest.setChannel(PaymentRequest.ChannelEnum.WEB);
        paymentRequest.setPaymentMethod(body.getPaymentMethod());
        paymentRequest.setReference("preauth-" + UUID.randomUUID());
        paymentRequest.setReturnUrl("http://localhost:8080/handleShopperRedirect");

        // Important: manual capture. Authorise now, capture later.
        paymentRequest.setCaptureDelayHours(0);

        var requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        var response = paymentsApi.payments(paymentRequest, requestOptions);

        if (response.getPspReference() != null) {
            Storage.LATEST_PRE_AUTH_PSP_REFERENCE = response.getPspReference();
            log.info("Stored preauth PSP reference: {}", Storage.LATEST_PRE_AUTH_PSP_REFERENCE);
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/modify-amount")
    public ResponseEntity<?> modifyAmount() throws IOException, ApiException {
        if (Storage.LATEST_PRE_AUTH_PSP_REFERENCE == null) {
            return ResponseEntity.badRequest().body("No pre-authorised payment found.");
        }

        var amountUpdateRequest = new PaymentAmountUpdateRequest();
        amountUpdateRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        amountUpdateRequest.setAmount(new Amount().currency("EUR").value(15000L));
        lastAmount = 15000L;
        amountUpdateRequest.setReference("modify-amount-" + UUID.randomUUID());

        var response = modificationsApi.updateAuthorisedAmount(
                Storage.LATEST_PRE_AUTH_PSP_REFERENCE,
                amountUpdateRequest
        );

        log.info("Modify amount response {}", response);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/capture")
    public ResponseEntity<?> capture() throws IOException, ApiException {
        if (Storage.LATEST_PRE_AUTH_PSP_REFERENCE == null) {
            log.info("No stored token found.");
            return ResponseEntity.badRequest().body("No pre-authorised payment found.");
        }

        var captureRequest = new PaymentCaptureRequest();
        captureRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        captureRequest.setAmount(new Amount().currency("EUR").value(lastAmount));
        captureRequest.setReference("capture-" + UUID.randomUUID());

        var response = modificationsApi.captureAuthorisedPayment(
                Storage.LATEST_PRE_AUTH_PSP_REFERENCE,
                captureRequest,
                new RequestOptions().idempotencyKey("UUID")
        );

        log.info("Capture response {}", response);

        Storage.CAPTURED_PSP_REFERENCE = response.getPspReference();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/cancel")
    public ResponseEntity<?> cancelPreauthorisation() throws IOException, ApiException {
        if (Storage.LATEST_PRE_AUTH_PSP_REFERENCE == null) {
            log.info("No stored token found.");
            return ResponseEntity.badRequest().body("No pre-authorised payment found.");
        }

        var cancelRequest = new StandalonePaymentCancelRequest();
        cancelRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        cancelRequest.setReference("cancel-" + UUID.randomUUID());
        cancelRequest.setReference(Storage.LATEST_PRE_AUTH_PSP_REFERENCE);

        var response = modificationsApi.cancelAuthorisedPayment(
                cancelRequest,
                new RequestOptions().idempotencyKey("UUID")
        );

        log.info("Cancel response {}", response);

//        Storage.LATEST_PRE_AUTH_PSP_REFERENCE = null;

        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/refund")
    public ResponseEntity<?> refund() throws IOException, ApiException {
        if (Storage.LATEST_PRE_AUTH_PSP_REFERENCE == null) {
            return ResponseEntity.badRequest().body("No original payment PSP reference found.");
        }

        var refundRequest = new PaymentRefundRequest();
        refundRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        refundRequest.setAmount(new Amount().currency("EUR").value(lastAmount));
        refundRequest.setReference("refund-" + UUID.randomUUID());

        var response = modificationsApi.refundCapturedPayment(
                Storage.LATEST_PRE_AUTH_PSP_REFERENCE,
                refundRequest
        );

        log.info("Refund response {}", response);

        return ResponseEntity.ok(response);
    }
}