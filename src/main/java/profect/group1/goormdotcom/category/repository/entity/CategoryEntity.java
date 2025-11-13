package profect.group1.goormdotcom.category.repository.entity;

import jakarta.persistence.*;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Locale.Category;

import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import profect.group1.goormdotcom.common.domain.BaseEntity;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor

@Entity
@Table(name = "p_category")
@SQLRestriction("deleted_at IS NULL")
@SQLDelete(sql = "update p_category set deleted_at = NOW() where id = ?")
@EntityListeners(AuditingEntityListener.class)
public class CategoryEntity extends BaseEntity{

    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(columnDefinition = "uuid", nullable = false)
    private UUID id;
    private UUID parentId;
    private String name;
    private LocalDateTime deletedAt;

    public CategoryEntity(
        UUID parentId,
        String name
    ) {
        this.parentId = parentId;
        this.name = name;
    }

    public CategoryEntity(UUID id, UUID parentId, String name) {
        this.id = id;
        this.parentId = parentId;
        this.name = name;
    }

    public void updateName(String newName) {
        this.name = newName;
    }
}
