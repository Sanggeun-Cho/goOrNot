/**
 * ui.js
 * 토스트 · 폼 에러 표시 · 제출 상태 관리 같은 화면 공통 유틸.
 */

import { ApiError } from './api.js';

/* ── 토스트 ──────────────────────────────────────────── */

function toastArea() {
    let area = document.querySelector('.toast-area');

    if (!area) {
        area = document.createElement('div');
        area.className = 'toast-area';
        area.setAttribute('role', 'status');
        area.setAttribute('aria-live', 'polite');
        document.body.appendChild(area);
    }

    return area;
}

/**
 * @param {string} message
 * @param {'info'|'success'|'error'} type
 */
export function toast(message, type = 'info') {
    const element = document.createElement('div');
    element.className = `toast toast--${type}`;
    element.textContent = message;

    toastArea().appendChild(element);

    setTimeout(() => {
        element.classList.add('is-leaving');
        element.addEventListener('animationend', () => element.remove(), { once: true });
    }, 2600);
}

/* ── 폼 에러 ─────────────────────────────────────────── */

function fieldOf(form, name) {
    const input = form.elements[name];
    return input ? input.closest('.field') : null;
}

export function setFieldError(form, name, message) {
    const field = fieldOf(form, name);
    if (!field) return false;

    field.classList.add('is-invalid');

    const errorBox = field.querySelector('.field__error');
    if (errorBox) errorBox.textContent = message;

    return true;
}

export function clearFieldErrors(form) {
    form.querySelectorAll('.field.is-invalid').forEach((field) => {
        field.classList.remove('is-invalid');

        const errorBox = field.querySelector('.field__error');
        if (errorBox) errorBox.textContent = '';
    });
}

/**
 * ApiError 를 화면에 뿌린다.
 * validation 실패(필드별 메시지)는 해당 입력칸 아래에, 나머지는 토스트로 보여준다.
 */
export function showApiError(form, error) {
    if (!(error instanceof ApiError)) {
        toast('네트워크 오류가 발생했습니다. 연결을 확인해 주세요.', 'error');
        console.error(error);
        return;
    }

    if (form && error.fieldErrors) {
        let placed = false;
        Object.entries(error.fieldErrors).forEach(([name, message]) => {
            if (setFieldError(form, name, message)) placed = true;
        });

        if (placed) return; // 폼에 없는 필드명이면 아래 토스트로 넘어간다
    }

    toast(error.message, 'error');
}

/* ── 제출 ────────────────────────────────────────────── */

/**
 * submit 핸들러를 연결하면서 중복 제출 차단과 로딩 표시를 같이 처리한다.
 * @param {HTMLFormElement} form
 * @param {(values: Record<string, string>) => Promise<void>} handler
 */
export function bindSubmit(form, handler) {
    let submitting = false;

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        if (submitting) return;

        submitting = true;
        clearFieldErrors(form);

        const button = form.querySelector('[type="submit"]');
        if (button) {
            button.disabled = true;
            button.classList.add('is-loading');
        }

        try {
            await handler(formValues(form), form);
        } catch (error) {
            showApiError(form, error);
        } finally {
            submitting = false;
            if (button) {
                button.disabled = false;
                button.classList.remove('is-loading');
            }
        }
    });
}

/** 폼 입력값을 객체로. 값이 비어 있는 항목은 빈 문자열로 남겨두고 호출부가 판단한다. */
export function formValues(form) {
    const values = {};

    new FormData(form).forEach((value, key) => {
        values[key] = typeof value === 'string' ? value.trim() : value;
    });

    return values;
}

/**
 * 빈 문자열 항목을 제거한다.
 * 백엔드 update 는 null 인 필드를 "변경 없음"으로 보기 때문에,
 * 빈 칸을 빈 문자열로 보내면 값이 지워져 버린다.
 */
export function omitEmpty(object) {
    return Object.fromEntries(
        Object.entries(object).filter(([, value]) => value !== '' && value !== null && value !== undefined),
    );
}

/* ── 표시 유틸 ───────────────────────────────────────── */

/** data-field 가 같은 요소를 모두 채운다 (요약 영역과 상세 목록에 같은 값이 중복 노출될 수 있어서) */
export function setText(root, name, value) {
    root.querySelectorAll(`[data-field="${name}"]`).forEach((element) => {
        element.textContent = value ?? '';
        element.dataset.empty = value ? 'false' : 'true';
    });
}

/** LocalDateTime 문자열(2026-09-11T12:34:56)을 보기 좋게 */
export function formatDate(value) {
    if (!value) return '';

    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;

    return date.toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' });
}
