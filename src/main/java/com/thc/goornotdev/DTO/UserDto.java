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

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        String password;

        String name; // 사용자 실명

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        String email;

        String phone;
        String birth;

        public User toEntity(){
            return User.of(getUsername(), getPassword(), getName(), getEmail(), getPhone(), getBirth());
        }
    }

    /**
     * REQUEST
     * 사용자 정보 수정 데이터
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class UpdateReqDto extends DefaultDto.UpdateReqDto {
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
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
         * 검색 조건 : 사용자 이름 (중간 글자 검색 가능)
         */
        String name;
    }
}
