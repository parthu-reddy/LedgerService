package contracts
import org.springframework.cloud.contract.spec.Contract
Contract.make {
    request {
        method 'GET'
        urlPath('/api/v1/ledger/orders/ORDER-123/total')
    }
    response {
        status 200
        headers {
            contentType(applicationJson())
        }
        body(100.50)
    }
}
