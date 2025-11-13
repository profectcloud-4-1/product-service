package profect.group1.goormdotcom.product.domain;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum ProductStatus {
    AVAILABLE("PRD0001", "AVAILABLE", "판매 가능"),
    SOLD_OUT("PRD0002", "SOLD_OUT", "품절"),
    NOT_EXIST("PRD0003", "NOT_EXIST", "존재하지 않음");

    public static final String CODE_KEY = "PRODUCT_STATUS";

    private final String code;      // e.g., PRD0001
    private final String value;     // e.g., AVAILABLE
    private final String label;     // e.g., 판매 가능

    ProductStatus(String code, String value, String label) {
        this.code = code;
        this.value = value;
        this.label = label;
    }

    public boolean isPurchasable() {
        return this == AVAILABLE;
    }

    public boolean isVisible() {
        return this != NOT_EXIST;
    }

    public static Optional<ProductStatus> fromCode(String code) {
        if (code == null) return Optional.empty();
        return Arrays.stream(values()).filter(s -> s.code.equals(code)).findFirst();
    }

    public static Optional<ProductStatus> fromValue(String value) {
        if (value == null) return Optional.empty();
        return Arrays.stream(values()).filter(s -> s.value.equalsIgnoreCase(value)).findFirst();
    }
}
