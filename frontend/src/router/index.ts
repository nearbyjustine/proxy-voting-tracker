import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import type { Role } from '@/api/types'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    roles?: Role[]
  }
}

const routes: RouteRecordRaw[] = [
  { path: '/', component: () => import('@/views/HomeView.vue'), meta: { public: true } },
  { path: '/callback', component: () => import('@/views/CallbackView.vue'), meta: { public: true } },
  { path: '/meetings', component: () => import('@/views/MeetingsView.vue'), meta: { roles: ['ANALYST', 'VOTER'] } },
  { path: '/meetings/:id', component: () => import('@/views/MeetingDetailView.vue'), props: true, meta: { roles: ['ANALYST', 'VOTER'] } },
  { path: '/policy', component: () => import('@/views/PolicyView.vue'), meta: { roles: ['ANALYST', 'POLICY_ADMIN'] } },
  { path: '/audit', component: () => import('@/views/AuditView.vue'), meta: { roles: ['POLICY_ADMIN'] } },
  { path: '/ingest', component: () => import('@/views/IngestView.vue'), meta: { roles: ['OPS'] } },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

export const router = createRouter({ history: createWebHistory(), routes })

export function canAccess(meta: { public?: boolean; roles?: Role[] }, authenticated: boolean, roles: Role[]): 'ok' | 'login' | 'forbidden' {
  if (meta.public) return 'ok'
  if (!authenticated) return 'login'
  if (meta.roles && !meta.roles.some((r) => roles.includes(r))) return 'forbidden'
  return 'ok'
}

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (!auth.ready) await auth.init()
  const decision = canAccess(to.meta, auth.isAuthenticated, auth.roles)
  if (decision === 'login') {
    await auth.login(to.fullPath)
    return false
  }
  if (decision === 'forbidden') return '/'
  return true
})
