# PickME JWT + Redis 변경본 (검증 전)

기준: Final_PickME-feature-community(7).zip. 기존 AI 서버, DB 스키마, 서비스 기능은 그대로 유지. 소셜 로그인 미포함. 기존 JSESSIONID는 더 이상 인증에 사용하지 않으므로 재로그인 필요.

## Windows 로컬 실행
1. Docker Desktop 실행 후 프로젝트 루트에서 `docker compose -f compose.redis.yml up -d`.
2. `docker compose -f compose.redis.yml exec redis redis-cli ping` → PONG 확인.
3. PowerShell에서 `cd backend` 후 아래 명령으로 무작위 32바이트 비밀키 생성 (기존 .env 내용 보존, 실제 키는 Git에 올리지 않기).

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+','-').Replace('/','_')
$env:REDIS_URL = 'redis://localhost:6379'
$env:AUTH_COOKIE_SECURE = 'false'
.\gradlew.bat bootRun
```

키를 재시작 후에도 유지하려면 backend/.env에 JWT_SECRET 값을 저장. 운영은 HTTPS, AUTH_COOKIE_SECURE=true 필수(prod 프로필은 false 거부). Supabase prod 프로필을 로컬에서 쓰는 경우에도 true 필요; localhost 쿠키 동작을 확인하거나 HTTPS 로컬 환경 사용. 운영 Redis는 인증/TLS, 비공개 네트워크, noeviction 및 적절한 영속성 설정. 테스트 Redis는 별도 인스턴스 또는 전용 DB 15 사용. 실제 운영 Redis에서 테스트 금지.

4. 기존 FastAPI 실행 방식 유지. 별도 터미널에서 frontend로 이동, `npm ci`, `npm run dev`.
5. 테스트: backend에서 `.\gradlew.bat test`, frontend에서 `npm run build`. 기존 세션 기반 ApiFlowTest는 Bearer 헤더로 변경. Rotation 재사용 테스트 추가. 이 환경은 Gradle 다운로드 DNS 실패 및 Redis 미설치로 실행 검증하지 못함.

## 정책
- HS256 JWT 15분, issuer/audience/signature/expiration 검증. Redis 인증 세션이 살아 있고 DB 비밀번호 해시 지문이 일치해야 API 인증 성공. 따라서 완전 무상태 JWT가 아니라 즉시 폐기 가능한 혼합 구조.
- Refresh Token은 세션 UUID + 256비트 난수. Redis에는 난수의 SHA-256 해시만 저장. 7일 절대 만료, 갱신 시 연장하지 않음. Lua로 원자적 교체 및 사용된 토큰 재사용 감지. Redis 세션 삭제 시 기존 JWT 모두 무효화. 비밀번호 변경/회원 삭제는 DB 확인으로 모든 기기 인증 무효화.
- Access Token 메모리, Refresh HttpOnly SameSite=Lax 쿠키(/api), CSRF 기존 유지. Vercel /api rewrite 동일 출처 전제. Redis 장애 시 인증 실패(우회 없음).
- 로그인 시 계정 5회/15분, IP 50회/15분 제한(성공 시 계정 카운터 초기화). IP는 신뢰할 수 있는 프록시 설정이 전제. 프록시 헤더 신뢰 범위 및 다중 탭/동시 갱신/장애 복구는 배포 전 검증 필요.
- 기존 관리자 로그인도 동일 토큰 방식. 소셜 로그인 추가 시 검증된 OAuth 사용자에 대해 TokenService.issue(tokens.member(user), request, response) 호출하는 별도 통합 필요.

## 필수 검증
회원가입/로그인/새로고침/로그아웃, 토큰 변조/만료/재사용, CSRF 없는 refresh 거부, 일반 회원 관리자 API 403, 비밀번호 변경 후 다른 기기 토큰 거부, Redis 중단 시 인증 거부, 커뮤니티 작성자 권한/고민기록 사용자 격리, AI 상담 및 결정 도우미, 관리자 로그인/로그아웃. 브라우저 개발자 도구에서 토큰 원문을 캡처하거나 공유하지 말 것.
