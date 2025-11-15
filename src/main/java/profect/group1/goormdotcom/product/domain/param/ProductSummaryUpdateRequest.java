package profect.group1.goormdotcom.product.domain.param;

import lombok.Builder;

@Builder
public record ProductSummaryUpdateRequest(
        String name,
        int price,
        String mainImageUrl,
        String status
) {
}
