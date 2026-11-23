import { useState } from "react";
import type { FormEvent } from "react";
import { Link, Navigate, useNavigate } from "react-router";
import { api, errorMessage } from "../api/client";
import { useAuth, useFlash } from "../lib/app-context";

const REGIONS = [
  "서울특별시",
  "경기도",
  "인천광역시",
  "부산광역시",
  "대구광역시",
  "대전광역시",
  "광주광역시",
  "울산광역시",
  "세종특별자치시",
  "강원특별자치도",
  "충청북도",
  "충청남도",
  "전북특별자치도",
  "전라남도",
  "경상북도",
  "경상남도",
  "제주특별자치도",
];

const MBTI_TYPES = [
  "ISTJ",
  "ISFJ",
  "INFJ",
  "INTJ",
  "ISTP",
  "ISFP",
  "INFP",
  "INTP",
  "ESTP",
  "ESFP",
  "ENFP",
  "ENTP",
  "ESTJ",
  "ESFJ",
  "ENFJ",
  "ENTJ",
];

function parseList(value: string): string[] {
  return [
    ...new Set(
      value
        .split(",")
        .map((item) => item.trim())
        .filter(Boolean),
    ),
  ];
}

/** 기존 templates/member/signup.html */
export default function SignupPage() {
  const { user } = useAuth();
  const { flash } = useFlash();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [nickname, setNickname] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [birthYear, setBirthYear] = useState("");
  const [region, setRegion] = useState("");
  const [occupation, setOccupation] = useState("");
  const [mbti, setMbti] = useState("");
  const [interests, setInterests] = useState("");
  const [dislikedFoods, setDislikedFoods] = useState("");
  const [bio, setBio] = useState("");

  if (user) return <Navigate to="/mypage" replace />;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    try {
      const year = birthYear.trim() ? Number(birthYear) : null;

      if (
        year !== null &&
        (!Number.isInteger(year) ||
          year < 1900 ||
          year > new Date().getFullYear())
      ) {
        flash("올바른 출생연도를 입력해주세요.");
        setSubmitting(false);
        return;
      }

      const res = await api.post<{ message: string }>("/auth/signup", {
        email,
        nickname,
        password,
        birthYear: year,
        region: region.trim() || null,
        occupation: occupation.trim() || null,
        mbti: mbti || null,
        interests: parseList(interests),
        dislikedFoods: parseList(dislikedFoods),
        bio: bio.trim() || null,
      });

      flash(res.data.message);
      navigate("/login");
    } catch (error) {
      flash(errorMessage(error));
      setSubmitting(false);
    }
  }

  return (
    <div className="pm-page pm-auth-layout">
      <aside className="pm-auth-intro">
        <Link className="pm-wordmark" to="/">
          PickMe AI<span className="pm-brand-dot" aria-hidden="true"></span>
        </Link>
        <p className="pm-eyebrow">YOUR NEXT CHAPTER</p>
        <h2>
          나를 위한 선택,
          <br />
          여기서 시작해요.
        </h2>
        <p>
          AI와 생각을 정리하고, 선택지를 비교하고,
          <br />
          다양한 사람들의 관점을 만나보세요.
        </p>
        <div className="pm-intro-chips">
          <span>AI 상담</span>
          <span>결정 도우미</span>
          <span>고민 기록</span>
          <span>커뮤니티</span>
        </div>
        <Link className="pm-text-link" to="/">
          ← 홈으로 돌아가기
        </Link>
      </aside>
      <section className="pm-card pm-auth-card" aria-labelledby="signup-title">
        <p className="pm-eyebrow">JOIN PICKME</p>
        <h1 id="signup-title">회원가입</h1>
        <p className="pm-muted">세 가지 정보로 나만의 공간을 만들어보세요.</p>
        <form onSubmit={submit} className="pm-form" noValidate>
          <label htmlFor="email">이메일</label>
          <input
            id="email"
            type="email"
            autoComplete="username"
            maxLength={255}
            placeholder="you@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
          <label htmlFor="nickname">닉네임</label>
          <input
            id="nickname"
            autoComplete="nickname"
            maxLength={80}
            placeholder="어떻게 불러드릴까요?"
            value={nickname}
            onChange={(e) => setNickname(e.target.value)}
            aria-describedby="nickname-help"
            required
          />
          <p id="nickname-help" className="pm-field-help">
            다른 회원과 겹치지 않는 닉네임 · 최대 80자
          </p>
          <label htmlFor="password">비밀번호</label>
          <input
            id="password"
            type="password"
            autoComplete="new-password"
            minLength={8}
            maxLength={1024}
            placeholder="8자 이상 입력해주세요"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            aria-describedby="password-help"
            required
          />
          <p id="password-help" className="pm-field-help">
            공백만 사용하지 않고 8자 이상 입력해주세요.
          </p>

          <div style={{ marginTop: "24px" }}>
            <h2>추가 프로필 정보</h2>
            <p className="pm-muted">
              AI 맞춤형 추천에 활용할 정보입니다. 모든 항목은 선택 사항이며
              나중에 마이페이지에서 수정할 수 있습니다.
            </p>
          </div>

          <label htmlFor="signup-birth-year">출생연도</label>
          <input
            id="signup-birth-year"
            type="number"
            min={1900}
            max={new Date().getFullYear()}
            placeholder="예: 2000"
            value={birthYear}
            onChange={(e) => setBirthYear(e.target.value)}
          />

          <label htmlFor="signup-region">거주 지역</label>
          <select
            id="signup-region"
            value={region}
            onChange={(e) => setRegion(e.target.value)}
          >
            <option value="">선택 안 함</option>
            {REGIONS.map((item) => (
              <option key={item} value={item}>
                {item}
              </option>
            ))}
          </select>

          <label htmlFor="signup-occupation">직업</label>
          <input
            id="signup-occupation"
            maxLength={100}
            placeholder="예: 대학생, 직장인, 취업 준비생"
            value={occupation}
            onChange={(e) => setOccupation(e.target.value)}
          />

          <label htmlFor="signup-mbti">MBTI</label>
          <select
            id="signup-mbti"
            value={mbti}
            onChange={(e) => setMbti(e.target.value)}
          >
            <option value="">선택 안 함</option>
            {MBTI_TYPES.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </select>

          <label htmlFor="signup-interests">관심사 / 취향</label>
          <input
            id="signup-interests"
            maxLength={1000}
            placeholder="예: 게임, 운동, 여행"
            value={interests}
            onChange={(e) => setInterests(e.target.value)}
          />
          <p className="pm-field-help">
            여러 개 입력할 경우 쉼표(,)로 구분해주세요.
          </p>

          <label htmlFor="signup-disliked-foods">싫어하는 음식</label>
          <input
            id="signup-disliked-foods"
            maxLength={1000}
            placeholder="예: 오이, 가지, 해산물"
            value={dislikedFoods}
            onChange={(e) => setDislikedFoods(e.target.value)}
          />

          <label htmlFor="signup-bio">추가 자기소개</label>
          <textarea
            id="signup-bio"
            rows={4}
            maxLength={2000}
            placeholder="AI가 참고하면 좋을 나의 정보를 입력해주세요."
            value={bio}
            onChange={(e) => setBio(e.target.value)}
          />

          <button
            className="pm-button pm-button-full"
            type="submit"
            disabled={submitting}
          >
            가입하기 <span aria-hidden="true">→</span>
          </button>
        </form>
        <p className="pm-form-footer">
          이미 계정이 있으신가요? <Link to="/login">로그인</Link>
        </p>
      </section>
    </div>
  );
}
