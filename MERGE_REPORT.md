# 통합 및 검증 보고서
Community(5)를 기준으로 결정 도우미의 역질문, 프로필 상담, 수동 저장을 병합했습니다. 기존 관리자/커뮤니티/기록/RAG API를 유지했습니다. 저장 후 임베딩을 요청하고 실패 시 기록 동기화로 재시도합니다. 자동 저장은 수동 저장으로 대체했습니다.

검증: Python 19개 파일 AST 파싱 성공, TypeScript/TSX 25개 파일 구문 파싱 성공. 타입 검사나 Java 컴파일 성공을 의미하지 않습니다. Gradle 다운로드 DNS 실패, npm ci 제한시간 초과 및 Node 버전 미달, pytest google.genai 미설치로 실행 차단. 실제 Supabase/Gemini 및 브라우저 통합 테스트 미실행. 기존 DB 스키마와 환경 설정 필요.

Windows 검증 명령 (각 폴더에서):
- ai-server: `python -m pip install -r requirements.txt pytest` 후 `python -m pytest -q`
- backend: `gradlew.bat test` (Java 21)
- frontend: `npm ci` 후 `npm run build` (Node >=22.22.0)

실제 통합 확인: 로그인 → 상담/역질문 → 수동 저장 → 기록 조회 → 임베딩/유사 검색/RAG → 회고 수정 및 통계 → 게시글/댓글/투표/공감/신고/관리자. 비로그인 저장 거부 및 다른 사용자 기록 접근 거부도 확인. 운영 DB 대신 테스트 DB 사용.

중복 소스 아카이브 backend.zip 및 결정 브랜치의 HWP 문서는 실행 소스가 아니므로 제외했습니다. 환경 변수 및 API 키는 직접 설정해야 합니다.
