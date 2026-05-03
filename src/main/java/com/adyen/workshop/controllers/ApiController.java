package com.adyen.workshop.controllers;

import com.adyen.Service;
import com.adyen.model.RequestOptions;
import com.adyen.model.checkout.*;
import com.adyen.workshop.Storage;
import com.adyen.workshop.configurations.ApplicationConfiguration;
import com.adyen.service.checkout.PaymentsApi;
import com.adyen.service.exception.ApiException;
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
    private final Service service;

    public ApiController(ApplicationConfiguration applicationConfiguration,
                         PaymentsApi paymentsApi, Service service) {
        this.applicationConfiguration = applicationConfiguration;
        this.paymentsApi = paymentsApi;
        this.service = service;
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
        var response = paymentsApi.payments(paymentRequest, requestOptions);
        log.info("Subscription payment response {}", response);

        return ResponseEntity.ok(response);
    }

    // Workshop-level cancel: delete locally stored token
    @PostMapping("/api/subscription-cancel")
    public ResponseEntity<String> subscriptionCancel() {
        Storage.STORED_PAYMENT_METHOD_ID = null;
        return ResponseEntity.ok("Stored subscription token removed.");
    }
}