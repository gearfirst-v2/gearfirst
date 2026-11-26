package com.gearfirst.backend.api.auth.entity;

import com.gearfirst.backend.common.entity.BaseTimeEntity;
import com.gearfirst.backend.common.exception.KnownBusinessException;
import jakarta.persistence.*;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.*;
import org.springframework.security.crypto.password.PasswordEncoder;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "auth")
public class Auth extends BaseTimeEntity {
    private static final int MAX_PASSWORD_CHANGE_FAILURES = 5;
    private static final Duration PASSWORD_CHANGE_LOCK = Duration.ofMinutes(15);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "auth_id")
    private Long authId;

    @Column(name = "user_id", unique = true)
    private Long userId;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    // 임시 비밀번호를 받을 주소. 재발급 때 요청 값이 아니라 이 값으로만 보낸다(C1)
    @Column(name = "personal_email", length = 100)
    private String personalEmail;

    @Enumerated(EnumType.STRING)
    private AuthStatus status;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt; // 마지막 로그인 시간

    @Column(name = "is_first_login")
    private Boolean isFirstLogin; // 첫 로그인 여부

    // change-password 전용 연속 실패 횟수와 잠금 끝 시각(C3). 칼럼 추가 전 계정은 null이라 0으로 본다
    @Column(name = "failed_count")
    private Integer failedCount;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.isFirstLogin = true;
        this.status = this.status == null ? AuthStatus.ACTIVE : this.status;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void updateLastLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    public boolean matchesPassword(String rawPassword, PasswordEncoder encoder) {
        return encoder.matches(rawPassword, this.password);
    }

    public boolean isPasswordChangeLocked(LocalDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    // shortcut: 읽고 +1 해서 저장하므로 동시 요청이 서로 덮어쓴다(C7). k6 기준선 뒤 C7에서 고친다
    public void recordPasswordChangeFailure(LocalDateTime now) {
        failedCount = (failedCount == null ? 0 : failedCount) + 1;
        if (failedCount >= MAX_PASSWORD_CHANGE_FAILURES) {
            lockedUntil = now.plus(PASSWORD_CHANGE_LOCK);
            failedCount = 0;
        }
    }

    public void resetPasswordChangeFailures() {
        failedCount = 0;
        lockedUntil = null;
    }

    public void changePassword(String newPassword, PasswordEncoder encoder) {
        this.password = encoder.encode(newPassword);
    }

    public void updatePassword(String newEncodedPassword) {
        this.password = newEncodedPassword;
    }

    public void linkToUser(Long userId) {
        if (this.userId != null) {
            throw new KnownBusinessException("이미 userId가 등록된 계정입니다.");
        }
        this.userId = userId;
    }
}
