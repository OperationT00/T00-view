<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Artplayer from 'artplayer'
import Hls from 'hls.js'
import mpegts from 'mpegts.js'
import Layout from './PikPakLayout.vue'
import { request, message, type Playback, type Media, type Entry, type Page } from './api'
const route=useRoute(),router=useRouter(),container=ref<HTMLDivElement>(),variants=ref<Media[]>([]),selected=ref(''),error=ref(''),busy=ref(false),files=ref<Entry[]>([]),notice=ref(''),continuation=ref(0),listCursor=ref(''),listBusy=ref(false),listError=ref('')
let listGeneration=0
const currentIndex=computed(()=>files.value.findIndex(f=>f.fileId===route.params.fileId))
function adjacent(delta:number){const f=files.value[currentIndex.value+delta];if(f)void router.push({path:`/shares/${route.params.id}/watch/${encodeURIComponent(f.fileId)}`,query:{...route.query,name:f.name}})}
let art:Artplayer|null=null, hls:Hls|null=null, ts:mpegts.Player|null=null, seq=0, automaticRetry=0, lastSaved=0, lastTime=0, activeKey=''
const progressDisabled=ref(false)
let bufferingTimer:ReturnType<typeof setTimeout>|null=null
function clearBufferingTimer(){if(bufferingTimer!==null){clearTimeout(bufferingTimer);bufferingTimer=null}}
function reportBuffering(){clearBufferingTimer();bufferingTimer=setTimeout(()=>{bufferingTimer=null;if(art&&!art.video.paused&&art.video.readyState<3)notice.value='视频持续缓冲，可能是网络波动。可点击“重新获取地址”从当前位置重试。'},12000)}
const title=computed(()=>String(route.query.name||'视频观看'))
const back=computed(()=>({path:`/shares/${route.params.id}`,query:{folder:String(route.query.parent||''),path:String(route.query.path||'[]')}}))
function save(){if(!art||!activeKey||art.video.readyState<1)return;const time=art.currentTime;if(!Number.isFinite(time)||!Number.isFinite(art.duration)||art.duration<=0)return;lastTime=time;if(progressDisabled.value)return;try{localStorage.setItem(activeKey,JSON.stringify({time,duration:art.duration,updated:Date.now()}))}catch{/* playback remains available */}}
function clearProgress(){try{localStorage.removeItem(`pikpak:progress:${route.params.id}:${route.params.fileId}`);progressDisabled.value=true;continuation.value=0}catch{error.value='浏览器拒绝访问本地存储，未能清除观看记录。'}}
function cleanup(){clearBufferingTimer();save();if(hls){hls.destroy();hls=null}if(ts){ts.destroy();ts=null}if(art){art.destroy(false);art=null}}
function mount(media:Media,start:number){cleanup();error.value='';selected.value=media.id;if(!container.value)return
  const type=media.protocol==='HLS'?'m3u8':media.protocol==='MPEG_TS'?'ts':'mp4'
  art=new Artplayer({container:container.value,url:media.uri,type,autoplay:false,theme:'#75975a',volume:.7,setting:true,playbackRate:true,fullscreen:true,fullscreenWeb:true,pip:true,playsInline:true,customType:{
    m3u8(video,url){if(video.canPlayType('application/vnd.apple.mpegurl'))video.src=url;else if(Hls.isSupported()){hls=new Hls();hls.loadSource(url);hls.attachMedia(video);hls.on(Hls.Events.ERROR,(_event,data)=>{if(data.fatal)error.value='此版本暂时无法播放，请切换版本或重新获取地址。'})}else error.value='当前浏览器不支持此播放格式'},
    ts(video,url){if(mpegts.isSupported()){ts=mpegts.createPlayer({type:'mpegts',url,isLive:false},{enableWorker:false});ts.attachMediaElement(video);ts.load();ts.on(mpegts.Events.ERROR,()=>{error.value='转码版本播放失败，请尝试原始版本或切换浏览器。'})}else error.value='当前浏览器不支持此转码格式'}
  }})
  activeKey=`pikpak:progress:${route.params.id}:${route.params.fileId}`
  art.on('video:loadedmetadata',()=>{if(!art)return;continuation.value=0;if(Number.isFinite(start)&&start>0&&start<art.duration-3){art.currentTime=start;continuation.value=start}notice.value='点击播放开始观看；可切换下面的媒体版本。'})
  art.on('video:timeupdate',()=>{if(Date.now()-lastSaved>15000){save();lastSaved=Date.now()}})
  art.on('video:waiting',reportBuffering)
  art.on('video:playing',()=>{clearBufferingTimer();notice.value='正在播放'})
  art.on('video:pause',clearBufferingTimer)
  art.on('video:ended',()=>{clearBufferingTimer();notice.value='播放结束'})
  art.on('video:pause',save);art.on('video:ended',save)
  art.on('video:error',()=>{save();error.value='播放失败：可能是地址失效、浏览器不支持编码或分享访问受限。';if(automaticRetry<1){automaticRetry++;void load(true)}})
}
async function load(refresh=false){const run=++seq;busy.value=true;error.value='';if(refresh)save();else{automaticRetry=0;cleanup();progressDisabled.value=false;activeKey='';lastTime=0;variants.value=[];notice.value='';continuation.value=0}try{
  const result=await request<Playback>('/playback','POST',{shareId:String(route.params.id),fileId:String(route.params.fileId)});if(run!==seq)return;variants.value=result.variants
  if(!variants.value.length)throw new Error('此视频没有可用的播放版本')
  let start=refresh?lastTime:0;if(!refresh){try{start=Number(JSON.parse(localStorage.getItem(`pikpak:progress:${route.params.id}:${route.params.fileId}`)||'{}').time)||0}catch{start=0}}
  const preferred=variants.value.find(v=>v.id===selected.value)||variants.value.find(v=>v.protocol==='PROGRESSIVE'&&v.videoCodec==='h264')||variants.value.find(v=>v.protocol==='PROGRESSIVE')||variants.value[0]!
  await nextTick();if(run===seq)mount(preferred,start)
}catch(e){if(run===seq)error.value=message(e)}finally{if(run===seq)busy.value=false}}
async function playlist(append=false){const generation=++listGeneration;listBusy.value=true;listError.value='';if(!append){files.value=[];listCursor.value=''}const previous=listCursor.value;try{const id=String(route.params.id);const page=await request<Page>(`/shares/${id}/entries?parentId=${encodeURIComponent(String(route.query.parent||''))}&cursor=${encodeURIComponent(append?previous:'')}`);if(generation!==listGeneration)return;files.value=[...files.value,...page.entries.filter(e=>e.kind==='VIDEO'&&!files.value.some(f=>f.fileId===e.fileId))];if(page.nextCursor&&page.nextCursor===previous){listCursor.value='';listError.value='目录分页未前进，请返回目录重新读取。'}else listCursor.value=page.nextCursor}catch(e){if(generation===listGeneration)listError.value=message(e)}finally{if(generation===listGeneration)listBusy.value=false}}
function choose(v:Media){const pos=art?.currentTime||lastTime;mount(v,pos)}
watch(()=>[route.params.id,route.params.fileId],()=>{selected.value='';void load()},{immediate:true})
watch(()=>`${route.params.id}/${String(route.query.parent||'')}`,()=>{void playlist()},{immediate:true})
const pageHide=()=>save();window.addEventListener('pagehide',pageHide)
onBeforeUnmount(()=>{seq++;listGeneration++;cleanup();window.removeEventListener('pagehide',pageHide)})
</script>
<template><Layout><RouterLink :to="back" class="pk-action">← 返回目录</RouterLink><h1 style="margin-top:24px">{{title}}</h1><p v-if="error" class="pk-error" role="alert">{{error}}</p><div class="pk-watch-grid"><section><div ref="container" class="pk-player"/><p class="pk-muted" aria-live="polite">{{busy?'正在获取播放地址…':notice}}</p><div class="pk-tags"><button v-for="v in variants" :key="v.id" :class="{active:v.id===selected}" @click="choose(v)" :disabled="busy">{{v.label}} <span class="pk-small">{{v.videoCodec}}</span></button></div><div class="pk-row" style="margin-top:18px"><button @click="load(true)" :disabled="busy">重新获取地址</button><button @click="art && (art.currentTime=0)">从头播放</button><button @click="clearProgress" :disabled="busy || progressDisabled">清除本视频观看记录</button><span v-if="progressDisabled" class="pk-muted">记录已清除，本次观看不再保存进度。</span><span v-else-if="continuation>5" class="pk-muted">已恢复上次进度</span></div><p class="pk-muted pk-small">媒体版本由分享源提供。如遇无画面或只能播放部分内容，请尝试其他版本；网页不会自动转存或删除网盘文件。</p></section><aside class="pk-watch-list"><strong>同目录视频</strong><div class="pk-row"><button :disabled="currentIndex<=0" @click="adjacent(-1)">上一条</button><button :disabled="currentIndex<0 || currentIndex>=files.length-1" @click="adjacent(1)">下一条</button></div><p v-if="listError" class="pk-error">{{listError}}</p><RouterLink v-for="f in files" :key="f.fileId" :class="{active:f.fileId===route.params.fileId}" :to="{path:`/shares/${route.params.id}/watch/${encodeURIComponent(f.fileId)}`,query:{...route.query,name:f.name}}">▷ {{f.name}}</RouterLink><button v-if="listCursor" :disabled="listBusy" @click="playlist(true)">加载更多视频</button></aside></div></Layout></template>
