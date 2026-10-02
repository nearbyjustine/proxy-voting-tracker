import { defineStore } from 'pinia'
import { UserManager, WebStorageStateStore, type User } from 'oidc-client-ts'
import type { Role } from '@/api/types'

/**
 * OIDC Authorization Code + PKCE against Keycloak. The SPA never sees a password or a client secret:
 * Keycloak shows the login page, and we exchange the returned code (+ PKCE verifier) for tokens.
 * Tokens live in sessionStorage (cleared when the tab closes), not localStorage.
 */
const manager = new UserManager({
  authority: import.meta.env.VITE_OIDC_AUTHORITY,
  client_id: import.meta.env.VITE_OIDC_CLIENT_ID,
  redirect_uri: `${window.location.origin}/callback`,
  post_logout_redirect_uri: `${window.location.origin}/`,
  response_type: 'code',
  scope: 'openid profile email',
  automaticSilentRenew: true,          // uses the refresh token before the 5-minute access token expires
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
})

function rolesOf(user: User | null): Role[] {
  if (!user?.access_token) return []
  try {
    const payload = JSON.parse(atob(user.access_token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
    return (payload.realm_access?.roles ?? []) as Role[]
  } catch {
    return []
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({ user: null as User | null, ready: false }),
  getters: {
    isAuthenticated: (s) => !!s.user && !s.user.expired,
    roles: (s): Role[] => rolesOf(s.user),
    displayName: (s) => (s.user?.profile.name as string) ?? s.user?.profile.preferred_username ?? '',
  },
  actions: {
    async init() {
      this.user = await manager.getUser()
      manager.events.addUserLoaded((u) => { this.user = u })
      manager.events.addUserUnloaded(() => { this.user = null })
      this.ready = true
    },
    hasRole(...roles: Role[]) {
      return roles.some((r) => this.roles.includes(r))
    },
    login(returnTo = window.location.pathname) {
      return manager.signinRedirect({ state: returnTo })
    },
    async handleCallback(): Promise<string> {
      const user = await manager.signinRedirectCallback()
      this.user = user
      return (user.state as string) || '/'
    },
    logout() {
      return manager.signoutRedirect()
    },
    async accessToken(): Promise<string | undefined> {
      if (this.user?.expired) {
        try {
          this.user = await manager.signinSilent()
        } catch {
          this.user = null
        }
      }
      return this.user?.access_token
    },
  },
})
