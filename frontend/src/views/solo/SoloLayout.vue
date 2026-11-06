<script setup>
/**
 * 单人版外壳：顶部报头 + 底部三入口。
 *
 * 与多角色版的 AppShell 分开实现，因为导航形态不同：
 * 单人版是手机上的三个入口（今天 / 排期 / 客户），款式与财务降为二级，
 * 做成报头下方的一行小字，不占底部导航的位置。
 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../../store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const tabs = [
  { key: 'today', label: '今天', to: '/solo/today' },
  { key: 'schedule', label: '排期', to: '/solo/schedule' },
  { key: 'clients', label: '客户', to: '/solo/clients' }
]

const secondary = [
  { key: 'styles', label: '款式库', to: '/solo/styles' },
  { key: 'finance', label: '财务', to: '/solo/finance' }
]

const today = new Date()
const dateStamp = computed(() => {
  const month = String(today.getMonth() + 1).padStart(2, '0')
  const day = String(today.getDate()).padStart(2, '0')
  const weekday = ['日', '一', '二', '三', '四', '五', '六'][today.getDay()]
  return `${month}.${day} 周${weekday}`
})

function isActive(key) {
  return route.path.startsWith(`/solo/${key}`)
}

function go(to) {
  if (route.path !== to) {
    router.push(to)
  }
}

function handleLogout() {
  userStore.logout()
  router.replace('/login')
}
</script>

<template>
  <div class="solo">
    <header class="solo-bar">
      <div class="solo-bar-main">
        <div class="brand">
          <span class="brand-mark" aria-hidden="true">指</span>
          <span class="brand-text">
            <span class="brand-name">指间工作室</span>
            <span class="brand-sub mono">SOLO STUDIO</span>
          </span>
        </div>
        <div class="bar-right">
          <button type="button" class="link-btn" @click="handleLogout">退出</button>
        </div>
      </div>

      <div class="solo-bar-sub">
        <span class="sub-owner mono">{{ dateStamp }}</span>
        <nav class="sub-nav" aria-label="二级导航">
          <button
            v-for="item in secondary"
            :key="item.key"
            type="button"
            class="sub-link"
            :class="{ 'is-active': isActive(item.key) }"
            @click="go(item.to)"
          >
            {{ item.label }}
          </button>
        </nav>
      </div>
    </header>

    <main class="solo-main">
      <slot />
    </main>

    <nav class="tabbar" aria-label="主导航">
      <button
        v-for="(item, index) in tabs"
        :key="item.key"
        type="button"
        class="tab"
        :class="{ 'is-active': isActive(item.key) }"
        :aria-current="isActive(item.key) ? 'page' : undefined"
        @click="go(item.to)"
      >
        <span class="tab-index mono">{{ String(index + 1).padStart(2, '0') }}</span>
        <span class="tab-label">{{ item.label }}</span>
      </button>
    </nav>
  </div>
</template>

<style scoped>
.solo {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  max-width: 560px;
  margin: 0 auto;
  /* 底部导航 + iOS 安全区，避免最后一张卡被挡住 */
  padding-bottom: calc(66px + env(safe-area-inset-bottom));
}

/* ---- 报头 ---- */

.solo-bar {
  position: sticky;
  top: 0;
  z-index: 30;
  background: var(--paper);
  border-bottom: 1px solid var(--line);
}

.solo-bar-main {
  display: flex;
  align-items: center;
  gap: var(--s3);
  padding: var(--s3) var(--s4) var(--s2);
}

.brand {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--s2);
}

.brand-mark {
  display: grid;
  flex: none;
  width: 26px;
  height: 26px;
  place-items: center;
  background: var(--ink);
  color: var(--paper-raised);
  font-family: var(--font-display);
  font-size: 14px;
  line-height: 1;
}

.brand-text {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 1px;
}

.brand-name {
  font-family: var(--font-display);
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0.14em;
  line-height: 1.1;
  white-space: nowrap;
}

.brand-sub {
  color: var(--ink-4);
  font-size: 8px;
  letter-spacing: 0.22em;
  line-height: 1;
}

.bar-right {
  display: flex;
  flex: none;
  align-items: center;
  gap: var(--s3);
  margin-left: auto;
}

.link-btn {
  padding: 0;
  border: 0;
  border-bottom: 1px solid transparent;
  background: transparent;
  color: var(--ink-3);
  font-family: var(--font-ui);
  font-size: 11px;
  letter-spacing: 0.06em;
  cursor: pointer;
}

.link-btn:hover {
  border-bottom-color: var(--lacquer);
  color: var(--ink);
}

.solo-bar-sub {
  display: flex;
  align-items: center;
  gap: var(--s3);
  padding: 0 var(--s4) 6px;
}

.sub-owner {
  flex: none;
  color: var(--ink-4);
  font-size: 10px;
  letter-spacing: 0.1em;
}

.sub-nav {
  display: flex;
  flex: none;
  gap: var(--s3);
  margin-left: auto;
}

.sub-link {
  padding: 0 0 1px;
  border: 0;
  border-bottom: 1px solid transparent;
  background: transparent;
  color: var(--ink-3);
  font-family: var(--font-ui);
  font-size: 11px;
  letter-spacing: 0.08em;
  cursor: pointer;
}

.sub-link.is-active,
.sub-link:hover {
  border-bottom-color: var(--lacquer);
  color: var(--ink);
}

/* ---- 内容 ---- */

.solo-main {
  flex: 1 1 auto;
  width: 100%;
  padding: var(--s4) var(--s4) var(--s5);
}

/* ---- 底部三入口 ---- */

.tabbar {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 30;
  display: flex;
  max-width: 560px;
  margin: 0 auto;
  padding-bottom: env(safe-area-inset-bottom);
  border-top: 1px solid var(--line);
  background: var(--paper-raised);
}

.tab {
  position: relative;
  display: flex;
  flex: 1 1 0;
  min-width: 0;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: var(--s3) 0 var(--s2);
  border: 0;
  background: transparent;
  color: var(--ink-3);
  cursor: pointer;
  font-family: var(--font-ui);
}

.tab-index {
  color: var(--ink-4);
  font-size: 8px;
  letter-spacing: 0.12em;
  line-height: 1;
}

.tab-label {
  font-family: var(--font-display);
  font-size: 13px;
  letter-spacing: 0.1em;
  line-height: 1.2;
}

.tab.is-active {
  color: var(--ink);
}

.tab.is-active .tab-index {
  color: var(--lacquer);
}

.tab.is-active::before {
  position: absolute;
  top: -1px;
  left: 50%;
  width: 22px;
  height: 1.5px;
  background: var(--lacquer);
  content: "";
  transform: translateX(-50%);
}
</style>
