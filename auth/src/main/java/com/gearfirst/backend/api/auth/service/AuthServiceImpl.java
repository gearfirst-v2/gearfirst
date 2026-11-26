package com.gearfirst.backend.api.auth.service;

import com.gearfirst.backend.api.auth.dto.ChangePasswordRequest;
import com.gearfirst.backend.api.auth.dto.CreateAccount;
import com.gearfirst.backend.api.auth.entity.Auth;
import com.gearfirst.backend.api.auth.repository.AuthRepository;
import com.gearfirst.backend.api.mail.MailService;
import com.gearfirst.backend.common.exception.BadRequestException;
import com.gearfirst.backend.common.exception.BaseException;
import com.gearfirst.backend.common.exception.KnownBusinessException;
import com.gearfirst.backend.common.exception.NotFoundException;
import com.gearfirst.backend.common.response.ErrorStatus;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final PasswordEncoder passwordEncoder;
    private final AuthRepository authRepository;
    private final MailService mailService;

    @Transactional
    @Override
    public void createAccount(CreateAccount request) {
        String tempPassword = RandomStringUtils.random(10, 0, 0, true, true, null, new SecureRandom());
        String encodedPassword = passwordEncoder.encode(tempPassword);
        // log.info("tempPassword: {}", tempPassword);

        // 이메일 중복 체크
        if (authRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new KnownBusinessException(ErrorStatus.DUPLICATE_EMAIL_EXCEPTION.getMessage());
        }
        if (request.getUserId() == null) {
            throw new KnownBusinessException(ErrorStatus.ESSENTIAL_USERID_EXCEPTION.getMessage());
        }

        Auth auth = Auth.builder()
                .userId(request.getUserId())
                .email(request.getEmail())
                .password(encodedPassword)
                .personalEmail(request.getPersonalEmail())
                .build();
        authRepository.save(auth);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        mailService.sendUserRegistrationMail(request.getPersonalEmail(), tempPassword);
                    } catch (Exception e) {
                        log.error(
                                "메일 발송 실패 - personalEmail={}, message={}",
                                request.getPersonalEmail(),
                                e.getMessage(),
                                e);
                    }
                }
            });
        } else {
            mailService.sendUserRegistrationMail(request.getPersonalEmail(), tempPassword);
        }
    }

    // 틀린 비밀번호(BadRequestException)에도 실패 횟수는 저장돼야 해서 롤백하지 않는다
    @Transactional(noRollbackFor = BadRequestException.class)
    @Override
    public void changePassword(Long userId, ChangePasswordRequest request) {
        Auth auth = authRepository
                .findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(ErrorStatus.NOT_FOUND_USER_EXCEPTION.getMessage()));
        LocalDateTime now = LocalDateTime.now();
        // 401이면 프론트가 토큰을 갱신해 재시도해서 실패가 두 번 세진다. 그래서 400, 423을 쓴다
        if (auth.isPasswordChangeLocked(now)) {
            throw new BaseException(HttpStatus.LOCKED, "비밀번호를 여러 번 틀려 잠시 변경할 수 없습니다. 15분 뒤 다시 시도해주세요.");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new KnownBusinessException("새 비밀번호와 비밀번호 확인이 일치하지 않습니다.");
        }
        if (!auth.matchesPassword(request.getCurrentPassword(), passwordEncoder)) {
            auth.recordPasswordChangeFailure(now);
            throw new BadRequestException("현재 비밀번호가 잘못되었습니다. 다시 입력해주세요.");
        }
        auth.resetPasswordChangeFailures();
        auth.changePassword(request.getNewPassword(), passwordEncoder);
    }

    @Transactional
    @Override
    public void regenerateTempPassword(String email) {
        Auth auth = authRepository
                .findByEmail(email)
                .orElseThrow(() -> new NotFoundException(ErrorStatus.NOT_FOUND_USER_EXCEPTION.getMessage()));
        String personalEmail = auth.getPersonalEmail();
        if (personalEmail == null) {
            throw new KnownBusinessException(ErrorStatus.NOT_REGISTERED_PERSONAL_EMAIL_EXCEPTION.getMessage());
        }
        // 새 임시 비밀번호 생성
        String newTempPassword = RandomStringUtils.random(10, 0, 0, true, true, null, new SecureRandom());
        String encoded = passwordEncoder.encode(newTempPassword);

        // 비밀번호 갱신
        auth.updatePassword(encoded);
        authRepository.save(auth);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    mailService.sendUserRegistrationMail(personalEmail, newTempPassword);
                } catch (Exception e) {
                    throw new IllegalStateException("메일 발송 중 오류가 발생했습니다: " + e.getMessage());
                }
            }
        });
    }

    @Transactional
    @Override
    public void deleteUser(Long userId) {
        Auth auth = authRepository
                .findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(ErrorStatus.NOT_FOUND_USER_EXCEPTION.getMessage()));

        authRepository.delete(auth);
    }
}
