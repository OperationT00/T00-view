export type Role = 'ADMIN' | 'VIEWER'
export type Kind = 'VIDEO' | 'IMAGE_SET' | 'OTHER'
export type SourceType = 'LOCAL_DISK' | 'PIKPAK' | 'BAIDU'
export type ReportedStatus = 'UNKNOWN' | 'ONLINE' | 'OFFLINE'
export type LocatorType = 'LOCAL_PATH' | 'CLOUD_PATH' | 'WEB_URL'
export type Dimension = 'TYPE' | 'WORK' | 'CHARACTER' | 'SKIN' | 'CLOTHING' | 'CREATOR'
export type FacetMode = 'ALL' | 'ANY'
export type FacetPurpose = 'FILTER' | 'ASSOCIATE' | 'MANAGE'
export interface CreatorAccount { platform: string; account: string; homepage: string }
export interface Facet { id: string; dimension: Dimension; name: string; context: string; disambiguation: string | null; note: string | null; enabled: boolean; selectable: boolean; version: number; workScopeId: string | null; characterId: string | null; accounts: CreatorAccount[] }
export interface EffectiveFacet extends Facet { derivedFrom: { dimension: Dimension; id: string; name: string }[] }
export type FacetSelection = Record<Dimension, Facet[]>
export type FacetIds = Record<Dimension, string[]>
export const dimensions: Dimension[] = ['TYPE', 'WORK', 'CHARACTER', 'SKIN', 'CLOTHING', 'CREATOR']
export const dimensionNames: Record<Dimension, string> = { TYPE: '内容类型', WORK: '作品范围', CHARACTER: '角色', SKIN: '皮肤 / 造型', CLOTHING: '服装属性', CREATOR: '创作者' }
export const dimensionPaths: Record<Dimension, string> = { TYPE: 'types', WORK: 'work-scopes', CHARACTER: 'characters', SKIN: 'skins', CLOTHING: 'clothing', CREATOR: 'creators' }
export const dimensionParams: Record<Dimension, string> = { TYPE: 'type', WORK: 'work', CHARACTER: 'character', SKIN: 'skin', CLOTHING: 'clothing', CREATOR: 'creator' }
export const dimensionHints: Record<Dimension, string> = { TYPE: '例如 cos、zw、do，与文件形式分开', WORK: '原作、游戏或动漫背景，可留空', CHARACTER: '按原作与辨识备注区分同名角色', SKIN: '每个造型都明确属于一个角色', CLOTHING: '只选择收藏中实际出现的穿搭属性', CREATOR: '手动确认作者身份，可关联多位' }
export function emptyFacets(): FacetSelection { return { TYPE: [], WORK: [], CHARACTER: [], SKIN: [], CLOTHING: [], CREATOR: [] } }
export function facetIdsOf(selection: FacetSelection): FacetIds { return Object.fromEntries(dimensions.map(d => [d, selection[d].map(f => f.id)])) as FacetIds }
export function facetLabel(facet: Facet): string { return facet.context || facet.name }
export interface LegacyMapping { legacyTagId: string; targets: { dimension: Dimension; id: string }[] }
export interface LegacyMigration { tags: Tag[]; mappings: LegacyMapping[] }
export interface MigrationPreview { token: string; assets: { id: string; title: string; version: number; additions: FacetSelection; unmappedTags: Tag[] }[]; total: number }
export interface User { id: string; username: string; role: Role; enabled: boolean; version: number }
export interface Tag { id: string; name: string; parentId: string | null; path: string; version: number; children: Tag[] }
export interface Source { id: string; name: string; type: SourceType; reportedStatus: ReportedStatus; statusReportedAt: string | null; note: string | null; version: number }
export interface Location { id: string; sourceId: string; sourceName: string; sourceType: SourceType; reportedStatus: ReportedStatus; statusReportedAt: string | null; locatorType: LocatorType; locator: string; note: string | null }
export interface Asset { id: string; title: string; kind: Kind; description: string | null; favorite: boolean; version: number; createdAt: string; updatedAt: string; tags: Tag[]; directFacets: FacetSelection; effectiveFacets: Record<Dimension, EffectiveFacet[]>; locations: Location[] }
export interface Page<T> { items: T[]; total: number; page: number; size: number }
export interface LocationInput { sourceId: string; locatorType: LocatorType; locator: string; note: string }
export const kindNames: Record<Kind, string> = { VIDEO: '视频', IMAGE_SET: '图集', OTHER: '其他' }
export const sourceNames: Record<SourceType, string> = { LOCAL_DISK: '本地硬盘', PIKPAK: 'PikPak', BAIDU: '百度网盘' }
export const statusNames: Record<ReportedStatus, string> = { UNKNOWN: '未报告', ONLINE: '可用', OFFLINE: '离线' }
export const locatorNames: Record<LocatorType, string> = { LOCAL_PATH: '本地路径', CLOUD_PATH: '网盘目录', WEB_URL: '页面链接' }
export function flattenTags(tree: Tag[], depth = 0): (Tag & { depth: number })[] {
  return tree.flatMap(t => [{ ...t, depth }, ...flattenTags(t.children || [], depth + 1)])
}
export function normalizeTagSearch(value: string): string {
  return value.normalize('NFC').trim().toLowerCase()
}
export function matchesTagSearch(tag: Pick<Tag, 'name' | 'path'>, query: string): boolean {
  const normalized = normalizeTagSearch(query)
  return !normalized || normalizeTagSearch(tag.name).includes(normalized) || normalizeTagSearch(tag.path).includes(normalized)
}
export function formatDate(value?: string | null, full = false): string {
  if (!value) return '尚未报告'
  const date = new Date(value.endsWith('Z') || /[+-]\d\d:\d\d$/.test(value) ? value : `${value}Z`)
  if (Number.isNaN(date.getTime())) return '时间未知'
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', ...(full ? { hour: '2-digit', minute: '2-digit' } as const : {}) }).format(date)
}
export function safeLink(value: string): string | undefined {
  try { const url = new URL(value); return url.protocol === 'https:' && !url.username && !url.password ? url.href : undefined } catch { return undefined }
}
