package contracts
import org.springframework.cloud.contract.spec.Contract
/*
 * Consumed by CustomerApplication LedgerClient.getCategoryTotal: the restaurant earnings summary reads
 * an outlet's clawbacks (CLAWBACK debits on its RESTAURANT_PAYABLE account) for the outlet's day, week
 * or month. The window is two ISO-8601 UTC instants, [from, to).
 *
 * The body is the sum; DoubleEntryLedgerServiceCategoryTotalTest pins what is summed.
 */
Contract.make {
    description("the total one owner's account moved under one category in [from, to)")
    request {
        method 'GET'
        urlPath(value(
                consumer(regex('/api/v1/internal/ledger/accounts/RESTAURANT_PAYABLE/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}/totals')),
                producer('/api/v1/internal/ledger/accounts/RESTAURANT_PAYABLE/0c9a8b7d-1e2f-4a3b-8c4d-5e6f7a8b9c01/totals'))) {
            queryParameters {
                parameter 'category': 'CLAWBACK'
                parameter 'direction': 'DEBIT'
                parameter 'from': value(consumer(matching('[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]+)?Z')), producer('2026-10-18T23:00:00Z'))
                parameter 'to': value(consumer(matching('[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]+)?Z')), producer('2026-10-26T00:00:00Z'))
            }
        }
    }
    response {
        status 200
        headers {
            contentType(applicationJson())
        }
        body(42.50)
    }
}
