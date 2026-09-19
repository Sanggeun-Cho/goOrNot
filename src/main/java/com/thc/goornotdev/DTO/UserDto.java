package com.thc.goornotdev.DTO;

import com.thc.goornotdev.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

public class UserDto {
    /**
     * REQUEST
     * 로그인 데이터
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class LoginReqDto {
        String username;
        String password;
    }

    /**
     * REQUEST
     * 사용자 생성 데이터 (신규 회원가입)
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateReqDto {
        @NotBlank(message = "아이디는 필수입니다.")
        @Size(min = 4, max = 20, message = "아이디는 4~20자여야 합니다.")
        String username; // 로그인 ID

        /**
         * 최대 길이를 두는 이유는 두 가지다.
         *  1. BCrypt 는 72바이트까지만 보고 나머지를 버린다. 그보다 긴 비밀번호는
         *     뒷부분이 검증에 쓰이지 않아 사용자가 착각하게 된다.
         *  2. 길이 제한이 없으면 수 MB 짜리 문자열을 보내 해시 계산을 강제하는
         *     느린 요청(DoS)을 만들 수 있다.
         */
        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
        String password;

        String name; // 사용자 실명

        /**
         * 선택 입력. 실제로 메일을 쓰는 기능이 없어 회원가입 화면에서는 묻지 않는다.
         * (User.email 주석 참고 — 값이 없으면 null 로 저장한다)
         */
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        String email;

        String phone;
        String birth;

        public User toEntity(){
            return User.of(getUsername(), getPassword(), getName(), blankToNull(getEmail()),
                    getPhone(), getBirth());
        }

        /**
         * 빈 문자열을 NULL 로 바꾼다.
         *
         * email 은 유니크 컬럼이라 "" 로 저장하면 두 번째 가입자부터 중복으로 막힌다.
         * "값이 없다" 는 상태는 한 가지 방법(NULL)으로만 표현한다.
         */
        private static String blankToNull(String value) {
            return (value == null || value.isBlank()) ? null : value;
        }
    }

    /**
     * REQUEST
     * 사용자 정보 수정 데이터
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class UpdateReqDto extends DefaultDto.UpdateReqDto {
        // 상한을 두는 이유는 CreateReqDto.password 주석 참고 (BCrypt 72바이트 절삭 + 긴 입력 방어)
        @Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
        String password;

        String name;

        @Email(message = "올바른 이메일 형식이 아닙니다.")
        String email;

        String phone;
        String birth;
    }

    /**
     * RESPONSE
     * 사용자 상세 데이터
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class DetailResDto extends DefaultDto.DetailResDto {
        String username;
        String name;
        String email;
        String phone;
        String birth;
    }

    /**
     * REQUEST
     * 사용자 목록 조회 시 검색 데이터
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class ListReqDto extends DefaultDto.ListReqDto {
        /**
         * 조회 대상 사용자 ID.
         * 클라이언트가 보낸 값은 쓰지 않는다. 서비스에서 요청자 ID 로 덮어써 본인 것만 조회되도록 강제한다.
         */
        Long userId;

        /**
         * 검색 조건 : 사용자 이름 (중간 글자 검색 가능)
         */
        String name;
    }
}
