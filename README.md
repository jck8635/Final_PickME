# PickMe (Spring Boot + FastAPI + React)

기존 `pickme-flask`(Flask 단일 서버, `origin/main` 2026-11-06 기준)의 모든 기능을 3개 서버 구조로 옮긴 프로젝트입니다.

```
React (5173)  ──/api──▶  Spring Boot (8080)  ──▶  FastAPI (8000)  ──▶  Gemini
 화면                     로그인·고민기록·커뮤니티·DB     AI상담·결정도우미(LLM)
```

- React 는 Spring Boot 만 호출합니다. FastAPI 는 Spring Boot 만 호출합니다.
- DB(PostgreSQL)는 Spring Boot 만 접근합니다.

## 버전

| 영역 | 선택 | 이유 |
|---|---|---|
| Java | 21 (LTS) | Spring Boot 4.1 지원 범위(17~26) 안에서 도구 호환이 가장 안정적 |
| Spring Boot | 4.1.2 | 현재 무료 지원 중인 최신 안정 라인 (4.0 은 2026-12 지원 종료) |
| Gradle | wrapper 포함 | 별도 설치 불필요. JDK 21 은 자동으로 찾습니다 |
| Python | 3.13 (3.12 도 동작 확인) | ML/DL 라이브러리 호환이 가장 넓음 |
| FastAPI | 0.162.x | 최신 안정 |
| Node.js | 24 LTS (22.22 이상이면 동작) | React Router 8 요구사항 충족 |
| React | 19.3 | 최신 안정 |
| Vite | 8.3 + @vitejs/plugin-react 6 | 최신 안정 |
| Tailwind CSS | 4.3 (@tailwindcss/vite) | 기존 화면이 Tailwind CDN 을 쓰던 것을 빌드 방식으로 전환 |
| TypeScript | 6.0 | Vite React 템플릿 기본값 |

## 폴더

```
backend/     Spring Boot  (com.pickme)
  member/      회원가입·로그인(세션)·마이페이지
  chat/        AI 상담 (FastAPI 호출)
  decision/    결정 도우미 (FastAPI 호출 + 기록 저장)
  history/     고민 기록 (Decision 엔티티, 북마크·회고)
  community/   커뮤니티 (게시글·투표·공감·댓글)
  ai/          FastAPI 클라이언트
  common/      공통 오류 처리, 카테고리
  config/      보안(세션·CSRF), AI 클라이언트 설정
ai-server/   FastAPI      (app/)
  services/chat_service.py      Flask 의 AI 상담 로직(위기/유해/잡담 판별, 선택지 추출) 그대로
  services/decision_service.py  Flask 의 결정 도우미 프롬프트
  services/gemini.py            Gemini 호출 공통 (혼잡 시 재시도·예비 모델)
frontend/    React + Vite (src/)
  pages/       홈, 로그인, 회원가입, 마이페이지, AI 상담, 결정 도우미, 고민 기록, 커뮤니티
  styles/legacy/  Flask 의 CSS 를 그대로 옮긴 것
.vscode/     팀 공용 VS Code 설정 (추천 확장, 실행 구성)
```

## 처음 한 번 설치

사전 설치: JDK 21, Python 3.13, Node.js 24

```bash
# 1) Spring Boot
cd backend
./gradlew build          # Windows: gradlew.bat build

# 2) FastAPI
cd ../ai-server
python -m venv .venv
# Windows: .venv\Scripts\activate   /  macOS·Linux: source .venv/bin/activate
pip install -r requirements-dev.txt
#   Windows 에서 cp949 UnicodeDecodeError 가 나면:  set PYTHONUTF8=1  후 다시 실행
cp .env.example .env     # GEMINI_API_KEY 입력 (.env.example 에는 키를 넣지 마세요. git 에 올라갑니다)

# 3) React
cd ../frontend
npm install
```

## 실행

VS Code: `pickme` 폴더를 열고 → 실행 및 디버그(Ctrl+Shift+D) → **전체 실행 (Spring + FastAPI + React)**
(처음 한 번 `Python: Select Interpreter` 에서 `ai-server/.venv` 를 선택)

터미널로 따로 실행할 때:

```bash
cd backend   && ./gradlew bootRun
cd ai-server && uvicorn app.main:app --reload --port 8000
cd frontend  && npm run dev
```

http://localhost:5173 접속. FastAPI 문서: http://localhost:8000/docs

## 테스트

```bash
cd backend   && ./gradlew test        # 회원/보안/결정도우미/고민기록/커뮤니티 통합 테스트 (AI 서버는 모킹)
cd ai-server && pytest && ruff check app tests
cd frontend  && npm run build         # 타입 검사 + 빌드
```

## API 요약 (React → Spring, 모두 `/api` 로 시작)

| 기능 | 메서드·경로 | 로그인 |
|---|---|---|
| 내 정보 | `GET /auth/me` | - |
| 회원가입 / 로그인 / 로그아웃 | `POST /auth/signup`, `/auth/login`, `/auth/logout` | - |
| 닉네임 / 비밀번호 변경 | `PATCH /members/me/nickname`, `/members/me/password` | 필요 |
| AI 상담 | `POST /chat` | - |
| 결정 도우미 상담 / 스타일만 바꿔 다시 | `POST /decisions/consult`, `/decisions/restyle` | - (로그인 시 기록 저장) |
| 인기 고민 Top 5 | `GET /decisions/popular` | - |
| 고민 기록 목록 (q, category, bookmarked, sort) | `GET /history` | 필요 |
| 북마크 / 회고 / 삭제 / 전체 삭제 | `POST /history/{id}/bookmark`, `PUT /history/{id}/retrospective`, `DELETE /history/{id}`, `DELETE /history` | 필요 |
| 커뮤니티 목록 (category, q) / 변경 확인 | `GET /community/posts`, `GET /community/updates` | - |
| 글 작성 / 수정 / 삭제 | `POST /community/posts`, `PUT·DELETE /community/posts/{id}` | 필요 |
| 투표 / 투표 취소 / 공감 | `POST /community/posts/{id}/votes/{optionId}`, `DELETE /community/posts/{id}/votes`, `POST /community/posts/{id}/like` | 필요 |
| 댓글 작성 / 수정 / 삭제 | `POST /community/posts/{id}/comments`, `PUT·DELETE /community/posts/{id}/comments/{cid}` | 필요 |

- 오류 응답은 항상 `{"message": "화면에 보여줄 문장"}` 입니다.
- 로그인은 세션 쿠키(JSESSIONID), CSRF 는 `XSRF-TOKEN` 쿠키 → `X-XSRF-TOKEN` 헤더 (axios 가 자동 처리).

FastAPI (Spring → FastAPI): `POST /chat`, `POST /decision/consult`, `GET /health`.

## DB

- 기본(`local` 프로필): H2 파일 DB(`backend/data/`). 설치 없이 실행되고 테이블도 자동 생성됩니다.
- PostgreSQL: `SPRING_PROFILES_ACTIVE=prod` 와 `backend/.env.example` 의 값을 환경변수로 지정.
  `prod` 는 `ddl-auto: validate` 라서 테이블을 자동으로 바꾸지 않습니다.
- 테이블/컬럼 이름은 Flask 와 동일합니다(`user`, `decision`, `post`, `comment`, `post_options`,
  `community_votes`, `community_likes`). 기존 Neon DB 를 그대로 연결할 수 있습니다.

## Flask 에서 옮기면서 달라진 점

- **기존 회원 비밀번호 호환**: Werkzeug 해시(scrypt / pbkdf2)도 그대로 로그인됩니다. 새 비밀번호는 BCrypt 로 저장됩니다.
- **고민 기록 보안 수정**: 원본은 로그인하지 않으면 `user_id=1` 의 기록을 보여주던 임시 코드가 있었습니다. 이제 로그인한 본인 기록만 보입니다.
- **고민 기록 카테고리**: 원본은 결정 도우미가 카테고리를 저장하지 않아 기록 화면의 카테고리 필터가 동작하지 않았습니다. 이제 첫 번째로 고른 카테고리를 저장합니다.
- **판단 기준 버그 수정**: 원본은 판단 기준 문자열을 한 글자씩 쪼개 프롬프트에 넣었습니다(`가, 격, (, 5, 점...`). 이제 `비용 (예산)(5점), 시간 효율(보류)` 형태로 전달합니다.
- **Gemini 혼잡 대응**: 503/429 는 한 번 재시도하고, 실패하면 예비 모델(`GEMINI_DECISION_MODELS`)로 넘어갑니다. AI 상담은 끝까지 실패해도 원본의 규칙 기반 답변으로 응답합니다.
- **화면**: 템플릿 HTML·CSS 클래스를 그대로 React 로 옮겼습니다. 서버 렌더링 + 새로고침 대신 API 호출로 동작합니다(커뮤니티는 5초마다 변경을 확인해 자동 갱신).
- 원본의 결정 도우미 2·3단계 별도 페이지(`/decision/new`, `/decision/conditions`)는 원본에서도 쓰이지 않아 옮기지 않았습니다(한 페이지 방식만 사용).
