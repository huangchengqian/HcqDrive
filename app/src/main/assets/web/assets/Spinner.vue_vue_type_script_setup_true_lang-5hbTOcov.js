import{c as i,d as x,b as l,p as u,N as p,s as y,v as s,w as M,e as h,m,g as d,t as f,i as g,r as C,l as v,h as I,f as w,_ as B,L as T}from"./index-DtbNGDuE.js";/**
 * @license lucide-vue-next v0.460.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const P=i("ChevronRightIcon",[["path",{d:"m9 18 6-6-6-6",key:"mthhwq"}]]);/**
 * @license lucide-vue-next v0.460.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const R=i("CircleAlertIcon",[["circle",{cx:"12",cy:"12",r:"10",key:"1mglay"}],["line",{x1:"12",x2:"12",y1:"8",y2:"12",key:"1pkeuh"}],["line",{x1:"12",x2:"12.01",y1:"16",y2:"16",key:"4dfq90"}]]);/**
 * @license lucide-vue-next v0.460.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const $=i("InboxIcon",[["polyline",{points:"22 12 16 12 14 15 10 15 8 12 2 12",key:"o97t9d"}],["path",{d:"M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z",key:"oot6mr"}]]);/**
 * @license lucide-vue-next v0.460.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const H=i("MusicIcon",[["path",{d:"M9 18V5l12-2v13",key:"1jmyc2"}],["circle",{cx:"6",cy:"18",r:"3",key:"fqmcym"}],["circle",{cx:"18",cy:"16",r:"3",key:"1hluhg"}]]);/**
 * @license lucide-vue-next v0.460.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const O=i("PauseIcon",[["rect",{x:"14",y:"4",width:"4",height:"16",rx:"1",key:"zuxfzm"}],["rect",{x:"6",y:"4",width:"4",height:"16",rx:"1",key:"1okwgv"}]]);/**
 * @license lucide-vue-next v0.460.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const U=i("PlayIcon",[["polygon",{points:"6 3 20 12 6 21 6 3",key:"1oa8hb"}]]);/**
 * @license lucide-vue-next v0.460.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const S=i("RefreshCwIcon",[["path",{d:"M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",key:"v9h5vc"}],["path",{d:"M21 3v5h-5",key:"1q7to0"}],["path",{d:"M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",key:"3uifl3"}],["path",{d:"M8 16H3v5",key:"1cv678"}]]),E=["aria-label","title","disabled"],_=x({__name:"IconButton",props:{size:{default:"md"},tone:{default:"default"},label:{},disabled:{type:Boolean,default:!1}},setup(e){const t=e,r={sm:"h-8 w-8",md:"h-10 w-10",lg:"h-12 w-12"},n={default:"text-surface-600 hover:bg-surface-100 hover:text-surface-900 active:bg-surface-200 dark:text-surface-300 dark:hover:bg-surface-800 dark:hover:text-surface-50",primary:"text-primary-600 hover:bg-primary-50 hover:text-primary-700 active:bg-primary-100 dark:text-primary-400 dark:hover:bg-primary-500/10",danger:"text-danger-600 hover:bg-danger-50 hover:text-danger-700 active:bg-danger-100 dark:text-danger-500 dark:hover:bg-danger-500/10"},a=y(()=>["inline-flex items-center justify-center rounded-full transition duration-200 ease-out-soft focus-visible:ring-2 focus-visible:ring-primary-500/60 focus-visible:ring-offset-2 focus-visible:ring-offset-surface-50 dark:focus-visible:ring-offset-surface-950 disabled:cursor-not-allowed disabled:opacity-50",r[t.size],n[t.tone]].join(" ")),o=y(()=>t.size==="sm"?"h-4 w-4":t.size==="lg"?"h-6 w-6":"h-5 w-5");return(c,z)=>(s(),l("button",{type:"button",class:u(a.value),"aria-label":e.label,title:e.label,disabled:e.disabled},[p(c.$slots,"default",{iconSize:o.value})],10,E))}}),b=["B","KB","MB","GB","TB"];function G(e,t=1){if(!Number.isFinite(e)||e<0)return"-";if(e<1)return"0 B";const r=Math.min(b.length-1,Math.floor(Math.log(e)/Math.log(1024))),n=e/Math.pow(1024,r),a=b[r]??"B";return r===0?`${Math.round(n)} ${a}`:`${n.toFixed(t)} ${a}`}const k=new Intl.RelativeTimeFormat("zh-CN",{numeric:"auto"}),L=new Intl.DateTimeFormat("zh-CN",{year:"numeric",month:"2-digit",day:"2-digit",hour:"2-digit",minute:"2-digit"}),A=[["year",60*60*24*365],["month",60*60*24*30],["day",60*60*24],["hour",60*60],["minute",60],["second",1]];function K(e,t=Date.now()){if(!e||e<1e10)return"-";const r=(e-t)/1e3,n=Math.abs(r);for(const[a,o]of A)if(n>=o||a==="second")return k.format(Math.round(r/o),a);return k.format(0,"second")}function Z(e){return!e||e<1e10?"-":L.format(new Date(e))}const N=["aria-label"],F={class:"text-sm font-medium text-surface-700 dark:text-surface-200"},V={key:0,class:"mt-1 text-xs text-surface-500 dark:text-surface-400"},J=x({__name:"EmptyState",props:{title:{},description:{default:void 0},variant:{default:"empty"},retryLabel:{default:"重试"},loading:{type:Boolean,default:!1}},emits:["retry"],setup(e,{emit:t}){const r=e,n=t,a=C(!1);return M(()=>r.title,()=>{a.value=!1,requestAnimationFrame(()=>{a.value=!0,window.setTimeout(()=>a.value=!1,350)})}),(o,c)=>(s(),l("div",{class:u(["flex flex-col items-center justify-center gap-3 px-6 py-16 text-center animate-fade-in",a.value?"animate-shake":""]),role:"status","aria-label":e.title},[h("div",{class:u(["flex h-14 w-14 items-center justify-center rounded-full",e.variant==="error"?"bg-danger-50 text-danger-500 dark:bg-danger-500/10 dark:text-danger-400":"bg-surface-100 text-surface-400 dark:bg-surface-800 dark:text-surface-500"]),"aria-hidden":"true"},[e.variant==="error"?(s(),m(d(R),{key:0,size:26})):(s(),m(d($),{key:1,size:26}))],2),h("div",null,[h("p",F,f(e.title),1),e.description?(s(),l("p",V,f(e.description),1)):g("",!0)]),p(o.$slots,"action",{},()=>[e.variant==="error"?(s(),m(B,{key:0,variant:"secondary",size:"sm",loading:e.loading,onClick:c[0]||(c[0]=z=>n("retry"))},{icon:v(()=>[w(d(S),{size:14})]),default:v(()=>[I(" "+f(e.retryLabel),1)]),_:1},8,["loading"])):g("",!0)])],10,N))}}),j=["aria-label"],q={key:0,class:"text-xs"},Q=x({__name:"Spinner",props:{size:{default:"md"},label:{default:"加载中"},inline:{type:Boolean,default:!1}},setup(e){const t=e,r=y(()=>t.size==="sm"?"h-4 w-4":t.size==="lg"?"h-7 w-7":"h-5 w-5");return(n,a)=>(s(),l("span",{class:u(["inline-flex items-center gap-2 text-surface-500 dark:text-surface-400",e.inline?"":"justify-center"]),role:"status","aria-label":e.label},[w(d(T),{class:u([r.value,"animate-spin text-primary-500"]),"aria-hidden":"true"},null,8,["class"]),e.inline?g("",!0):(s(),l("span",q,f(e.label),1))],10,j))}});export{P as C,H as M,U as P,S as R,_,K as a,Q as b,Z as c,O as d,J as e,G as f};
