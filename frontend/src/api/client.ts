import { useAuthStore } from '@/stores/auth'
import { i18n } from '@/i18n'

/** An error from the API, parsed from the backend's ProblemDetail body. */
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public code?: string,
    public fieldErrors: Record<string, string> = {},
    public correlationId?: string,
  ) {
    super(message)
  }
}

export async function toApiError(res: Response): Promise<ApiError> {
  let body: Record<string, unknown> = {}
  try {
    body = await res.json()
  } catch {
    /* non-JSON error body (e.g. proxy error page) */
  }
  return new ApiError(
    res.status,
    (body.detail as string) ?? res.statusText ?? 'Request failed',
    body.code as string | undefined,
    (body.errors as Record<string, string>) ?? {},
    (body.correlationId as string) ?? res.headers.get('X-Correlation-Id') ?? undefined,
  )
}

const BASE = import.meta.env.VITE_API_URL ?? ''

/** fetch wrapper: adds the bearer token + language, turns non-2xx into ApiError, re-logs-in on 401. */
export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const auth = useAuthStore()
  const token = await auth.accessToken()
  const headers = new Headers(init.headers)
  if (token) headers.set('Authorization', `Bearer ${token}`)
  headers.set('Accept-Language', i18n.global.locale.value)
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')

  const res = await fetch(BASE + path, { ...init, headers })
  if (res.status === 401) {
    await auth.login()
    throw new ApiError(401, 'Session expired')
  }
  if (!res.ok) throw await toApiError(res)
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}
