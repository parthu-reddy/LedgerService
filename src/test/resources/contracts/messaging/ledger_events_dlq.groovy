package contracts.messaging

/*
 * ledger-events-dlq -- the compensation route for ledger-rejected debits.
 *
 * Producer: LedgerService.LedgerEventListener.handleDltEvent, which fires after
 * @RetryableTopic(attempts = "5") on ledger-events is exhausted. It forwards the ORIGINAL
 * ledger-events payload VERBATIM: kafkaTemplate.send(TOPIC_LEDGER_EVENTS_DLQ, payload) -- no key,
 * no headers, no envelope, no re-serialization.
 *
 * Consumer: WalletService.LedgerFailureConsumer, which reads fromType, fromId, transferId and
 * amount AT THE ROOT and credits the amount back when fromType == ADVERTISER_WALLET.
 *
 * The verbatim forwarding is the point of this contract. Wrapping the payload in an
 * {eventType, payload} envelope -- the shape most other topics on this platform use -- would make
 * every root read return null and the consumer would silently do nothing: no exception, no DLQ,
 * nothing logged at ERROR. This contract fails if that happens.
 *
 * Body shape is WalletService.publishLedgerEvent's flat ObjectNode for a debit (isDebit = true).
 * NOTE: amount is a STRING (amount.toPlainString()), not a JSON number.
 * NOTE: no headers are asserted because the producer sends none.
 */
org.springframework.cloud.contract.spec.Contract.make {
    description("Should forward a ledger transaction that exhausted its retries to ledger-events-dlq, unchanged, so WalletService can credit the advertiser back")
    label("ledger_events_dlq")
    input { triggeredBy('fireLedgerDlqEvent()') }
    outputMessage {
        sentTo('ledger-events-dlq')
        body([
            transferId: $(producer(regex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))),
            amount: "250.00",
            chargeCategory: "AD_CLICK",
            fromId: $(producer(regex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))),
            fromType: "ADVERTISER_WALLET",
            toId: $(producer(regex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))),
            toType: "PLATFORM"
        ])
    }
}
