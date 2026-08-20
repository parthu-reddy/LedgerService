package contracts
import org.springframework.cloud.contract.spec.Contract
/*
 * Consumed by ONDCIntegrationService.LedgerServiceClient.getOrderLedgerAmount, which
 * ReconciliationService compares against a counterparty's reported amount during ONDC settlement.
 *
 * The path variable is the order's REFERENCE UUID. It was previously the literal 'ORDER-123', which
 * is not a UUID and could never have matched -- LedgerEntry.referenceId is a UUID and there is no
 * business-key lookup. The Feign signature is String, so the consumer needs no change.
 *
 * The body is the sum of CREDIT entries against the PLATFORM account for that reference. See
 * DoubleEntryLedgerService.getOrderLedgerTotal for why, including that it is gross of refunds.
 */
Contract.make {
    request {
        method 'GET'
        urlPath('/api/v1/ledger/orders/3f2504e0-4f89-41d3-9a0c-0305e82c3301/total')
    }
    response {
        status 200
        headers {
            contentType(applicationJson())
        }
        body(100.50)
    }
}
