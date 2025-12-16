package contracts.product

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "상품 조회 응답(ApiResponse<ProductResponseDto>) - 성공"

    request {
        method GET()
        urlPath($(consumer(regex('/api/v1/product/[0-9a-fA-F-]{36}')),
                producer('/api/v1/product/00000000-0000-0000-0000-000000000000')))
    }

    response {
        status OK()
        headers {
            contentType(applicationJson())
        }

        body(
                code: $(consumer(anyNonBlankString()), producer('200')),
                message: $(consumer(anyNonBlankString()), producer('OK')),
                result: [
                        name: $(consumer(anyNonBlankString()), producer('테스트 상품')),
                        brandId: $(consumer(anyUuid()), producer('11111111-1111-1111-1111-111111111111')),
                        categoryId: $(consumer(anyUuid()), producer('22222222-2222-2222-2222-222222222222')),
                        description: $(consumer(anyNonBlankString()), producer('상품 설명입니다')),
                        price: $(consumer(anyNumber()), producer(10000)),
                        imageIds: $(consumer(any()), producer(['img-1', 'img-2']))
                ]
        )

        bodyMatchers {
            jsonPath('$.code', byType())
            jsonPath('$.message', byType())

            jsonPath('$.result.name', byType())
            jsonPath('$.result.brandId', byRegex('[0-9a-fA-F-]{36}'))
            jsonPath('$.result.categoryId', byRegex('[0-9a-fA-F-]{36}'))
            jsonPath('$.result.description', byType())
            jsonPath('$.result.price', byType())
            jsonPath('$.result.imageIds', byType())
            jsonPath('$.result.imageIds[0]', byType())
        }
    }
}
