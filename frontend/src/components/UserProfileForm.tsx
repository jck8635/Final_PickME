import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { api, errorMessage } from "../api/client";
import { useFlash } from "../lib/app-context";

type UserProfile = {
  userId: number;
  birthYear: number | null;
  region: string | null;
  occupation: string | null;
  mbti: string | null;
  interests: string[];
  dislikedFoods: string[];
  bio: string | null;
};

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

export default function UserProfileForm() {
  const { flash } = useFlash();

  const [birthYear, setBirthYear] = useState("");
  const [region, setRegion] = useState("");
  const [occupation, setOccupation] = useState("");
  const [mbti, setMbti] = useState("");
  const [interests, setInterests] = useState("");
  const [dislikedFoods, setDislikedFoods] = useState("");
  const [bio, setBio] = useState("");

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [loadError, setLoadError] = useState("");

  useEffect(() => {
    let active = true;

    async function loadProfile() {
      try {
        const response = await api.get<UserProfile>("/users/me/profile");

        if (!active) return;

        const profile = response.data;

        setBirthYear(profile.birthYear?.toString() ?? "");
        setRegion(profile.region ?? "");
        setOccupation(profile.occupation ?? "");
        setMbti(profile.mbti ?? "");
        setInterests((profile.interests ?? []).join(", "));
        setDislikedFoods((profile.dislikedFoods ?? []).join(", "));
        setBio(profile.bio ?? "");
      } catch (error) {
        if (active) {
          setLoadError(errorMessage(error));
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    void loadProfile();

    return () => {
      active = false;
    };
  }, []);

  async function saveProfile(event: FormEvent) {
    event.preventDefault();

    const year = birthYear.trim() ? Number(birthYear) : null;

    const currentYear = new Date().getFullYear();

    if (
      year !== null &&
      (!Number.isInteger(year) || year < 1900 || year > currentYear)
    ) {
      flash("올바른 출생연도를 입력해주세요.");
      return;
    }

    setSaving(true);

    try {
      await api.put("/users/me/profile", {
        birthYear: year,
        region: region.trim() || null,
        occupation: occupation.trim() || null,
        mbti: mbti || null,
        interests: parseList(interests),
        dislikedFoods: parseList(dislikedFoods),
        bio: bio.trim() || null,
      });

      flash("개인 프로필을 저장했습니다.");
    } catch (error) {
      flash(errorMessage(error));
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="pm-card" aria-labelledby="personal-profile-title">
      <span className="pm-step">03 / PERSONAL PROFILE</span>

      <h2 id="personal-profile-title">개인 프로필 설정</h2>

      <p className="pm-muted">
        AI 맞춤형 추천에 활용할 정보를 입력해주세요. 모든 항목은 선택
        사항입니다.
      </p>

      {loading ? (
        <p>프로필 정보를 불러오는 중...</p>
      ) : loadError ? (
        <p role="alert">프로필을 불러오지 못했습니다: {loadError}</p>
      ) : (
        <form onSubmit={saveProfile} className="pm-form">
          <label htmlFor="profile-birth-year">출생연도</label>
          <input
            id="profile-birth-year"
            type="number"
            min={1900}
            max={new Date().getFullYear()}
            placeholder="예: 2000"
            value={birthYear}
            onChange={(e) => setBirthYear(e.target.value)}
          />

          <label htmlFor="profile-region">거주 지역</label>
          <select
            id="profile-region"
            value={region}
            onChange={(e) => setRegion(e.target.value)}
          >
            <option value="">선택 안 함</option>
            {REGIONS.map((item) => (
              <option key={item} value={item}>
                {item}
              </option>
            ))}
            {region && !REGIONS.includes(region) && (
              <option value={region}>{region}</option>
            )}
          </select>

          <label htmlFor="profile-occupation">직업</label>
          <input
            id="profile-occupation"
            maxLength={100}
            placeholder="예: 대학생, 직장인, 취업 준비생"
            value={occupation}
            onChange={(e) => setOccupation(e.target.value)}
          />

          <label htmlFor="profile-mbti">MBTI</label>
          <select
            id="profile-mbti"
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

          <label htmlFor="profile-interests">관심사 / 취향</label>
          <input
            id="profile-interests"
            maxLength={1000}
            placeholder="예: 게임, 운동, 여행"
            value={interests}
            onChange={(e) => setInterests(e.target.value)}
          />
          <p className="pm-field-help">
            여러 개 입력할 경우 쉼표(,)로 구분해주세요.
          </p>

          <label htmlFor="profile-disliked-foods">싫어하는 음식</label>
          <input
            id="profile-disliked-foods"
            maxLength={1000}
            placeholder="예: 오이, 가지, 해산물"
            value={dislikedFoods}
            onChange={(e) => setDislikedFoods(e.target.value)}
          />

          <label htmlFor="profile-bio">추가 자기소개</label>
          <textarea
            id="profile-bio"
            rows={4}
            maxLength={2000}
            placeholder="AI가 참고하면 좋을 나의 정보를 입력해주세요."
            value={bio}
            onChange={(e) => setBio(e.target.value)}
          />

          <button className="pm-button" type="submit" disabled={saving}>
            {saving ? "저장 중..." : "개인 프로필 저장"}
          </button>
        </form>
      )}
    </section>
  );
}
