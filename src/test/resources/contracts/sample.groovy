package contracts
import org.springframework.cloud.contract.spec.Contract
Contract.make {
    request {
        method 'GET'
        url '/api/v1/ledger/payouts/pending'
    }
    response {
        status 200
        headers {
            contentType(applicationJson())
        }
        body([])
    }
}
