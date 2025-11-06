package profect.group1.goormdotcom.presigned.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import profect.group1.goormdotcom.presigned.domain.FileStatus;
import profect.group1.goormdotcom.presigned.repository.entity.FileUpload;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileUploadRepository extends JpaRepository<FileUpload, UUID> {
    Optional<FileUpload> findByObjectKey(String objectKey);

    @Query("SELECT f FROM FileUpload f WHERE f.status = :status " +
            "AND f.createdAt < :expirationTime AND f.deletedAt IS NULL")
    List<FileUpload> findExpiredTempFiles(
            @Param("status") FileStatus status,
            @Param("expirationTime") LocalDateTime expirationTime
    );
}
