<script setup lang="ts">
const pets = [
  { id: 1, name: '小贝', breed: '金毛', age: '3岁' },
  { id: 2, name: '富贵', breed: '布偶猫', age: '2岁' },
]
</script>

<template>
  <view class="min-h-screen bg-cream px-5 py-6">
    <text class="text-lg font-medium text-gray-800 mb-6 block">我的档案</text>

    <view
      v-for="pet in pets"
      :key="pet.id"
      class="bg-white rounded-2xl p-4 mb-3 shadow-sm flex items-center gap-4"
    >
      <view class="w-12 h-12 rounded-full bg-orange-100 flex items-center justify-center">
        <text class="text-xl">🐾</text>
      </view>
      <view>
        <text class="text-gray-800 font-medium block">{{ pet.name }}</text>
        <text class="text-gray-400 text-sm">{{ pet.breed }} · {{ pet.age }}</text>
      </view>
    </view>
  </view>
</template>
