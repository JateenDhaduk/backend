package com.example.jwtRefreshdemo.repoitory;

import com.example.jwtRefreshdemo.entity.RefreshToken;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @Query(value = "SELECT * FROM refresh_tokens WHERE token_hash = :tokenHash LIMIT 1",
            nativeQuery = true)
    Optional<RefreshToken> findByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @Transactional
    @Query(value = "UPDATE refresh_tokens SET is_revoked = true WHERE family_id = :familyId",
            nativeQuery = true)
    void revokeAllByFamilyId(@Param("familyId") String familyId);

    @Modifying
    @Transactional
    @Query(value = "UPDATE refresh_tokens SET is_revoked = true WHERE user_id = :userId",
            nativeQuery = true)
    void revokeAllByUserId(@Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM refresh_tokens WHERE expires_at < :now OR is_revoked = true",
            nativeQuery = true)
    void deleteExpiredAndRevoked(@Param("now") LocalDateTime now);
}