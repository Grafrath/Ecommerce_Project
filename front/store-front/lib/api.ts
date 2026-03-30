import axios from 'axios';

// 1. Axios 인스턴스 생성
const api = axios.create({
    // baseURL을 생략하면 현재 도메인 기준으로 상대 경로(/api/*, /login)가 정상 작동합니다.
    headers: {
        'Content-Type': 'application/json',
    },
});

// 2. Request 인터셉터 (요청 전 가로채기)
api.interceptors.request.use(
    (config) => {
        // Next.js SSR 에러 방지를 위해 브라우저 환경인지 확인
        if (typeof window !== 'undefined') {
            const token = localStorage.getItem('accessToken');
            if (token) {
                config.headers.Authorization = `Bearer ${token}`;
            }
        }
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

// 3. Response 인터셉터 (응답 후 가로채기)
api.interceptors.response.use(
    (response) => {
        return response;
    },
    (error) => {
        // 401 Unauthorized 에러 처리 (토큰 만료 또는 인증 실패)
        if (error.response && error.response.status === 401) {
            if (typeof window !== 'undefined') {
                localStorage.removeItem('accessToken');
                alert('로그인이 필요하거나 세션이 만료되었습니다.');
                // useRouter 훅 대신 window.location 사용하여 로그인 페이지로 강제 이동
                window.location.href = '/login';
            }
        }
        return Promise.reject(error);
    }
);

export default api;