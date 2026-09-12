package com.thc.goornotdev.service.Impl;

import com.thc.goornotdev.DTO.DefaultDto;
import com.thc.goornotdev.DTO.UserDto;
import com.thc.goornotdev.domain.User;
import com.thc.goornotdev.exception.DuplicateDataException;
import com.thc.goornotdev.exception.NoMatchingDataException;
import com.thc.goornotdev.mapper.UserMapper;
import com.thc.goornotdev.repository.UserRepository;
import com.thc.goornotdev.security.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthService authService;

    @InjectMocks
    private UserServiceImpl userService;

    private UserDto.CreateReqDto createReqDto() {
        return UserDto.CreateReqDto.builder()
                .username("tester")
                .password("password123")
                .name("조상근")
                .email("test@example.com")
                .build();
    }

    @Test
    @DisplayName("회원가입 - 비밀번호가 암호화되어 저장된다")
    void create_encodesPassword() {
        given(userRepository.existsByUsername("tester")).willReturn(false);
        given(userRepository.existsByEmail("test@example.com")).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("encodedPassword");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        userService.create(createReqDto(), null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("encodedPassword");
        assertThat(captor.getValue().getUsername()).isEqualTo("tester");
    }

    @Test
    @DisplayName("회원가입 - 아이디가 중복되면 DuplicateDataException")
    void create_duplicateUsername() {
        given(userRepository.existsByUsername("tester")).willReturn(true);

        assertThatThrownBy(() -> userService.create(createReqDto(), null))
                .isInstanceOf(DuplicateDataException.class)
                .hasMessageContaining("tester");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("회원가입 - 이메일이 중복되면 DuplicateDataException")
    void create_duplicateEmail() {
        given(userRepository.existsByUsername("tester")).willReturn(false);
        given(userRepository.existsByEmail("test@example.com")).willReturn(true);

        assertThatThrownBy(() -> userService.create(createReqDto(), null))
                .isInstanceOf(DuplicateDataException.class)
                .hasMessageContaining("test@example.com");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("정보 수정 - 비밀번호는 재암호화되어 저장된다")
    void update_encodesPassword() {
        User user = User.of("tester", "oldEncoded", "조상근", "test@example.com", null, null);
        user.setId(1L);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.encode("newPassword")).willReturn("newEncoded");

        userService.update(UserDto.UpdateReqDto.builder()
                .id(1L)
                .password("newPassword")
                .build(), 1L);

        assertThat(user.getPassword()).isEqualTo("newEncoded");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("정보 수정 - 타인의 정보를 수정하려 하면 AccessDeniedException")
    void update_otherUser() {
        assertThatThrownBy(() -> userService.update(UserDto.UpdateReqDto.builder()
                .id(2L)
                .name("해커")
                .build(), 1L))
                .isInstanceOf(AccessDeniedException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("삭제 - Soft Delete 처리 후 Refresh Token 을 폐기한다")
    void delete_softDeletesAndRevokesToken() {
        User user = User.of("tester", "encoded", "조상근", "test@example.com", null, null);
        user.setId(1L);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        userService.delete(UserDto.UpdateReqDto.builder().id(1L).build(), 1L);

        assertThat(user.getDeleted()).isTrue();
        verify(userRepository).save(user);
        verify(authService).revokeRefreshToken(1L);
    }

    @Test
    @DisplayName("상세 조회 - 대상이 없으면 NoMatchingDataException")
    void detail_notFound() {
        given(userMapper.detail(1L)).willReturn(null);

        assertThatThrownBy(() -> userService.detail(DefaultDto.DetailReqDto.builder().id(1L).build(), 1L))
                .isInstanceOf(NoMatchingDataException.class);
    }

    @Test
    @DisplayName("상세 조회 - 타인의 정보를 조회하려 하면 AccessDeniedException")
    void detail_otherUser() {
        assertThatThrownBy(() -> userService.detail(DefaultDto.DetailReqDto.builder().id(2L).build(), 1L))
                .isInstanceOf(AccessDeniedException.class);

        verify(userMapper, never()).detail(any());
    }

    @Test
    @DisplayName("목록 조회 - Mapper 가 반환한 id 로 상세를 채운다")
    void list_fillsDetailById() {
        UserDto.ListReqDto param = UserDto.ListReqDto.builder().build();
        UserDto.DetailResDto idOnly = UserDto.DetailResDto.builder().id(1L).build();
        UserDto.DetailResDto detail = UserDto.DetailResDto.builder().id(1L).username("tester").build();

        given(userMapper.list(param)).willReturn(List.of(idOnly));
        given(userMapper.detail(1L)).willReturn(detail);

        List<UserDto.DetailResDto> result = userService.list(param, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsername()).isEqualTo("tester");
    }

    @Test
    @DisplayName("목록 조회 - 결과에 타인이 섞여 있으면 AccessDeniedException")
    void list_containsOtherUser() {
        UserDto.ListReqDto param = UserDto.ListReqDto.builder().name("조").build();
        UserDto.DetailResDto idOnly = UserDto.DetailResDto.builder().id(2L).build();

        given(userMapper.list(param)).willReturn(List.of(idOnly));

        assertThatThrownBy(() -> userService.list(param, 1L))
                .isInstanceOf(AccessDeniedException.class);
    }
}
