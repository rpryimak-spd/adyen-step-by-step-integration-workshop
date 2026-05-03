const clientKey = document.getElementById("clientKey").innerHTML;
const { AdyenCheckout, Dropin } = window.AdyenWeb;

async function startCheckout() {
    try {
        const paymentMethodsResponse = await fetch("/api/paymentMethods", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            },
        }).then((response) => response.json());

        const configuration = {
            paymentMethodsResponse: paymentMethodsResponse,
            clientKey,
            locale: "en_US",
            countryCode: 'NL',
            environment: "test",
            showPayButton: true,
            translations: {
                'en-US': {
                    'creditCard.securityCode.label': 'CVV/CVC'
                }
            },
            // Step 10 - Add the onSubmit handler by telling it what endpoint to call when the pay button is pressed.
            onSubmit: async (state, component, actions) => {
                console.info("onSubmit", state, component, actions);
                try {
                    if (!state.isValid) {
                        actions.reject();
                        return;
                    }

                    const { action, order, resultCode } = await fetch("/api/subscription-create", {
                        method: "POST",
                        body: state.data ? JSON.stringify(state.data) : "",
                        headers: {
                            "Content-Type": "application/json",
                        },
                    }).then((response) => response.json());

                    if (!resultCode && !action) {
                        actions.reject();
                        return;
                    }

                    actions.resolve({ resultCode, action, order });
                } catch (error) {
                    console.error(error);
                    actions.reject();
                }
            },

            onPaymentCompleted: (result, component) => {
                console.info("onPaymentCompleted", result, component);
                handleOnPaymentCompleted(result);
            },

            onPaymentFailed: (result, component) => {
                console.info("onPaymentFailed", result, component);
                handleOnPaymentFailed(result);
            },

            onError: (error, component) => {
                console.error("onError", error, component);
                window.location.href = "/result/error";
            },
            onAdditionalDetails: async (state, component, actions) => {
                console.info("onAdditionalDetails", state, component);
                try {
                    const { resultCode } = await fetch("/api/payments/details", {
                        method: "POST",
                        body: state.data ? JSON.stringify(state.data) : "",
                        headers: {
                            "Content-Type": "application/json",
                        }
                    }).then(response => response.json());

                    if (!resultCode) {
                        console.warn("reject");
                        actions.reject();
                    }

                    actions.resolve({ resultCode });
                } catch (error) {
                    console.error(error);
                    actions.reject();
                }
            }
        };

        const paymentMethodsConfiguration = {
            card: {
                showBrandIcon: true,
                hasHolderName: true,
                holderNameRequired: true,
                enableStoreDetails: true,
                name: "Credit or debit card",
                amount: {
                    value: 0,
                    currency: "EUR",
                },
                placeholders: {
                    cardNumber: "1234 5678 9012 3456",
                    expiryDate: "MM/YY",
                    securityCodeThreeDigits: "123",
                    securityCodeFourDigits: "1234",
                    holderName: "Developer Relations Team",
                },
            },
        };

        const adyenCheckout = await AdyenCheckout(configuration);

        new Dropin(adyenCheckout, {
            paymentMethodsConfiguration,
        }).mount(document.getElementById("payment"));
    } catch (error) {
        console.error(error);
        alert("Error occurred. Look at console for details.");
    }
}

function handleOnPaymentCompleted(response) {
    switch (response.resultCode) {
        case "Authorised":
            window.location.href = "/result/success";
            break;
        case "Pending":
        case "Received":
            window.location.href = "/result/pending";
            break;
        default:
            window.location.href = "/result/error";
            break;
    }
}

function handleOnPaymentFailed(response) {
    switch (response.resultCode) {
        case "Cancelled":
        case "Refused":
            window.location.href = "/result/failed";
            break;
        default:
            window.location.href = "/result/error";
            break;
    }
}

startCheckout();