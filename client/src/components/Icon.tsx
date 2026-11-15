// 아이콘 몇 개를 선 그림 SVG 로 직접 둔다(아이콘 라이브러리를 새로 넣지 않으려고).
const PATHS = {
  info: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Zm0-5v-5m0-3h.01',
  warning: 'M12 4 2.5 20h19L12 4Zm0 6v4m0 3h.01',
  search: 'M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14Zm5-2 4.5 4.5',
  trash: 'M4 7h16M9 7V4h6v3m-8 0 1 13h8l1-13',
  chevronDown: 'm6 9 6 6 6-6',
  chevronRight: 'm9 6 6 6-6 6',
  back: 'm15 6-6 6 6 6',
  check: 'm5 12 5 5 9-10',
  close: 'm6 6 12 12M18 6 6 18',
  image: 'M4 5h16v14H4V5Zm0 11 5-5 4 4 2-2 5 5M15 9h.01',
  external: 'M14 4h6v6m0-6-9 9M18 14v6H4V6h6',
  plus: 'M12 5v14M5 12h14',
  refresh: 'M20 12a8 8 0 1 1-2.3-5.6M20 4v4h-4',
  doc: 'M6 3h9l3 3v15H6V3Zm3 7h6m-6 4h6m-6 4h4',
  arrowRight: 'M5 12h14m-6-6 6 6-6 6',
} as const

export type IconName = keyof typeof PATHS

export function Icon({ name, size = 16 }: { name: IconName; size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <path d={PATHS[name]} />
    </svg>
  )
}
