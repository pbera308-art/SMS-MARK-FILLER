const tools=[
 {name:'FORMATIVE FILLER',desc:'Automatic marks filling',icon:'✎',url:'formative/popup.html'},
 {name:'TALK TO FILLER',desc:'Voice-controlled marks entry',icon:'🎙',url:'talk/popup.html'},
 {name:'LPCD FILLER',desc:'Qualitative data auto & manual',icon:'▤',url:'lpcd/popup.html'},
 {name:'BCO FILLER',desc:'A/B form group filler',icon:'▦',url:'bco/ab_form_filler_v2/popup.html'},
 {name:'EXCEL FILLER',desc:'Excel / CSV to webpage',icon:'▥',url:'excel/popup.html'}
];
const $=id=>document.getElementById(id);let current=null;
function render(filter=''){const box=$('cards');box.innerHTML='';tools.filter(t=>t.name.toLowerCase().includes(filter.toLowerCase())).forEach(t=>{const b=document.createElement('button');b.className='card';b.innerHTML=`<span class="ico">${t.icon}</span><b>${t.name}</b><small>${t.desc}</small>`;b.addEventListener('click',()=>openTool(t));box.appendChild(b);});}
function openTool(t){current=t;$('toolTitle').textContent=t.name;$('toolFrame').src=t.url;$('mainView').hidden=true;$('toolView').hidden=false;chrome.storage.local.set({bspLastTool:t.name});}
function back(){ $('toolFrame').src='about:blank';$('toolView').hidden=true;$('mainView').hidden=false;current=null;}
$('search').addEventListener('input',e=>render(e.target.value));$('backBtn').onclick=back;$('minBtn').onclick=$('toolMin').onclick=()=>parent.postMessage({type:'BSP_MINIMIZE'},'*');$('closeBtn').onclick=()=>parent.postMessage({type:'BSP_CLOSE'},'*');window.addEventListener('message',e=>{if(e.data?.type==='BSP_RESTORE')parent.postMessage({type:'BSP_RESTORE'},'*');});render();

$('miniRestore').onclick=()=>parent.postMessage({type:'BSP_RESTORE'},'*');window.addEventListener('message',e=>{if(e.data?.type==='BSP_MINIMIZED')document.body.classList.add('minimized');if(e.data?.type==='BSP_RESTORED')document.body.classList.remove('minimized');});$('topbar').addEventListener('pointerdown',e=>{if(e.target.closest('button'))return;parent.postMessage({type:'BSP_DRAG_START',x:e.screenX,y:e.screenY},'*');});
