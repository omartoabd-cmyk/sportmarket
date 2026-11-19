export function GearPlaceholder({ large = false }: { large?: boolean }) {
  return (
    <div
      className={`gear-placeholder ${large ? "gear-large" : ""}`}
      aria-hidden="true"
    >
      <svg viewBox="0 0 200 150" fill="none">
        <ellipse
          cx="101"
          cy="127"
          rx="61"
          ry="8"
          fill="currentColor"
          opacity=".06"
        />
        <path
          d="m43 54 17-10h80l17 10-8 66H51l-8-66Z"
          stroke="currentColor"
          strokeWidth="2"
        />
        <path
          d="M80 44v-8c0-24 40-24 40 0v8M43 54h114M64 55l4 65M137 55l-4 65"
          stroke="currentColor"
          strokeWidth="2"
        />
        <circle
          cx="102"
          cy="85"
          r="21"
          fill="var(--placeholder-bg)"
          stroke="currentColor"
          strokeWidth="2"
        />
        <path
          d="M81 85h42M102 64c-14 10-14 32 0 42M102 64c14 10 14 32 0 42M87 70c12 12 18 18 30 30"
          stroke="currentColor"
          strokeWidth="1.5"
        />
      </svg>
      <span>GOOD GEAR. MORE GAME.</span>
    </div>
  );
}
