"""결정 도우미 로직. 기존 pickme-flask 의 services/decision_service.py 를 옮긴 것."""

import logging

from google.genai import types

from app.core.config import get_settings
from app.services import gemini

logger = logging.getLogger(__name__)

# 고민 카테고리 (복수 선택 가능)
CATEGORIES = [
    "음식/식사",
    "진로/직업",
    "소비/쇼핑",
    "연애/관계",
    "일상/생활",
    "학습/자기계발",
    "여행/여가",
    "기타",
]

# AI 조언 스타일 (상담 중 변경 가능)
STYLES = {
    "soft": (
        "따뜻한 공감형: 친구 같은 어조로 먼저 충분히 공감해주고, 장점 중심으로 부드럽게 조언한다."
    ),
    "objective": (
        "객관적인 분석형: 근거 기반 논리와 데이터처럼 객관적인 시각으로 균형 잡힌 조언을 한다."
    ),
    "fact": "냉정한 팩트형: 우유부단함 없이 현실적이고 냉정하게, 핵심만 간결하게 짚어준다.",
}


class ConsultationError(RuntimeError):
    """사용자에게 보여줄 수 있는 상담 실패 메시지."""


def get_ai_consultation(prompt: str) -> str:
    if not gemini.has_api_key():
        raise ConsultationError("AI 서버에 GEMINI_API_KEY가 설정되지 않았습니다.")
    try:
        return gemini.generate(
            prompt,
            models=get_settings().decision_models,
            config=types.GenerateContentConfig(
                temperature=0.7,
                max_output_tokens=1200,
            ),
        ).strip()
    except gemini.GeminiUnavailable as error:
        logger.warning("결정 도우미 Gemini 호출 실패: %s", error.__cause__ or error)
        raise ConsultationError(
            "AI 응답을 생성하지 못했습니다. 잠시 후 다시 시도해 주세요."
        ) from error


# 역질문 프롬프트
def build_question_prompt(
    title: str,
    content: str,
    options: list[dict] | None = None,  # 선택지
    criteria: str | None = None,  # 가중지
) -> str:

    # AI 읽는 문자열정리
    option_text = "\n".join(f"{i}. {opt['title']}" for i, opt in enumerate(options or [], 1))

    # 선택,가중치 판단
    criteria_text = criteria.strip() if criteria and criteria.strip() else " 지정 없음 "
    lines = [
        "당신은 결정 도우미의 역질문 담당 AI입니다.",
        "당신의 역할은 고민의 누락이나 불일치를 확인하고, 필요한 대안을 제안하는 것뿐입니다.",
        "상담, 장단점 분석, 최종 선택 및 결론은 절대 작성하지 않습니다.",
        "",
        "작성 규칙:",
        "1. 고민 내용에 등장하는 실제 비교 대상과 선택지 목록을 대조합니다.",
        (
            "2. 고민 내용에는 있지만 선택지에는 없는 대상과, 선택지에는 "
            "있지만 고민 내용에는 없는 대상을 확인합니다."
        ),
        "3. 누락된 대상과 추가된 대상이 있다면 한 문장으로 묶어 한 번만 알려줍니다.",
        (
            "4. 선택지에 포함되었다는 이유만으로 사용자가 그 대상을 실제"
            "로 고민한다고 판단하지 않습니다."
        ),
        "5. 사용자가 이미 밝힌 고민이나 선호를 다시 질문하지 않습니다.",
        "6. 사용자가 말하지 않은 취향, 목적, 예산, 시간, 주변 사람의 의견을 만들어내지 않습니다.",
        (
            "7. 확인 질문은 결정에 꼭 필요한 정보가 실제로 빠져 있을 때만"
            " 작성합니다. 사용자의 선택을 다시 묻는 질문은 금지합니다."
        ),
        "8. 사용자가 대안을 요청했다면 고민 내용에 등장한 대상과 직접 연결되는 대안만 제안합니다.",
        (
            "9. 음식 고민에서는 사용자가 언급한 음식의 특징과 연결되는 메"
            "뉴를 제안합니다. 예를 들어 짬뽕과 짜장을 함께 고민한다면 짬"
            "짜면, 해산물을 좋아하는 경우의 삼선짬뽕처럼 구체적으로 연결"
            "합니다."
        ),
        "10. 볶음밥처럼 고민 대상과 직접 연결되지 않은 메뉴를 무작위로 추천하지 않습니다.",
        "11. 대안은 기존 선택지에 자동으로 추가하지 않습니다.",
        (
            "12. 판단 기준의 숫자는 중요도 1~5점이지 실제 금액이나 시간이"
            " 아닙니다. 이를 근거로 추가 질문하지 않습니다."
        ),
        "13. 확인 질문이나 대안 제안이 필요하지 않다면 억지로 작성하지 않습니다.",
        "14. 선택지 비교, 점수 계산, 장단점 분석, 최종 추천 및 상담 답변은 작성하지 않습니다.",
        (
            "15. 출력은 필요한 항목만 한 줄씩 작성합니다. 서론, 장황한 설"
            "명, 반복, 별도의 상담 문단은 금지합니다."
        ),
        "",
        "[출력 형식]",
        "1. 누락 또는 추가 항목이 있다면 그 차이를 한 문장으로 설명합니다.",
        "2. 꼭 필요한 확인 질문이 있다면 작성합니다.",
        "3. 사용자가 대안을 요청했다면 관련 대안과 추천 이유를 작성합니다.",
        "해당 항목이 필요하지 않으면 생략합니다.",
        "",
        f"[고민 제목] {title or '제목 없음'}",
        "[고민 내용]",
        content,
        "",
        "[비교할 선택지]",
        option_text or "선택지 없음",
        "",
        f"[판단 기준 및 가중치] {criteria_text}",
    ]

    # 줄바꿈
    return "\n".join(lines)


# AI ->답을 질문 목록
def get_follow_up_questions(
    title: str,
    content: str,
    options: list[dict] | None = None,
    criteria: str | None = None,
) -> list[str]:
    #  지시문
    prompt = build_question_prompt(title, content, options, criteria)

    # AI 호출
    text = get_ai_consultation(prompt)

    # 줄단위 나누기
    questions = []
    for line in text.splitlines():
        # 앞뒤공백 , 기호 지움
        cleaned = line.strip().lstrip("-*0123456789.) ").strip()
        # 빈줄 건너뜀
        if cleaned:
            questions.append(cleaned)
            # 질문은 3개만
    return questions[:3]


def build_prompt(
    categories: list[str],
    style: str,
    title: str,
    content: str,
    conditions: str,
    options: list[dict] | None = None,
    criteria: str | None = None,
    user_profile: dict | None = None,  # 마이페이지 사용자 정보추가
) -> str:
    """카테고리(복수), 스타일, 제목, 내용, 추가 조건, 선택지, 판단 기준(가중치 포함)을 반영."""
    style_guide = STYLES.get(style, STYLES["objective"])
    cat_text = ", ".join(categories) if categories else "지정 없음"

    opt_lines = ""
    if options:
        opt_lines += "[비교할 선택지]\n"
        for i, opt in enumerate(options, 1):
            line = f"{i}. {opt['title']}"
            if opt.get("note"):
                line += f" - {opt['note']}"
            opt_lines += line + "\n"
        opt_lines += "\n"

    # 원본은 이미 만들어진 문자열을 다시 ", ".join 해서 글자 단위로 쪼개지는 버그가 있었음
    crit_text = criteria.strip() if criteria and criteria.strip() else "지정 없음"

    profile_text = ""
    if user_profile:
        profile_items = {
            "지역": user_profile.get("region"),
            "직업": user_profile.get("occupation"),
            "MBTI": user_profile.get("mbti"),
            "관심사": user_profile.get("interests"),
            "싫어하는 음식": user_profile.get("dislikedFoods"),
        }
        profile_text = "\n".join(
            f"{key}: {', '.join(value) if isinstance(value, list) else value}"
            for key, value in profile_items.items()
            if value
        )

    lines = [
        "당신은 고민 해결을 돕는 상담 전문가입니다. 아래 상담 요청에 답해주세요.",
        "답변은 500자 내외 안팎으로 간결하게 작성해주세요.",
        "",
        f"[고민 카테고리] {cat_text}",
        f"[조언 스타일] {style_guide}",
        f"[고민 제목] {title or '제목 없음'}",
        "",
        f"[사용자 정보]\n{profile_text or '등록된 정보 없음'}",
        "",
        "[고민 내용]",
        content,
        "",
        opt_lines or "[비교할 선택지] 없음\n",
        f"[판단 기준(중요도 1~5점)] {crit_text}",
        f"[추가 조건 및 특별한 상황] {conditions or '없음'}",
        "",
        "요구사항:",
        "1. 고민 내용에 나타난 사용자의 실제 고민과 선택 의도를 가장 먼저 파악합니다.",
        "2. 고민 내용에 등장하는 실제 비교 대상과 선택지 목록을 구분합니다.",
        "3. 고민 내용과 선택지가 다르면 누락된 대상과 추가된 대상을 간결하게 설명합니다.",
        "4. 고민 내용에 명시된 비교 대상은 선택지에 없더라도 제외하지 않습니다.",
        (
            "5. 선택지에만 등장하는 대상은 사용자가 실제로 고민한다고 단"
            "정하지 않습니다. 고민과 무관한 추가 선택지는 핵심 비교에서 "
            "제외합니다."
        ),
        (
            "6. 실제 장단점 비교는 고민 내용에서 밝힌 비교 대상을 중심으"
            "로 진행하고, 모든 대상에 동일한 판단 기준을 적용합니다."
        ),
        (
            "7. 판단 기준의 1~5점은 사용자가 해당 기준을 얼마나 중요하게 "
            "생각하는지 나타내는 중요도 점수입니다. 실제 예산, 가격, 소요"
            " 시간 등의 수치로 해석하지 않습니다."
        ),
        (
            "8. 중요도가 높은 판단 기준을 우선하되, 해당 기준의 실제 정보"
            "가 없다는 이유로 추천을 회피하거나 결론을 미루지 않습니다. "
            "확인할 수 없는 기준은 미확인으로 표시하고, 나머지 확인 가능"
            "한 판단 기준과 사용자 정보를 종합하여 가장 적합한 선택지를 "
            "하나 추천합니다. 추천 근거와 확인되지 않은 한계를 구분하여 "
            "설명하며, 이미 판단 가능한 경우 사용자에게 불필요한 재질문을"
            " 하지 않습니다."
        ),
        (
            "9. 기회비용은 실제 비교 대상 중 하나를 선택했을 때 포기하는 "
            "다른 대상의 가치를 설명합니다."
        ),
        (
            "10. 사용자가 제공하지 않은 취향, 목적, 예산, 시간, 주변 사람"
            "의 의견을 만들어내지 않습니다."
        ),
        "11. 객관적으로 확인 가능한 일반적인 특성과 사용자가 직접 제공한 정보를 구분합니다.",
        (
            "12. 음식의 특징은 실제 메뉴의 일반적인 특성을 바탕으로 설명"
            "하며 존재하지 않는 음식 조합이나 잘못된 특성을 만들어내지 않"
            "습니다."
        ),
        (
            "13. 사용자가 대안을 요청했다면 고민 중인 음식의 특성과 선택 "
            "고민에 직접 연결되는 대안을 제안합니다. 예를 들어 짬뽕과 짜"
            "장 중 고민한다면 짬짜면, 해산물을 좋아한다면 삼선짬뽕 등을 "
            "제안할 수 있습니다."
        ),
        (
            "14. 사용자가 제공하지 않은 취향은 조건부로 표현합니다. 예를 "
            "들어 '해산물을 좋아한다면 삼선짬뽕도 고려할 수 있습니다'라고"
            " 작성합니다."
        ),
        (
            "15. 근거가 부족하면 확인할 수 없다고 밝히며 '기본적이다', '"
            "무난하다'는 이유만으로 특정 선택지를 추천하지 않습니다."
        ),
        (
            "16. 최종 추천은 실제 고민 대상, 사용자 정보, 입력한 판단 기"
            "준을 종합하여 결정합니다. 근거가 부족하면 억지로 하나를 선택"
            "하지 않습니다."
        ),
        (
            "17. 사용자를 비난하거나 재촉하지 않습니다. '결정 장애로 시간"
            "을 낭비한다'와 같은 평가성 표현을 사용하지 않습니다."
        ),
        "18. 고민 요약, 비교, 기회비용 및 결론에서 같은 내용을 반복하지 않고 간결하게 작성합니다.",
        (
            "19. [사용자 정보]에 등록된 지역, 직업, 관심사, 취향, 싫어하"
            "는 음식, MBTI 등은 해당 고민과 관련된 경우 실제 판단 근거로 "
            "활용합니다. 정보를 단순히 언급하는 데 그치지 말고 선택지 비"
            "교, 장단점 분석, 대안 제안 및 최종 조언에 반영합니다."
        ),
        (
            "20. 사용자의 성격 및 MBTI 정보는 조언의 표현 방식과 접근 방"
            "법을 조정하는 참고 자료로 활용합니다. 성격이나 MBTI만으로 사"
            "용자의 생각과 행동을 단정하지 않습니다."
        ),
        (
            "21. 등록된 취향과 선호는 음식 및 관련 선택지를 판단할 때 적"
            "극적으로 반영합니다. 등록된 싫어하는 음식은 추천하거나 대안"
            "으로 제안하지 않습니다."
        ),
        (
            "22. 등록된 지역과 직업은 해당 고민의 생활 환경이나 현실적인 "
            "선택 조건과 관련 있을 때 활용합니다. 확인되지 않은 사실이나 "
            "상황을 추측하지 않습니다."
        ),
        (
            "23. [사용자 정보]가 '등록된 정보 없음'이거나 일부 항목이 비"
            "어 있다면 없는 정보를 만들어내지 않습니다. 등록된 정보만 활"
            "용하고 나머지는 고민 내용과 판단 기준을 바탕으로 답변합니다."
        ),
    ]
    return "\n".join(lines)
