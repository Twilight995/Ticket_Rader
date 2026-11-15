// 화면 ⑤의 편집 칸: 공연 정보, 정가표, 규정표. 각 행은 바로 고칠 수 있고, 저장 전까지는 "변경 취소"로 되돌릴 수 있다.
import type { ReactNode } from 'react'
import type { Category, EventRule, PriceTier, RuleType, TicketEvent } from '../api.ts'
import { CATEGORY_LABELS, RULE_LABELS } from '../labels.ts'
import { newRule, newTier } from '../admin.ts'
import { Card, CircleButton, Pill } from '../components/ui.tsx'

export type Errors = Record<string, string>


function Cell({ label, error, children, wide }: { label: string; error?: string; children: ReactNode; wide?: boolean }) {
  return (
    <label className={`tr-cell${wide ? ' tr-cell--wide' : ''}`}>
      <span className="tr-cell__label">{label}</span>
      {children}
      {error && <span className="tr-field__error" role="alert">{error}</span>}
    </label>
  )
}

interface EditProps {
  event: TicketEvent
  errors: Errors
  onChange: (event: TicketEvent) => void
}

export function EventFields({ event, errors, onChange }: EditProps) {
  const set = (patch: Partial<TicketEvent>) => onChange({ ...event, ...patch })
  return (
    <Card>
      <h2 className="tr-card__heading">공연</h2>
      <div className="tr-event-grid">
        <Cell label="공연명" error={errors.name} wide>
          <input className="tr-input" maxLength={100} value={event.name} onChange={(e) => set({ name: e.target.value })} />
        </Cell>
        <Cell label="일시" error={errors.startsAt}>
          <input className="tr-input" type="datetime-local" value={event.startsAt} onChange={(e) => set({ startsAt: e.target.value })} />
        </Cell>
        <Cell label="장소" error={errors.venue}>
          <input className="tr-input" maxLength={100} value={event.venue} onChange={(e) => set({ venue: e.target.value })} />
        </Cell>
        <Cell label="종류" error={errors.category}>
          <select className="tr-input" value={event.category} onChange={(e) => set({ category: e.target.value as Category })}>
            {(Object.keys(CATEGORY_LABELS) as Category[]).map((c) => <option key={c} value={c}>{CATEGORY_LABELS[c]}</option>)}
          </select>
        </Cell>
        <Cell label="공식 안내 원문 URL" error={errors.officialUrl} wide>
          <input className="tr-input" inputMode="url" placeholder="https://" value={event.officialUrl} onChange={(e) => set({ officialUrl: e.target.value })} />
        </Cell>
      </div>
    </Card>
  )
}

function replaceAt<T>(list: T[], index: number, patch: Partial<T>): T[] {
  return list.map((item, i) => (i === index ? { ...item, ...patch } : item))
}

export function PriceTable({ event, errors, onChange }: EditProps) {
  const tiers = event.priceTiers
  const setTiers = (priceTiers: PriceTier[]) => onChange({ ...event, priceTiers })
  return (
    <Card>
      <h2 className="tr-card__heading">정가표</h2>
      <div className="tr-table-head tr-table-head--price" aria-hidden="true">
        <span>좌석 등급</span><span>1장당 정가(원)</span><span>출처 URL</span><span>확인 날짜</span><span />
      </div>
      {tiers.map((tier, i) => {
        const key = `priceTiers[${i}]`
        return (
          <div key={i} className="tr-table-row tr-table-row--price">
            <Cell label="좌석 등급" error={errors[`${key}.grade`]}>
              <input className="tr-input" maxLength={30} value={tier.grade} onChange={(e) => setTiers(replaceAt(tiers, i, { grade: e.target.value }))} />
            </Cell>
            <Cell label="1장당 정가(원)" error={errors[`${key}.price`]}>
              <input className="tr-input" type="number" min={0} step={1000} value={tier.price} onChange={(e) => setTiers(replaceAt(tiers, i, { price: Number(e.target.value) }))} />
            </Cell>
            <Cell label="출처 URL" error={errors[`${key}.sourceUrl`]}>
              <input className="tr-input tr-input--link" inputMode="url" placeholder="https://" value={tier.sourceUrl} onChange={(e) => setTiers(replaceAt(tiers, i, { sourceUrl: e.target.value }))} />
            </Cell>
            <Cell label="확인 날짜" error={errors[`${key}.checkedOn`]}>
              <input className="tr-input" type="date" value={tier.checkedOn} onChange={(e) => setTiers(replaceAt(tiers, i, { checkedOn: e.target.value }))} />
            </Cell>
            <CircleButton icon="trash" size="tool" label={`'${tier.grade || '새'}' 정가 행 삭제`} onClick={() => setTiers(tiers.filter((_, j) => j !== i))} />
          </div>
        )
      })}
      <div>
        <Pill icon="plus" onClick={() => setTiers([...tiers, newTier()])}>정가 행 추가</Pill>
      </div>
    </Card>
  )
}

export function RuleTable({ event, errors, onChange }: EditProps) {
  const rules = event.rules
  const setRules = (next: EventRule[]) => onChange({ ...event, rules: next })
  return (
    <Card>
      <h2 className="tr-card__heading">규정표</h2>
      <div className="tr-table-head tr-table-head--rule" aria-hidden="true">
        <span>규정 종류</span><span>공식 문장</span><span>출처 URL</span><span>확인 날짜</span><span />
      </div>
      {rules.map((rule, i) => {
        const key = `rules[${i}]`
        return (
          <div key={i} className="tr-table-row tr-table-row--rule">
            <Cell label="규정 종류" error={errors[`${key}.type`]}>
              <select className="tr-input" value={rule.type} onChange={(e) => setRules(replaceAt(rules, i, { type: e.target.value as RuleType }))}>
                {(Object.keys(RULE_LABELS) as RuleType[]).map((t) => <option key={t} value={t}>{RULE_LABELS[t]}</option>)}
              </select>
            </Cell>
            <Cell label="공식 문장" error={errors[`${key}.text`]}>
              <input className="tr-input" maxLength={500} value={rule.text} onChange={(e) => setRules(replaceAt(rules, i, { text: e.target.value }))} />
            </Cell>
            <Cell label="출처 URL" error={errors[`${key}.sourceUrl`]}>
              <input className="tr-input tr-input--link" inputMode="url" placeholder="https://" value={rule.sourceUrl} onChange={(e) => setRules(replaceAt(rules, i, { sourceUrl: e.target.value }))} />
            </Cell>
            <Cell label="확인 날짜" error={errors[`${key}.checkedOn`]}>
              <input className="tr-input" type="date" value={rule.checkedOn} onChange={(e) => setRules(replaceAt(rules, i, { checkedOn: e.target.value }))} />
            </Cell>
            <CircleButton icon="trash" size="tool" label={`'${RULE_LABELS[rule.type]}' 규정 행 삭제`} onClick={() => setRules(rules.filter((_, j) => j !== i))} />
          </div>
        )
      })}
      <div>
        <Pill icon="plus" onClick={() => setRules([...rules, newRule()])}>규정 행 추가</Pill>
      </div>
    </Card>
  )
}
