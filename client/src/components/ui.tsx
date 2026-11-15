// 공통 부품 (design-tokens.md 4장). 모양은 App.css 의 클래스가, 색·크기는 토큰이 정한다.
import type { ReactNode } from 'react'
import type { Severity } from '../api.ts'
import { Icon, type IconName } from './Icon.tsx'

export const FOOTER_NOTICE = '이 결과는 참고용이며 최종 판단은 사용자가 합니다.'

interface PrimaryButtonProps {
  children: ReactNode
  onClick?: () => void
  disabled?: boolean
  busy?: boolean
  busyLabel?: string
  icon?: IconName
  wide?: 'home'
}

/** 주인공 버튼(화면당 1개). 누르면 비활성 + "처리 중…" 같은 글자로 바뀐다. */
export function PrimaryButton({ children, onClick, disabled, busy, busyLabel = '처리 중…', icon, wide }: PrimaryButtonProps) {
  return (
    <button
      type="button"
      className={`tr-primary${wide ? ' tr-primary--home' : ''}`}
      onClick={onClick}
      disabled={disabled || busy}
      aria-busy={busy || undefined}
    >
      {icon && !busy && <Icon name={icon} size={18} />}
      {busy ? busyLabel : children}
    </button>
  )
}

interface PillProps {
  children: ReactNode
  variant?: 'grey' | 'white' | 'link'
  icon?: IconName
  href?: string
  onClick?: () => void
  disabled?: boolean
}

/** 회색·흰·링크 알약. href 가 있으면 새 창으로 여는 링크가 된다. */
export function Pill({ children, variant = 'grey', icon, href, onClick, disabled }: PillProps) {
  const className = `tr-pill tr-pill--${variant}`
  const content = (
    <>
      {icon && <Icon name={icon} />}
      {children}
    </>
  )
  if (href) {
    return (
      <a className={className} href={href} target="_blank" rel="noopener noreferrer">
        {content}
      </a>
    )
  }
  return (
    <button type="button" className={className} onClick={onClick} disabled={disabled}>
      {content}
    </button>
  )
}

/** 누를 수 없는 회색 알약(모든 확인사항 카드의 "예매처에 재확인하세요"). */
export function Tag({ children, icon }: { children: ReactNode; icon?: IconName }) {
  return (
    <span className="tr-pill tr-pill--grey tr-pill--static">
      {icon && <Icon name={icon} />}
      {children}
    </span>
  )
}

/** PC 보조 행동: 아이콘 + 회색 글자 버튼. 모바일에서는 휴지통 원(CircleButton)을 쓴다. */
export function TextButton({ children, icon, onClick, disabled }: { children: ReactNode; icon: IconName; onClick: () => void; disabled?: boolean }) {
  return (
    <button type="button" className="tr-text-button" onClick={onClick} disabled={disabled}>
      <Icon name={icon} />
      {children}
    </button>
  )
}

export function CircleButton({ icon, label, onClick, disabled, size = 'cta' }: { icon: IconName; label: string; onClick: () => void; disabled?: boolean; size?: 'cta' | 'tool' }) {
  return (
    <button type="button" className={`tr-circle tr-circle--${size}`} onClick={onClick} disabled={disabled} aria-label={label} title={label}>
      <Icon name={icon} size={size === 'cta' ? 24 : 16} />
    </button>
  )
}

export function Card({ children, className = '', as: Tag = 'section' }: { children: ReactNode; className?: string; as?: 'section' | 'article' | 'div' }) {
  return <Tag className={`tr-card ${className}`}>{children}</Tag>
}

/** 안내 한 줄. 오류일 때는 같은 부품을 오류색으로. */
export function InfoLine({ children, tone = 'info' }: { children: ReactNode; tone?: 'info' | 'error' }) {
  return (
    <p className={`tr-info-line tr-info-line--${tone}`} role={tone === 'error' ? 'alert' : undefined}>
      <Icon name={tone === 'error' ? 'warning' : 'info'} size={18} />
      <span>{children}</span>
    </p>
  )
}

/** 가운데 정렬 오류 안내 + 알약들. */
export function ErrorNotice({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="tr-error-notice" role="alert">
      <Icon name="warning" size={20} />
      <p className="tr-error-notice__title">{title}</p>
      {children && <div className="tr-row tr-row--center">{children}</div>}
    </div>
  )
}

const SEVERITY_ICON: Record<Severity, IconName> = { INFO: 'info', CAUTION: 'warning', CHECK: 'search' }

/** 심각도 배지: 색만으로 구분하지 않고 아이콘 + 글자 라벨을 항상 같이. */
export function Badge({ severity, label }: { severity: Severity; label: string }) {
  return (
    <span className={`tr-badge tr-badge--${severity.toLowerCase()}`}>
      <Icon name={SEVERITY_ICON[severity]} size={14} />
      {label}
    </span>
  )
}

export function Skeleton({ lines = 2 }: { lines?: number }) {
  return (
    <div className="tr-skeleton" aria-hidden="true">
      <span className="tr-skeleton__bar tr-skeleton__bar--title" />
      {Array.from({ length: lines }, (_, i) => (
        <span key={i} className="tr-skeleton__bar" />
      ))}
    </div>
  )
}

/** 로딩 안내 알약(분홍 고리 스피너 + 글자). */
export function ProgressPill({ children }: { children: ReactNode }) {
  return (
    <p className="tr-progress" role="status">
      <span className="tr-progress__spinner" aria-hidden="true" />
      {children}
    </p>
  )
}

export function FooterNote() {
  return <p className="tr-footer-note">{FOOTER_NOTICE}</p>
}

/** 근거 칩: AI가 그 값을 읽은 판매글 구절. */
export function EvidenceChip({ quote }: { quote: string }) {
  return <span className="tr-chip">근거: “{quote}”</span>
}

export function Toggle({ checked, onChange, label }: { checked: boolean; onChange: (next: boolean) => void; label: string }) {
  return (
    <button type="button" role="switch" aria-checked={checked} aria-label={label} className="tr-toggle" onClick={() => onChange(!checked)}>
      <span className="tr-toggle__knob" />
    </button>
  )
}

/** 라벨은 입력칸 위, 오류는 칸 아래 빨간 글씨. */
export function Field({ label, error, hint, children, htmlFor }: { label: string; error?: string; hint?: ReactNode; children: ReactNode; htmlFor: string }) {
  return (
    <div className="tr-field">
      <label className="tr-field__label" htmlFor={htmlFor}>
        {label}
      </label>
      {children}
      {error ? (
        <p className="tr-field__error" role="alert">
          {error}
        </p>
      ) : (
        hint
      )}
    </div>
  )
}
