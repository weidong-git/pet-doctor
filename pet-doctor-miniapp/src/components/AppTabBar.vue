<script setup lang="ts">
import { onMounted } from 'vue'
import { TAB_BAR_ITEMS } from '@/config/iconfont'
import { iconfontReady, loadIconfont } from '@/utils/iconfont'

const props = defineProps<{
  selected: number
}>()

const color = '#9ca3af'
const selectedIconColor = '#E9967A'
const selectedTextColor = '#1f2937'

onMounted(() => {
  void loadIconfont()
})

function switchTab(index: number, pagePath: string) {
  if (props.selected === index) return
  uni.switchTab({ url: pagePath })
}
</script>

<template>
  <view v-if="iconfontReady" class="app-tab-bar">
    <view class="app-tab-bar-border" />
    <view
      v-for="(item, index) in TAB_BAR_ITEMS"
      :key="item.pagePath"
      class="app-tab-bar-item"
      @tap="switchTab(index, item.pagePath)"
    >
      <text
        class="iconfont app-tab-icon"
        :style="{ color: selected === index ? selectedIconColor : color }"
      >{{ item.icon }}</text>
      <text
        class="app-tab-text"
        :style="{ color: selected === index ? selectedTextColor : color }"
      >{{ item.text }}</text>
    </view>
  </view>
</template>

<style scoped>
@import '@/static/iconfont/iconfont.css';

.app-tab-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 48px;
  background: #ffffff;
  display: flex;
  padding-bottom: env(safe-area-inset-bottom);
  box-sizing: content-box;
  z-index: 9999;
}

.app-tab-bar-border {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 1px;
  transform: scaleY(0.5);
  background-color: rgba(0, 0, 0, 0.06);
}

.app-tab-bar-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 48px;
}

.app-tab-icon {
  font-size: 22px;
  line-height: 24px;
  margin-bottom: 2px;
}

.app-tab-text {
  font-size: 10px;
  line-height: 14px;
}
</style>
