<script setup>
/**
 * 单人版外壳。两套导航形态：
 *
 * - 手机（< 900px）：报头两行 + 底部三入口，款式 / 财务放二级；
 * - 电脑（≥ 900px）：导航挪进报头一行，底部栏收起，版心放宽到 1080。
 *
 * 二级页传 back-to，页面顶部会出现「← 返回」，不用靠浏览器后退。
 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../../store/user'

const props = defineProps({
  /** 'back' 表示浏览器后退；传路径则跳到该路径。 */
  backTo: { type: String, default: '' },
  backLabel: { type: String, default: '返回' }
})

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
  { key: 'materials', label: '物料', to: '/solo/materials' },
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

function goBack() {
  if (props.backTo === 'back') {
    router.back()
    return
  }
  router.push(props.backTo || '/solo/today')
}

function handleLogout() {
  userStore.logout()
  router.replace('/solo/login')
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

        <!-- 电脑端导航：和底部三入口同一套 key -->
        <nav class="top-nav" aria-label="主导航">
          <button
            v-for="item in tabs"
            :key="item.key"
            type="button"
            class="top-item"
            :class="{ 'is-active': isActive(item.key) }"
            @click="go(item.to)"
          >
            {{ item.label }}
          </button>
          <span class="top-divider" aria-hidden="true"></span>
          <button
            v-for="item in secondary"
            :key="item.key"
            type="button"
            class="top-item is-secondary"
            :class="{ 'is-active': isActive(item.key) }"
            @click="go(item.to)"
          >
            {{ item.label }}
          </button>
        </nav>

        <div class="bar-right">
          <span class="bar-date mono">{{ dateStamp }}</span>
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
      <button v-if="backTo" type="button" class="back-link" @click="goBack">
        <span class="back-arrow" aria-hidden="true">←</span> {{ backLabel }}
      </button>
      <slot />
    </main>

    <!-- 手机端底部三入口 -->
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
  flex: none;
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

/* 电脑端导航（手机隐藏） */
.top-nav {
  display: none;
}

.bar-right {
  display: flex;
  flex: none;
  align-items: center;
  gap: var(--s3);
  margin-left: auto;
}

.bar-date {
  display: none;
  color: var(--ink-4);
  font-size: 10px;
  letter-spacing: 0.1em;
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
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 11px;
  letter-spacing: 0.08em;
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

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: var(--s3);
  padding: 2px 0;
  border: 0;
  background: transparent;
  color: var(--ink-3);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 12px;
  letter-spacing: 0.04em;
  transition: color var(--dur-1) var(--ease);
}

.back-link:hover {
  color: var(--ink);
}

.back-arrow {
  font-family: var(--font-mono);
}

/* ---- 手机底部三入口 ---- */

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

/* ---- 电脑端 ---- */

@media (min-width: 900px) {
  .solo {
    max-width: 1080px;
    padding-bottom: 0;
  }

  .solo-bar-main {
    padding: var(--s4) var(--s6);
    gap: var(--s6);
  }

  .solo-bar-sub {
    display: none;
  }

  .tabbar {
    display: none;
  }

  .top-nav {
    display: flex;
    flex: 1 1 auto;
    align-items: baseline;
    gap: var(--s4);
    min-width: 0;
  }

  .top-item {
    position: relative;
    padding: 0 0 3px;
    border: 0;
    background: transparent;
    color: var(--ink-3);
    cursor: pointer;
    font-family: var(--font-ui);
    font-size: 13px;
    letter-spacing: 0.08em;
    white-space: nowrap;
    transition: color var(--dur-1) var(--ease);
  }

  .top-item:hover {
    color: var(--ink);
  }

  .top-item.is-active {
    color: var(--ink);
  }

  .top-item.is-active::after {
    position: absolute;
    right: 0;
    bottom: 0;
    left: 0;
    height: 1.5px;
    background: var(--lacquer);
    content: "";
  }

  .top-item.is-secondary {
    color: var(--ink-4);
    font-size: 12px;
  }

  .top-divider {
    width: 1px;
    height: 12px;
    background: var(--line);
    transform: translateY(2px);
  }

  .bar-date {
    display: inline;
  }

  .solo-main {
    padding: var(--s6);
  }

  .back-link {
    margin-bottom: var(--s4);
    font-size: 13px;
  }
}
</style>
