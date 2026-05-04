const clientKey = document.getElementById("clientKey").innerHTML;
const { AdyenCheckout, Dropin } = window.AdyenWeb;

async function startCheckout() {
    const paymentMethodsResponse = await fetch("/api/paymentMethods", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
    }).then((response) => response.json());

    const configuration = {
        paymentMethodsResponse,
        clientKey,
        locale: "en_US",
        countryCode: "NL",
        environment: "test",
        showPayButton: true,
        translations: {
            "en-US": {
                payButton: "Pre-authorise",
                "creditCard.securityCode.label": "CVV/CVC",
            },
        },

        onSubmit: async (state, component, actions) => {
            try {
                if (!state.isValid) {
                    actions.reject();
                    return;
                }

                const { action, order, resultCode } = await fetch("/api/preauthorisation", {
                    method: "POST",
                    body: state.data ? JSON.stringify(state.data) : "",
                    headers: { "Content-Type": "application/json" },
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

        onPaymentCompleted: (result) => {
            console.info("Preauth completed", result);
            alert("Pre-authorisation completed. Now you can capture or cancel.");
        },

        onPaymentFailed: () => {
            window.location.href = "/result/failed";
        },

        onError: (error) => {
            console.error(error);
            window.location.href = "/result/error";
        },

        onAdditionalDetails: async (state, component, actions) => {
            try {
                const { resultCode } = await fetch("/api/payments/details", {
                    method: "POST",
                    body: state.data ? JSON.stringify(state.data) : "",
                    headers: { "Content-Type": "application/json" },
                }).then((response) => response.json());

                actions.resolve({ resultCode });
            } catch (error) {
                console.error(error);
                actions.reject();
            }
        },
    };

    const paymentMethodsConfiguration = {
        card: {
            showBrandIcon: true,
            hasHolderName: true,
            holderNameRequired: true,
            name: "Credit or debit card",
            amount: {
                value: 10000,
                currency: "EUR",
            },
        },
    };

    const adyenCheckout = await AdyenCheckout(configuration);

    new Dropin(adyenCheckout, {
        paymentMethodsConfiguration,
    }).mount(document.getElementById("payment"));
}

startCheckout();

document.getElementById("capture-button")?.addEventListener("click", async () => {
    const response = await fetch("/api/capture", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
    });

    alert(await response.text());
});

document.getElementById("cancel-button")?.addEventListener("click", async () => {
    const response = await fetch("/api/cancel", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
    });

    alert(await response.text());
});

document.getElementById("modify-amount-button")?.addEventListener("click", async () => {
    try {
        const response = await fetch("/api/modify-amount", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            },
        });

        const data = await response.json();

        console.log("Modify amount response:", data);
        alert("Amount modification requested. Check webhook AUTHORISATION_ADJUSTMENT.");
    } catch (error) {
        console.error("Modify amount failed:", error);
        alert("Could not modify amount.");
    }
});

document.getElementById("refund-button")?.addEventListener("click", async () => {
    try {
        const response = await fetch("/api/refund", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            },
        });

        const text = await response.text();

        console.log("Refund response:", text);
        alert(text);
    } catch (error) {
        console.error("Refund failed:", error);
        alert("Could not refund payment.");
    }
});