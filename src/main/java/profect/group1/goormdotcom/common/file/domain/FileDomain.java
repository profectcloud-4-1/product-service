package profect.group1.goormdotcom.common.file.domain;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FileDomain {
    REVIEW("review"),
    PRODUCT("product");

    private final String path;
}
