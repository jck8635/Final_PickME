import { Link } from 'react-router'
import { useAuth } from '../lib/app-context'

/** 기존 templates/index.html */
export default function HomePage() {
  const { user } = useAuth()

  return (
    <div className="pm-page pm-home">
      <section className="pm-hero" aria-labelledby="home-title">
        <div className="pm-hero-copy">
          <p className="pm-eyebrow">YOUR CHOICE, YOUR WAY</p>
          <h1 id="home-title">
            선택이 어려운 순간,
            <br />
            <span>생각을 함께 정리해요.</span>
          </h1>
          <p className="pm-lead">
            사소한 망설임부터 오래 품은 고민까지.
            <br />
            PickMe AI와 이야기하고, 나다운 답을 찾아보세요.
          </p>
          <div className="pm-actions">
            <Link className="pm-button" to="/chat">
              AI와 고민 나누기 <span aria-hidden="true">↗</span>
            </Link>
            <Link className="pm-button pm-button-secondary" to="/decision">
              선택지 비교하기
            </Link>
          </div>
          {user ? (
            <p className="pm-hero-note">
              {user.nickname}님, 오늘은 어떤 고민이 있나요? <Link to="/history">내 고민 기록 보기 →</Link>
            </p>
          ) : (
            <p className="pm-hero-note">
              나의 공간을 시작해보세요. <Link to="/signup">회원가입</Link> · <Link to="/login">로그인</Link>
            </p>
          )}
        </div>
        <div className="pm-choice-art" aria-hidden="true">
          <div className="pm-orbit pm-orbit-one"></div>
          <div className="pm-orbit pm-orbit-two"></div>
          <div className="pm-art-label">마음속 복잡한 생각을</div>
          <div className="pm-thought pm-thought-one">어떤 선택이 좋을까?</div>
          <div className="pm-art-core">
            P<span>PickMe AI</span>
          </div>
          <div className="pm-thought pm-thought-two">하나씩, 나답게.</div>
          <div className="pm-art-caption">고민에서 선택까지, 함께</div>
        </div>
      </section>

      <section className="pm-features" aria-labelledby="features-title">
        <div className="pm-section-heading">
          <div>
            <p className="pm-eyebrow">A LITTLE CLARITY</p>
            <h2 id="features-title">지금 나에게 필요한 도움</h2>
          </div>
          <p>고민을 꺼내는 것부터 시작해요.</p>
        </div>
        <div className="pm-feature-grid">
          <Feature no="01" to="/chat" tag="생각을 꺼내고 싶을 때" title="AI 상담" link="이야기 시작하기">
            막연한 고민을 대화로 풀어가며 내 마음과 생각을 정리해보세요.
          </Feature>
          <Feature no="02" to="/decision" tag="선택 사이에서 망설일 때" title="결정 도우미" link="선택지 살펴보기">
            여러 선택지를 살펴보고 나에게 중요한 기준을 찾아보세요.
          </Feature>
          <Feature no="03" to="/history" tag="지난 생각이 궁금할 때" title="고민 기록" link="기록 돌아보기">
            지나온 고민과 선택의 흐름을 돌아보는 나만의 공간이에요.
          </Feature>
          <Feature no="04" to="/community" tag="다른 시선이 필요할 때" title="커뮤니티" link="함께 이야기하기">
            다른 사람들의 고민을 만나고 다양한 관점을 나눠보세요.
          </Feature>
        </div>
      </section>
      <div className="pm-bottom-note">
        <span className="pm-brand-dot" aria-hidden="true"></span>답을 고르는 주인공은 언제나 당신이에요. PickMe AI는 그
        과정을 함께합니다.
      </div>
    </div>
  )
}

function Feature(props: { no: string; to: string; tag: string; title: string; link: string; children: string }) {
  return (
    <Link className="pm-feature" to={props.to}>
      <span className="pm-feature-icon" aria-hidden="true">
        {props.no}
      </span>
      <span className="pm-feature-tag">{props.tag}</span>
      <h3>{props.title}</h3>
      <p>{props.children}</p>
      <span className="pm-feature-link">
        {props.link} <span aria-hidden="true">↗</span>
      </span>
    </Link>
  )
}
