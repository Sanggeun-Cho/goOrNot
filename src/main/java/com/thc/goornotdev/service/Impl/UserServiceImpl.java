package com.thc.goornotdev.service.Impl;

import com.thc.goornotdev.DTO.DefaultDto;
import com.thc.goornotdev.DTO.UserDto;
import com.thc.goornotdev.domain.User;
import com.thc.goornotdev.exception.DuplicateDataException;
import com.thc.goornotdev.exception.NoMatchingDataException;
import com.thc.goornotdev.mapper.UserMapper;
import com.thc.goornotdev.repository.UserRepository;
import com.thc.goornotdev.security.AuthService;
import com.thc.goornotdev.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    @Override
    @Transactional
    public DefaultDto.CreateResDto create(UserDto.CreateReqDto param, Long reqUserId) {
        if (userRepository.existsByUsername(param.getUsername())) {
            throw new DuplicateDataException("username : " + param.getUsername());
        }
        if (userRepository.existsByEmail(param.getEmail())) {
            throw new DuplicateDataException("email : " + param.getEmail());
        }

        // bCrypt 를 통한 비밀번호 암호화
        param.setPassword(passwordEncoder.encode(param.getPassword()));

        User newUser = userRepository.save(param.toEntity());

        return newUser.toCreateResDto();
    }

    @Override
    @Transactional
    public void update(UserDto.UpdateReqDto param, Long reqUserId) {
        // 사용자가 특정되지 않으면 요청한 사용자의 정보를 수정
        if (param.getId() == null) {
            param.setId(reqUserId);
        }

        // 타인의 정보를 바꾸려는 시도는 차단
        if (!param.getId().equals(reqUserId)) {
            throw new AccessDeniedException("본인의 정보만 수정할 수 있습니다.");
        }

        User user = userRepository.findById(param.getId())
                .orElseThrow(() -> new NoMatchingDataException("id : " + param.getId()));

        // 평문이 그대로 저장되지 않도록 수정 시에도 반드시 암호화
        if (param.getPassword() != null) {
            param.setPassword(passwordEncoder.encode(param.getPassword()));
        }

        // 이메일 변경 시 중복 검사
        if (param.getEmail() != null && !param.getEmail().equals(user.getEmail())
                && userRepository.existsByEmail(param.getEmail())) {
            throw new DuplicateDataException("email : " + param.getEmail());
        }

        user.update(param);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void delete(UserDto.UpdateReqDto param, Long reqUserId) {
        Long targetUserId = (param.getId() == null) ? reqUserId : param.getId();

        // 타인의 계정을 탈퇴시키려는 시도는 차단
        if (!targetUserId.equals(reqUserId)) {
            throw new AccessDeniedException("본인의 계정만 탈퇴할 수 있습니다.");
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new NoMatchingDataException("id : " + targetUserId));

        // Soft Delete + 개인정보 익명화.
        // 아이디·이메일이 비워지므로 같은 값으로 재가입할 수 있다
        user.withdraw();
        userRepository.save(user);

        // 탈퇴한 계정의 Refresh Token 이 남아 재발급되지 않도록 폐기
        authService.revokeRefreshToken(targetUserId);
    }

    // Mapper 를 이용한 사용자 정보 조회 함수
    public UserDto.DetailResDto get(DefaultDto.DetailReqDto param, Long reqUserId) {
        // 타인의 정보를 조회하려는 시도는 차단 (커뮤니티 기능 도입 전까지는 본인 정보만 조회 가능)
        if (!param.getId().equals(reqUserId)) {
            throw new AccessDeniedException("본인의 정보만 조회할 수 있습니다.");
        }

        UserDto.DetailResDto user = userMapper.detail(param.getId());

        if (user == null) {
            throw new NoMatchingDataException("id : " + param.getId());
        }

        return user;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto.DetailResDto detail(DefaultDto.DetailReqDto param, Long reqUserId) {
        // 사용자가 특정되지 않으면 요청한 사용자의 정보를 조회
        if (param.getId() == null) {
            param.setId(reqUserId);
        }

        return get(param, reqUserId);
    }

    // Mapper 를 통해 받은 사용자 리스트의 ID 값을 이용해 상세 객체 리스트로 채움
    public List<UserDto.DetailResDto> addList(List<UserDto.DetailResDto> list, Long reqUserId) {
        List<UserDto.DetailResDto> newList = new ArrayList<>();

        for (UserDto.DetailResDto user : list) {
            newList.add(get(DefaultDto.DetailReqDto.builder()
                    .id(user.getId())
                    .build(), reqUserId));
        }

        return newList;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDto.DetailResDto> list(UserDto.ListReqDto param, Long reqUserId) {
        // 클라이언트가 보낸 소유자 조건은 신뢰하지 않는다.
        // 요청자 ID 로 덮어써 SQL 단계에서 본인 것만 조회되도록 강제한다
        param.setUserId(reqUserId);

        return addList(userMapper.list(param), reqUserId);
    }
}
