<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { loadIconfont } from '@/utils/iconfont'

onLaunch(() => {
  void loadIconfont()
})
</script>

<template>
  <view />
</template>
