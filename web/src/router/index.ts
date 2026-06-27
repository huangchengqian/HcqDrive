import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useFilesStore } from '@/stores/files'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/home',
  },
  {
    path: '/pair',
    name: 'pair',
    component: () => import('@/views/PairView.vue'),
    meta: { public: true, layout: 'centered' },
  },
  {
    path: '/s/:token',
    name: 'share',
    component: () => import('@/views/ShareView.vue'),
    meta: { public: true, layout: 'centered' },
  },
  {
    path: '/home',
    name: 'home',
    component: () => import('@/views/HomeView.vue'),
    meta: { public: false, layout: 'app' },
  },
  {
    path: '/preview',
    name: 'preview',
    component: () => import('@/views/PreviewView.vue'),
    meta: { public: false, layout: 'full' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { public: false, layout: 'centered' },
  },
]

export const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior(_to, _from, saved) {
    return saved ?? { top: 0 }
  },
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  auth.hydrate()
  if (!to.meta.public && !auth.isAuthenticated) {
    return { name: 'pair' }
  }
  if (to.name === 'pair' && auth.isAuthenticated) {
    return { name: 'home' }
  }
  return true
})

router.afterEach((to) => {
  if (to.meta.layout === 'app') {
    const files = useFilesStore()
    if (files.entries.length === 0 && !files.isLoading) {
      void files.load('/')
    }
  }
})
