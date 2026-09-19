package com.thc.goornotdev.domain;

import com.thc.goornotdev.DTO.DefaultDto;
import com.thc.goornotdev.DTO.UserDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Entity @Getter
@Table(name = "users")
public class User extends AuditingFields {
    /** 탈퇴 계정 식별자 */
    private static final String WITHDRAWN_PREFIX = "withdrawn_";
    private static final DateTimeFormatter WITHDRAWN_SUFFIX = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /**
     * 로그인 ID (Unique)
     */
    @Setter
    @Column(nullable = false, unique = true)
    String username;

    /**
     * 로그인 비밀번호 (Encrypted 로 관리)
     */
    @Setter
    @Column(nullable = false)
    String password;

    /**
     * 사용자 이름
     */
    @Setter
    String name;

    /**
     * 사용자 이메일 (Unique, 선택 입력)
     *
     * [2026-09-18] 필수 → 선택으로 내렸다.
     * 비밀번호 찾기·알림 메일 같은 "이메일을 실제로 쓰는 기능" 이 아직 없어서,
     * 받아두면 쓰지도 않는 개인정보를 보관만 하게 된다(개인정보보호법 제3조 최소수집).
     * 회원가입 화면에서도 묻지 않는다. 메일을 쓰는 기능이 생기면 그때 다시 받는다.
     *
     * unique 는 유지한다. 나중에 받기 시작할 때 중복이 쌓여 있으면 곤란하다.
     * (MySQL 유니크 인덱스는 NULL 이 여러 개 들어가는 것을 허용한다)
     */
    @Setter
    @Column(unique = true)
    String email;

    /**
     * 사용자 추가 정보
     */
    @Setter
    String phone;
    @Setter
    String birth;

    protected User() {}
    private User(String username, String password, String name, String email, String phone, String birth) {
        this.username = username;
        this.password = password;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.birth = birth;
    }

    public static User of (String username, String password, String name, String email, String phone, String birth) {
        return new User(username, password, name, email, phone, birth);
    }

    /**
     * 사용자 정보 수정
     * 수정 가능 항목 : 비밀번호, 이름, 이메일, 휴대폰번호, 생일
     */
    public void update(UserDto.UpdateReqDto param){
        if(param.getDeleted() != null){
            setDeleted(param.getDeleted());
        }
        if(param.getPassword() != null){
            setPassword(param.getPassword());
        }
        if(param.getName() != null){
            setName(param.getName());
        }
        if(param.getEmail() != null){
            setEmail(param.getEmail());
        }
        if(param.getPhone() != null){
            setPhone(param.getPhone());
        }
        if(param.getBirth() != null){
            setBirth(param.getBirth());
        }
    }

    public void delete(){
        update(UserDto.UpdateReqDto.builder()
                .deleted(true)
                .build());
    }

    /**
     * 탈퇴 처리. Soft Delete + 개인정보 익명화.
     *
     * 행 자체는 남겨 통계·정산 기록이 끊기지 않게 하되, 식별 가능한 값은 전부 지운다.
     * username / email 이 비워지므로 같은 아이디·이메일로 다시 가입할 수 있고,
     * 탈퇴한 계정의 개인정보가 DB 에 평문으로 계속 남는 문제도 함께 해결된다.
     */
    public void withdraw(){
        // 탈퇴 시각을 붙여 유니크 제약과 충돌하지 않게 한다.
        // 회원가입 최대 길이(20자)를 넘겨 살아있는 계정의 아이디와는 겹칠 수 없다
        String tag = getId() + "_" + LocalDateTime.now().format(WITHDRAWN_SUFFIX);

        setUsername(WITHDRAWN_PREFIX + tag);

        // 이메일은 덮어쓰지 않고 아예 비운다. 남겨 둘 이유가 없는 개인정보다.
        // (유니크 제약은 NULL 을 여러 개 허용하므로 같은 메일로 재가입할 수 있다)
        setEmail(null);

        // 남은 해시로 로그인할 수 없도록 비밀번호는 검증 불가능한 값으로 덮는다
        setPassword(UUID.randomUUID().toString());

        setName(null);
        setPhone(null);
        setBirth(null);

        setDeleted(true);
    }

    public DefaultDto.CreateResDto toCreateResDto(){
        return DefaultDto.CreateResDto.builder()
                .id(getId())
                .build();
    }
}
