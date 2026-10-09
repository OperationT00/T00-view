<script setup lang="ts">
import { Link2, ExternalLink, Tags } from 'lucide-vue-next'
import { auth } from '../state'
import { dimensions, dimensionNames, dimensionParams, facetLabel, safeLink, type Asset, type EffectiveFacet, type Dimension } from '../types'
defineProps<{ asset: Asset }>()
function isDirect(asset: Asset, dimension: Dimension, id: string) { return asset.directFacets[dimension].some(f => f.id === id) }
function basis(facet: EffectiveFacet) { return facet.derivedFrom.map(f => `${dimensionNames[f.dimension]}：${f.name}`).join('；') }
</script>

<template>
  <section class="panel facet-detail-panel"><div class="section-line"><h2>六维标签</h2><Tags :size="17" class="muted" /></div><p class="field-note">直接标注来自管理员选择；角色、原作可按明确归属补充。服装不推导。</p><section v-for="dimension in dimensions" :key="dimension" class="facet-detail-dimension"><h3>{{ dimensionNames[dimension] }}</h3><p v-if="!asset.effectiveFacets[dimension].length" class="facet-blank">未标注</p><article v-for="facet in asset.effectiveFacets[dimension]" :key="facet.id" class="facet-detail-item" :class="{ derived: !isDirect(asset, dimension, facet.id) }"><RouterLink :to="{ path: '/assets', query: { [`${dimensionParams[dimension]}Ids`]: facet.id } }" class="facet-detail-link">{{ facetLabel(facet) }}</RouterLink><div class="facet-detail-origin"><span>{{ isDirect(asset, dimension, facet.id) ? '直接标注' : '关系补充' }}</span><span v-if="!facet.selectable" class="historical-label">{{ facet.enabled ? '归属链已停用' : '已停用' }}</span></div><p v-if="facet.derivedFrom.length" class="derived-basis"><Link2 :size="11" />{{ basis(facet) }}</p><details v-if="dimension === 'CREATOR' && (facet.accounts.length || facet.note)" class="creator-detail"><summary>创作者资料</summary><p v-if="facet.note" class="preserve-text">{{ facet.note }}</p><div v-for="(account, index) in facet.accounts" :key="index"><span>{{ account.platform }} · {{ account.account || '未填账号' }}</span><a v-if="safeLink(account.homepage)" :href="safeLink(account.homepage)" target="_blank" rel="noopener noreferrer" referrerpolicy="no-referrer" class="text-button">主页<ExternalLink :size="11" /></a></div></details></article></section></section>
  <section v-if="asset.tags.length" class="panel legacy-detail"><h2>旧标签 / 待归类</h2><div class="detail-tags"><span v-for="tag in asset.tags" :key="tag.id" class="legacy-path">{{ tag.path }}</span></div><p class="field-note">保留原始路径，未按名称猜测维度。</p><RouterLink v-if="auth.isAdmin.value" to="/tags?tab=legacy" class="text-button">人工映射与迁移</RouterLink></section>
</template>
