type Props = {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
  label: string;
};

/** 화면에 표시하는 페이지 번호는 1부터 시작합니다. */
export default function Pagination({
  page,
  totalPages,
  onChange,
  label,
}: Props) {
  if (totalPages < 1) return null;

  const current = Math.max(1, Math.min(page, totalPages));
  const visible = new Set([1, totalPages, current]);

  for (let offset = -1; offset <= 1; offset++) {
    const value = current + offset;

    if (value >= 1 && value <= totalPages) {
      visible.add(value);
    }
  }

  const numbers = [...visible].sort((a, b) => a - b);

  return (
    <nav className="pickme-pagination" aria-label={label}>
      <button
        type="button"
        disabled={current <= 1}
        onClick={() => onChange(current - 1)}
      >
        이전
      </button>

      <div className="pickme-page-numbers">
        {numbers.map((number, index) => (
          <span className="pickme-page-slot" key={number}>
            {index > 0 && number - numbers[index - 1] > 1 && (
              <span className="pickme-page-gap" aria-hidden="true">
                …
              </span>
            )}

            <button
              type="button"
              aria-label={`${number}페이지`}
              aria-current={number === current ? "page" : undefined}
              disabled={number === current}
              onClick={() => onChange(number)}
            >
              {number}
            </button>
          </span>
        ))}
      </div>

      <span className="pickme-page-status" aria-live="polite">
        {current} / {totalPages} 페이지
      </span>

      <button
        type="button"
        disabled={current >= totalPages}
        onClick={() => onChange(current + 1)}
      >
        다음
      </button>
    </nav>
  );
}
