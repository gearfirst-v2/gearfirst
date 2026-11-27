package com.gearfirst.user_be.user.service;

import com.gearfirst.user_be.common.context.UserContext;
import com.gearfirst.user_be.common.exception.ForbiddenException;
import com.gearfirst.user_be.common.response.ErrorStatus;
import com.gearfirst.user_be.user.enums.Rank;

/**
 * 권한표의 허용 규칙 (GEA-15, B1·B2). API별 적용은 UserService, 표 전체 검증은 UserAuthorizationMatrixTest.
 * 표는 docs/security/B1-B2-user-authz.md. 호출자 정보는 gateway가 토큰 클레임으로 넣은 X-User-* 헤더이고,
 * 직급·근무유형은 한글 이름으로 온다.
 */
final class UserAuthorization {

    static final String HEAD_OFFICE = "본사";

    private UserAuthorization() {}

    /** 계정 생성·수정·삭제: 본사 팀장만 */
    static void requireHeadOfficeLeader(UserContext caller) {
        if (!isHeadOffice(caller) || !Rank.LEADER.getStatus().equals(caller.getRank())) {
            throw forbidden();
        }
    }

    /** 직원 목록·남의 정보 조회: 본사(팀장·사원) */
    static void requireHeadOffice(UserContext caller) {
        if (!isHeadOffice(caller)) {
            throw forbidden();
        }
    }

    static boolean isSelf(UserContext caller, Long userId) {
        return String.valueOf(userId).equals(caller.getUserId());
    }

    private static boolean isHeadOffice(UserContext caller) {
        return HEAD_OFFICE.equals(caller.getWorkType());
    }

    private static ForbiddenException forbidden() {
        return new ForbiddenException(ErrorStatus.FORBIDDEN_ACCESS.getMessage());
    }
}
