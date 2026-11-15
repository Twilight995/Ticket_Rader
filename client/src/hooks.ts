// 데이터 가져오기·보내기와 주소(해시) 상태를 화면 밖으로 뺀 커스텀 훅.
import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError, api } from './api.ts'

export type RequestState<T> =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'success'; data: T }
  | { status: 'error'; error: ApiError }

/**
 * 요청 하나의 상태(idle → loading → success | error). run 은 끝난 상태(success | error)를 돌려준다.
 * 늦게 도착한 옛 응답은 버리고 idle 을 돌려준다(부른 쪽이 아무것도 하지 않게).
 */
export function useRequest<A extends unknown[], T>(fn: (...args: A) => Promise<T>) {
  const [state, setState] = useState<RequestState<T>>({ status: 'idle' })
  const latest = useRef(0)

  const run = useCallback(
    async (...args: A): Promise<RequestState<T>> => {
      const id = ++latest.current
      setState({ status: 'loading' })
      let outcome: RequestState<T>
      try {
        outcome = { status: 'success', data: await fn(...args) }
      } catch (e) {
        const error = e instanceof ApiError ? e : new ApiError(0, '잠시 문제가 생겼어요. 다시 시도해 주세요')
        outcome = { status: 'error', error }
      }
      if (id !== latest.current) return { status: 'idle' }
      setState(outcome)
      return outcome
    },
    [fn],
  )

  const reset = useCallback(() => {
    latest.current++
    setState({ status: 'idle' })
  }, [])

  return { state, run, reset }
}

/** 공연 1건 조회(⓪①②③이 같이 쓴다). 화면이 열리면 바로 부른다. */
export function useEvent() {
  const request = useRequest(api.getEvent)
  const { run } = request
  useEffect(() => {
    void run()
  }, [run])
  return request
}

export type Route = 'home' | 'input' | 'review' | 'result' | 'admin'
const ROUTES: Route[] = ['home', 'input', 'review', 'result', 'admin']

function readRoute(): Route {
  const name = window.location.hash.replace(/^#\/?/, '')
  return ROUTES.includes(name as Route) ? (name as Route) : 'home'
}

/**
 * 주소의 해시(#/input 등)로 화면을 고른다. 서버는 index.html 하나만 주면 되고,
 * 브라우저 뒤로 가기도 그대로 동작한다.
 */
export function useHashRoute() {
  const [route, setRoute] = useState<Route>(readRoute)
  useEffect(() => {
    const onChange = () => {
      setRoute(readRoute())
      window.scrollTo(0, 0)
    }
    window.addEventListener('hashchange', onChange)
    return () => window.removeEventListener('hashchange', onChange)
  }, [])
  const navigate = useCallback((next: Route) => {
    window.location.hash = next === 'home' ? '/' : `/${next}`
  }, [])
  return { route, navigate }
}
