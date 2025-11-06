package profect.group1.goormdotcom.common.dto;

import lombok.Getter;

import java.util.UUID;

@Getter
public class UserContext {

    private final UUID userId;
    private final String role;

    public UserContext(UUID userId, String role) {
        this.userId = userId;
        this.role = role;
    }

    public UUID getId() {
        return userId;
    }
}