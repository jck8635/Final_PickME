import { request, type FullConfig } from "@playwright/test";

export default async function globalSetup(config: FullConfig) {
  if (process.env.E2E_TEST_DB_CONFIRMED !== "yes") {
    throw new Error("E2E_TEST_DB_CONFIRMED=yes 설정이 필요합니다.");
  }

  const baseURL = config.projects[0]?.use.baseURL;

  if (baseURL !== "http://localhost:5173") {
    throw new Error("E2E 테스트는 지정된 로컬 환경에서만 실행할 수 있습니다.");
  }

  const api = await request.newContext({
    baseURL,
    timeout: 10000,
  });

  try {
    const response = await api.get("/api/health/test-environment");

    if (!response.ok()) {
      throw new Error("백엔드 테스트 환경 확인에 실패했습니다.");
    }

    const result = await response.json();

    if (result.testAllowed !== true) {
      throw new Error(
        "테스트가 허용되지 않는 환경입니다. dev 프로필을 확인하세요.",
      );
    }

    console.log("[E2E] 개발용 프로필 확인 완료");
  } finally {
    await api.dispose();
  }
}
