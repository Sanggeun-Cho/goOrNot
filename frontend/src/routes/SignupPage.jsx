import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';

import { signup } from '../lib/auth.js';

/**
 * 회원가입.
 *
 * 와이어프레임에는 화면 5(로그인)의 "회원가입" 링크만 있고 화면 자체는 없어서 새로 잡았다.
 * 서버 제약(UserDto.CreateReqDto)에 맞춘 필수 항목만 받는다.
 *   username 4~20자 · password 8~64자 · name
 *
 * email / phone / birth 는 엔티티에는 있지만 서비스 어디에서도 쓰지 않아서 묻지 않는다.
 * 쓰지도 않을 개인정보를 받아두면 보관할 이유 없는 것을 보관하게 된다.
 * (email 은 [2026-09-18] 결정으로 선택 입력이 됐다. 비밀번호 찾기를 붙일 때 다시 받는다)
 */
const INITIAL = { username: '', password: '', passwordConfirm: '', name: '' };

/** 서버 @Size(min=8, max=64) 와 짝. 상한 이유는 UserDto.CreateReqDto 주석 참고 */
const PASSWORD_MIN = 8;
const PASSWORD_MAX = 64;

export default function SignupPage() {
    const navigate = useNavigate();
    const location = useLocation();

    /*
     * 지역 확정 게이트에서 "회원가입" 을 눌러 넘어온 경우 로그인 화면이 들려준 정보다.
     * 가입만 하고 로그인 화면으로 넘길 때 그대로 다시 넘겨야 정해둔 지역으로 돌아간다.
     */
    const gate = {
        from: location.state?.from,
        linkSession: location.state?.linkSession ?? null,
    };

    const [values, setValues] = useState(INITIAL);
    const [fieldErrors, setFieldErrors] = useState({});
    const [error, setError] = useState('');
    const [submitting, setSubmitting] = useState(false);

    function update(key, value) {
        setValues((prev) => ({ ...prev, [key]: value }));

        // 고치기 시작하면 그 칸의 에러는 지운다. 다 고쳤는데 빨간 글씨가 남아 있으면 혼란스럽다
        if (fieldErrors[key]) {
            setFieldErrors((prev) => ({ ...prev, [key]: undefined }));
        }
    }

    async function handleSubmit(event) {
        event.preventDefault();
        if (submitting) return;

        setError('');
        setFieldErrors({});

        const weak = passwordProblem(values.password, values.username);

        if (weak) {
            setFieldErrors({ password: weak });
            return;
        }

        // 비밀번호 확인은 서버가 모르는 값이라 여기서만 검사한다
        if (values.password !== values.passwordConfirm) {
            setFieldErrors({ passwordConfirm: '비밀번호가 일치하지 않습니다.' });
            return;
        }

        setSubmitting(true);

        try {
            // passwordConfirm 은 서버가 모르는 값이라 보내지 않는다
            const { passwordConfirm, ...payload } = values;
            await signup(payload);

            // 가입 직후 자동 로그인하지 않는다. 방금 정한 비밀번호를 한 번 쳐보게 하는 편이
            // 오타로 가입해 놓고 못 들어오는 경우를 바로 잡아준다
            navigate('/login', { replace: true, state: { ...gate, signedUp: true } });
        } catch (caught) {
            // validation 실패는 { 필드명: 메시지 } 로 온다 (GlobalExceptionHandler)
            if (caught.fieldErrors) {
                setFieldErrors(caught.fieldErrors);
            } else if (caught.status === 409) {
                const duplicated = duplicateFieldError(caught.message);

                if (duplicated) setFieldErrors(duplicated);
                else setError('이미 사용 중인 값이에요.');
            } else {
                setError(caught.message || '가입하지 못했습니다.');
            }
            setSubmitting(false);
        }
    }

    const filled = values.username && values.password && values.passwordConfirm && values.name;

    return (
        <>
            <div className="page-head">
                <h1 className="page-head__title">회원가입</h1>
                <p className="page-head__desc">저장한 여행을 다음에도 꺼내 보려면 필요해요.</p>
            </div>

            <form className="form auth-form" onSubmit={handleSubmit} noValidate>
                {error && (
                    <p className="form-error" role="alert">
                        {error}
                    </p>
                )}

                <Field
                    name="username"
                    label="아이디"
                    hint="4~20자"
                    autoComplete="username"
                    value={values.username}
                    error={fieldErrors.username}
                    disabled={submitting}
                    onChange={update}
                />

                <Field
                    name="password"
                    label="비밀번호"
                    type="password"
                    hint="8자 이상. 영문과 숫자를 섞어 주세요"
                    autoComplete="new-password"
                    maxLength={PASSWORD_MAX}
                    value={values.password}
                    error={fieldErrors.password}
                    disabled={submitting}
                    onChange={update}
                />

                <Field
                    name="passwordConfirm"
                    label="비밀번호 확인"
                    type="password"
                    autoComplete="new-password"
                    maxLength={PASSWORD_MAX}
                    value={values.passwordConfirm}
                    error={fieldErrors.passwordConfirm}
                    disabled={submitting}
                    onChange={update}
                />

                <Field
                    name="name"
                    label="이름"
                    autoComplete="name"
                    value={values.name}
                    error={fieldErrors.name}
                    disabled={submitting}
                    onChange={update}
                />

                {/*
                  안내 문구를 버튼 위에 둔다.
                  - 만 14세: 만 14세 미만의 개인정보를 받으려면 법정대리인 동의가 필요한데
                    (개인정보보호법 제22조의2) 생년월일을 받지 않아 확인할 방법이 없다.
                    받지 않기로 한 이상 "대상이 아니다" 를 명시해 두는 것이 최소한의 처리다.
                  - 처리방침: 계정을 만드는 순간 개인정보 처리가 시작되므로, 무엇을 얼마나
                    보관하는지 읽을 수 있는 곳을 가입 전에 보여줘야 한다.
                */}
                <p className="form-note">
                    만 14세 이상만 가입할 수 있어요. 가입하면{' '}
                    <Link to="/privacy">개인정보처리방침</Link>에 동의한 것으로 봅니다.
                </p>

                <button
                    type="submit"
                    className={`btn btn--accent btn--lg btn--block${submitting ? ' is-loading' : ''}`}
                    disabled={submitting || !filled}
                >
                    가입하기
                </button>
            </form>

            <p className="hint-link">
                이미 계정이 있으신가요?{' '}
                <Link to="/login" state={gate}>
                    로그인
                </Link>
            </p>
        </>
    );
}

/**
 * 중복 가입(409)을 해당 입력칸 아래 메시지로 옮긴다.
 *
 * 서버는 DuplicateDataException 의 메시지를 그대로 내려주는데, 그 형식이
 *   "이미 사용 중입니다. username : hong123"
 * 처럼 앞은 사람 말이고 뒤는 개발자용이다. 폼 위에 그대로 띄우면
 *   - 뒷부분이 무슨 뜻인지 알 수 없고
 *   - 방금 입력한 아이디/이메일을 화면에 다시 뿌리게 된다
 * 그래서 값은 버리고 "어느 칸이 문제인지" 만 남긴다.
 *
 * ⚠ 서버 메시지 형식에 기대는 코드다. 백엔드 문구가 바뀌면 여기도 같이 봐야 한다.
 *   (근본 해결은 서버가 필드명을 따로 내려주는 것 — 브라우저로 확인한 현재 형식에 맞춰둔다)
 *
 * @returns {object|null} 알아볼 수 없는 형식이면 null
 */
function duplicateFieldError(message = '') {
    if (/\busername\s*:/.test(message)) return { username: '이미 사용 중인 아이디예요.' };

    // 이메일은 받지 않으므로 여기서 걸릴 일이 없다. 다시 받기 시작하면 분기를 하나 더 넣는다
    return null;
}

/**
 * 비밀번호를 보내기 전에 한 번 거른다.
 *
 * 서버도 길이(8~64자)는 검사하지만, "아이디와 같은 비밀번호" 처럼 서버가 막지 않는 것도 있다.
 * 강도 점수를 매기거나 특수문자를 강제하지는 않는다. 규칙을 빡빡하게 걸수록
 * 사람들은 오히려 외우기 쉬운 변형(Password1!)으로 몰린다.
 * 여기서는 "바로 뚫리는 것만" 막고, 나머지는 길이로 유도한다.
 *
 * @returns {string} 문제가 있으면 안내 문구, 없으면 빈 문자열
 */
function passwordProblem(password, username) {
    if (password.length < PASSWORD_MIN) return `비밀번호는 ${PASSWORD_MIN}자 이상이어야 해요.`;
    if (password.length > PASSWORD_MAX) return `비밀번호는 ${PASSWORD_MAX}자까지 쓸 수 있어요.`;

    // 아이디가 그대로 들어간 비밀번호는 아이디만 알면 몇 번 만에 맞힐 수 있다
    if (username && password.toLowerCase().includes(username.toLowerCase())) {
        return '비밀번호에 아이디를 그대로 넣지 말아 주세요.';
    }

    // 숫자만 / 영문만이면 경우의 수가 급격히 줄어든다
    if (/^\d+$/.test(password)) return '숫자만으로는 너무 쉬워요. 영문을 섞어 주세요.';
    if (/^[a-zA-Z]+$/.test(password)) return '영문만으로는 너무 쉬워요. 숫자를 섞어 주세요.';

    return '';
}

/** 입력칸 한 줄. 가입 폼에만 쓰는 모양이라 여기 같이 둔다 */
function Field({ name, label, type = 'text', hint, autoComplete, maxLength, value, error, disabled, onChange }) {
    return (
        <div className={`field${error ? ' is-invalid' : ''}`}>
            <label className="field__label" htmlFor={name}>
                {label}
                <span className="req">*</span>
            </label>

            <input
                id={name}
                className="field__input"
                type={type}
                autoComplete={autoComplete}
                maxLength={maxLength}
                autoCapitalize="none"
                spellCheck="false"
                value={value}
                onChange={(e) => onChange(name, e.target.value)}
                disabled={disabled}
                required
            />

            {/* 에러가 있으면 힌트 대신 에러를 보여준다. 둘 다 띄우면 어느 쪽을 고쳐야 할지 헷갈린다 */}
            {error ? (
                <p className="field__error">{error}</p>
            ) : (
                hint && <p className="field__hint">{hint}</p>
            )}
        </div>
    );
}
