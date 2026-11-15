// 화면 ⑤ 관리자 규정 재확인: 공연 1건의 정가표와 규정 문장이 맞는지 보고 고친다.
// 비밀번호는 브라우저 기본 로그인창이 받는다(HTTP Basic). 이 화면에는 비밀번호 입력칸이 없다.
import { useEffect, useState, type ReactNode } from 'react'
import { api, type TicketEvent } from '../api.ts'
import { blankEvent, findUrlErrors, toDraft, toSaveBody } from '../admin.ts'
import { useRequest, type RequestState } from '../hooks.ts'
import { Card, ErrorNotice, InfoLine, Pill, PrimaryButton, Skeleton } from '../components/ui.tsx'
import { EventFields, PriceTable, RuleTable } from './AdminTables.tsx'

const TOAST_MS = 2000

function saveErrorMessage(state: RequestState<TicketEvent>): string | null {
  if (state.status !== 'error') return null
  const { status, message } = state.error
  return status === 0 || status >= 500 ? '저장하지 못했어요. 입력값은 그대로 있어요' : message
}

function useToast() {
  const [toast, setToast] = useState<string | null>(null)
  useEffect(() => {
    if (!toast) return
    const timer = setTimeout(() => setToast(null), TOAST_MS)
    return () => clearTimeout(timer)
  }, [toast])
  return [toast, setToast] as const
}

/** 관리자 공연 불러오기·편집·저장. original 은 "변경 취소"로 돌아갈 마지막 저장 상태. */
function useAdminEditor() {
  const load = useRequest(api.getAdminEvent)
  const save = useRequest(api.saveAdminEvent)
  const [original, setOriginal] = useState<TicketEvent | null>(null)
  const [draft, setDraft] = useState<TicketEvent | null>(null)
  const [toast, setToast] = useToast()
  const { run: runLoad } = load
  const replaceBoth = (next: TicketEvent) => {
    setOriginal(next)
    setDraft(next)
  }

  useEffect(() => {
    void runLoad().then((outcome) => {
      if (outcome.status === 'success') replaceBoth(outcome.data ? toDraft(outcome.data) : blankEvent())
    })
  }, [runLoad])

  const edit = (next: TicketEvent) => {
    if (save.state.status === 'error') save.reset()
    setDraft(next)
  }
  const submit = async (current: TicketEvent) => {
    const outcome = await save.run(toSaveBody(current))
    if (outcome.status !== 'success') return
    replaceBoth(toDraft(outcome.data))
    setToast(`저장했어요 · ${new Date().toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })}`)
  }
  return { loadState: load.state, saveState: save.state, original, draft, toast, edit, submit }
}

export function AdminScreen({ onHome }: { onHome: () => void }) {
  const { loadState, saveState, original, draft, toast, edit, submit } = useAdminEditor()
  if (loadState.status === 'error') return <AdminLoadError status={loadState.error.status} onRetry={() => window.location.reload()} onHome={onHome} />
  if (!draft || !original) return <AdminFrame><Card><Skeleton lines={5} /></Card></AdminFrame>

  const dirty = JSON.stringify(draft) !== JSON.stringify(original)
  const urlErrors = findUrlErrors(draft)
  const errors = { ...(saveState.status === 'error' ? saveState.error.fields : null), ...urlErrors }
  const saveError = saveErrorMessage(saveState)
  const saving = saveState.status === 'loading'
  const isEmpty = !dirty && JSON.stringify(original) === JSON.stringify(blankEvent())
  const actions = (
    <>
      <Pill icon="refresh" onClick={() => edit(original)} disabled={!dirty || saving}>변경 취소</Pill>
      <PrimaryButton icon="check" onClick={() => submit(draft)} disabled={!dirty || Object.keys(urlErrors).length > 0} busy={saving} busyLabel="저장하는 중…">
        {saveError ? '다시 저장' : '수정 내용 저장'}
      </PrimaryButton>
    </>
  )
  return (
    <AdminFrame actions={actions}>
      {isEmpty && <InfoLine>아직 공연 정보가 없어요. 공식 공지를 보고 첫 정가와 규정을 입력하세요</InfoLine>}
      {saveError && <InfoLine tone="error">{saveError}</InfoLine>}
      <EventFields event={draft} errors={errors} onChange={edit} />
      <PriceTable event={draft} errors={errors} onChange={edit} />
      <RuleTable event={draft} errors={errors} onChange={edit} />
      {toast && <p className="tr-toast" role="status">{toast}</p>}
    </AdminFrame>
  )
}

function AdminFrame({ actions, children }: { actions?: ReactNode; children: ReactNode }) {
  return (
    <div className="tr-page">
      <header className="tr-header tr-header--admin">
        <a className="tr-header__brand tr-header__brand--always" href="#/">티켓레이더 관리자</a>
        {actions && <div className="tr-header__actions">{actions}</div>}
      </header>
      <main className="tr-admin">
        <div className="tr-screen-head tr-screen-head--left">
          <h1 className="tr-screen-title">정가·규정 재확인</h1>
          <p className="tr-screen-desc">공식 공지와 맞는지 보고 고쳐 주세요. 저장하면 다음 확인부터 바로 쓰여요.</p>
        </div>
        {children}
      </main>
    </div>
  )
}

/** 401(비밀번호 없음·틀림·로그인창 취소)과 그 밖의 불러오기 실패. */
function AdminLoadError({ status, onRetry, onHome }: { status: number; onRetry: () => void; onHome: () => void }) {
  const title = status === 401 ? '관리자만 들어갈 수 있어요. 새로고침하면 다시 물어봐요' : '공연 정보를 불러오지 못했어요'
  return (
    <AdminFrame>
      <ErrorNotice title={title}>
        <Pill icon="refresh" onClick={onRetry}>다시 시도</Pill>
        <Pill icon="back" onClick={onHome}>홈으로 가기</Pill>
      </ErrorNotice>
    </AdminFrame>
  )
}
