<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Layout from './PikPakLayout.vue'
import CopyLinks from './CopyLinks.vue'
import OpenPikPak from './OpenPikPak.vue'
import { Folder, File, Film } from 'lucide-vue-next'
import { request, message, size, shareUrl, exportUrl, type Share, type Entry, type Page } from './api'
const route=useRoute(),router=useRouter(),share=ref<Share|null>(null),rows=ref<Entry[]>([]),cursor=ref(''),error=ref(''),busy=ref(false),password=ref(''),showPassword=ref(false),filter=ref('')
let generation=0
const parent=computed(()=>String(route.query.folder||''))
const crumbs=computed<Array<{id:string;name:string}>>(()=>{try{const p=JSON.parse(String(route.query.path||'[]'));return Array.isArray(p)?p.filter(x=>typeof x?.id==='string'&&typeof x?.name==='string').slice(0,32):[]}catch{return []}})
const filtered=computed(()=>rows.value.filter(e=>e.name.toLowerCase().includes(filter.value.toLowerCase())))
async function load(append=false){const seq=++generation;busy.value=true;error.value='';if(!append){rows.value=[];cursor.value=''}try{const id=String(route.params.id);const s=await request<Share>(`/shares/${encodeURIComponent(id)}`);if(seq!==generation)return;share.value=s;const data=await request<Page>(`/shares/${encodeURIComponent(id)}/entries?parentId=${encodeURIComponent(parent.value)}&cursor=${encodeURIComponent(append?cursor.value:'')}`);if(seq!==generation)return;share.value=s;rows.value=append?[...rows.value,...data.entries.filter(e=>!rows.value.some(r=>r.fileId===e.fileId))]:data.entries;cursor.value=data.nextCursor}catch(e){if(seq===generation)error.value=message(e)}finally{if(seq===generation)busy.value=false}}
function open(e:Entry){if(e.kind!=='FOLDER')return;router.push({path:`/shares/${route.params.id}`,query:{folder:e.fileId,path:JSON.stringify([...crumbs.value,{id:e.fileId,name:e.name}])}})}
function back(index:number){const p=crumbs.value.slice(0,index+1);router.push({path:`/shares/${route.params.id}`,query:p.length?{folder:p[p.length-1]!.id,path:JSON.stringify(p)}:{}})}
async function setPassword(){busy.value=true;try{const s=share.value||await request<Share>(`/shares/${route.params.id}`);await request(`/shares/${s.id}`,'PUT',{name:s.name,note:s.note,version:s.version,passcode:password.value});password.value='';showPassword.value=false;await load()}catch(e){error.value=message(e)}finally{busy.value=false}}
watch(()=>route.fullPath,()=>{filter.value='';share.value=null;load()},{immediate:true})
</script>
<template><Layout>
<RouterLink to="/shares" class="pk-action">← 全部在线文件夹</RouterLink>
<h1 style="margin-top:24px">{{share?.name || '在线文件夹'}}</h1>
<p class="pk-muted">目录与分享中的结构一致，文件保留在 PikPak。进入文件夹读取下一层内容。</p>
<div v-if="share" class="pk-panel"><p class="pk-muted">{{share.originalUrl ? "原始分享链接" : "标准链接（旧记录还原）"}}</p><p style="overflow-wrap:anywhere"><a :href="shareUrl(share)" target="_blank" rel="noopener noreferrer">{{shareUrl(share)}}</a></p><OpenPikPak :key="share.id + '-client'" :share="share" /><CopyLinks :key="share.id" :share="share" /><a class="pk-action" style="margin-left:12px" :href="exportUrl(share.id)" download="pikpak-links.txt">导出此分享链接</a></div>
<nav class="pk-breadcrumb" aria-label="文件路径"><button @click="back(-1)">根目录</button><template v-for="(c,i) in crumbs" :key="c.id"><span aria-hidden="true">/</span><button @click="back(i)">{{c.name}}</button></template></nav>
<div class="pk-row"><input v-model="filter" aria-label="筛选文件" placeholder="筛选当前已加载文件"><button v-if="crumbs.length" @click="back(crumbs.length-2)">返回上一级</button><button :disabled="busy" @click="load()">刷新目录</button><button @click="showPassword=!showPassword">设置提取码</button></div>
<form v-if="showPassword" @submit.prevent="setPassword" class="pk-panel pk-row"><input v-model="password" type="password" aria-label="提取码" placeholder="提取码" maxlength="128" autocomplete="off"><button :disabled="busy">保存并重试</button></form>
<p v-if="error" class="pk-error" role="alert">{{error}} <button :disabled="busy" @click="load()">重试读取</button></p>
<div class="pk-panel">
<div class="pk-file pk-file-head"><span class="pk-file-name-text">名称</span><span class="pk-file-type">类型</span><span class="pk-file-size">大小</span></div>
<p v-if="busy" class="pk-muted" role="status">正在读取 PikPak 目录…</p>
<p v-else-if="!filtered.length && !error" class="pk-muted">{{filter ? '当前已加载内容中没有匹配项。' : '此文件夹为空。'}}</p>
<div v-for="entry in filtered" :key="entry.fileId" class="pk-file">
<Folder v-if="entry.kind==='FOLDER'" :size="23" class="pk-file-icon"/><Film v-else-if="entry.kind==='VIDEO'" :size="23" class="pk-file-icon"/><File v-else :size="23" class="pk-file-icon"/>
<button v-if="entry.kind==='FOLDER'" class="pk-file-name" @click="open(entry)">{{entry.name}}</button><span v-else class="pk-file-name-text">{{entry.name}}</span>
<span class="pk-muted pk-file-type">{{entry.kind==='FOLDER'?'文件夹':entry.kind==='VIDEO'?'视频':'文件'}}</span><span class="pk-muted pk-file-size">{{entry.kind==='FOLDER'?'—':size(entry.sizeBytes)}}</span>
</div><button v-if="cursor" :disabled="busy" @click="load(true)" style="margin-top:20px">加载更多</button><p v-if="rows.length" class="pk-muted">当前目录已加载 {{rows.length}} 项{{cursor ? '，还有更多内容' : ''}}</p>
</div></Layout></template>
