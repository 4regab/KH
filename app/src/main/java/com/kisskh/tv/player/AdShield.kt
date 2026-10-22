package com.kisskh.tv.player

import android.net.Uri
import android.util.Log
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.kisskh.tv.api.media.EmbedPolicy
import org.json.JSONObject

/**
 * Keeps ads off embed pages, whatever domain they come from. Embed sites load ad tags from rotating
 * domains, so a host list alone can't keep up. A small script is added to every frame of the page
 * (document start, all origins) and does three things:
 *
 * 1. Popups: `window.open` returns null, also for windows reached through an iframe (the trick vsembed
 *    uses to escape an override). Clicks on links that open a new tab or leave the site are cancelled.
 * 2. Overlays: every 1.5 s, and after DOM changes, it removes layers drawn over the page. A layer is
 *    removed when it is fixed or absolute, has z-index 100000 or more, holds no video and is not the
 *    page's main player frame, and either covers 20% of the screen, is an iframe or link to another
 *    site, or (z-index 2147480000 or more, the ad tags' own value) is an empty click trap.
 * 3. It tells this class what it did, through the `khShield` message listener, and each block is logged
 *    under [TAG].
 *
 * Needs WebView's document-start script and message listener features (WebView 87 and up). On older
 * WebViews [install] returns false and only the Kotlin side rules apply.
 * ponytail: the sweep skips open shadow roots, and treats the last two host labels as the site (so
 * `a.co.uk` and `b.co.uk` count as one).
 */
object AdShield {
    const val TAG = "KhAdShield"
    private const val NAME = "khShield"

    fun install(view: WebView): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
        ) {
            Log.w(TAG, "WebView too old for the page script; popups are still stopped by the navigation rules")
            return false
        }
        try {
            WebViewCompat.addWebMessageListener(view, NAME, setOf("*")) { _, message, _, isMainFrame, _ ->
                val data = runCatching { JSONObject(message.data ?: return@addWebMessageListener) }.getOrNull()
                    ?: return@addWebMessageListener
                Log.i(
                    TAG,
                    "${if (isMainFrame) "main" else "sub"} frame ${data.optString("f")}: ${data.optString("k")}" +
                        " ${data.optString("x")} tag=${data.optString("t")} size=${data.optInt("w")}x${data.optInt("h")}" +
                        " z=${data.optLong("z")} src=${data.optString("s")} total=${data.optInt("n")}",
                )
            }
            WebViewCompat.addDocumentStartJavaScript(view, script(EmbedPolicy.adHosts), setOf("*"))
            return true
        } catch (e: RuntimeException) {
            Log.w(TAG, "could not install the page script: $e")
            return false
        }
    }

    /** The page script with the blocked host list filled in. */
    internal fun script(hosts: List<String>): String =
        SCRIPT.replace("__HOSTS__", hosts.joinToString(",", "[", "]") { "\"$it\"" })

    private const val SCRIPT = """
(function(){
try{
var W=window,D=document;
if(W.__khShield)return;
try{Object.defineProperty(W,'__khShield',{value:1});}catch(e){W.__khShield=1;}
var ADS=__HOSTS__,SAFE=['challenges.cloudflare.com','hcaptcha.com','recaptcha.net','gstatic.com'];
var Z_AD=100000,Z_TAG=2147480000,SHARE=0.2,MAIN=0.3,MAXMSG=20,MAXKILL=40;
var SKIP=' HTML BODY HEAD SCRIPT STYLE LINK META VIDEO NOSCRIPT ';
var PLAYER='video,canvas,[id*="player" i],[class*="player" i],[class*="plyr" i],[class*="jw-" i],[class*="vjs" i],[class*="video" i],[class*="artplayer" i],[class*="dplayer" i],[class*="mejs" i]';
var SAFEQ='iframe[src*="challenges.cloudflare.com"],iframe[src*="hcaptcha"],iframe[src*="recaptcha"],[class*="turnstile" i],[id*="turnstile" i]';
var nHit=0,nMsg=0,killed=0,vw=0,vh=0,tmr=0,lastRun=0,started=false;

function site(h){return String(h||'').toLowerCase().split('.').slice(-2).join('.');}
function host(u){try{var a=D.createElement('a');a.href=String(u||'');return a.hostname||'';}catch(e){return '';}}
function own(){try{return site(W.location.hostname);}catch(e){return '';}}
function listed(h,L){h=String(h||'').toLowerCase();if(!h)return false;for(var i=0;i<L.length;i++){var x=L[i];if(h===x||(h.length>x.length&&h.slice(-x.length-1)==='.'+x))return true;}return false;}
function foreign(u){var h=host(u),o=own();return !!h&&!!o&&site(h)!==o;}
function zOf(cs){var z=parseInt(cs.zIndex,10);return isNaN(z)?0:z;}

function report(kind,el,extra){
 nHit++;
 if(nMsg>=MAXMSG)return;
 nMsg++;
 try{
  var b=W.khShield;if(!b||!b.postMessage)return;
  var m={k:kind,n:nHit,f:own()};
  if(extra)m.x=String(extra).slice(0,60);
  if(el&&el.getBoundingClientRect){var r=el.getBoundingClientRect();m.t=el.tagName;m.w=Math.round(r.width);m.h=Math.round(r.height);m.z=zOf(W.getComputedStyle(el));m.s=host(el.src||el.href||'');}
  b.postMessage(JSON.stringify(m));
 }catch(e){}
}

var stub=function open(){try{report('open',null,host(arguments[0]));}catch(e){}return null;};
try{stub.toString=function(){return 'function open() { [native code] }';};}catch(e){}
function lock(w){
 try{
  if(!w||w.open===stub)return;
  Object.defineProperty(w,'open',{get:function(){return stub;},set:function(){},configurable:false,enumerable:true});
 }catch(e){}
}
function wrap(proto,name,fix){
 try{
  var d=Object.getOwnPropertyDescriptor(proto,name);
  if(!d||!d.get)return;
  var orig=d.get;
  Object.defineProperty(proto,name,{configurable:true,enumerable:d.enumerable,get:function(){var v=orig.call(this);try{fix(v);}catch(e){}return v;}});
 }catch(e){}
}
lock(W);
try{
 wrap(W.HTMLIFrameElement.prototype,'contentWindow',lock);
 wrap(W.HTMLIFrameElement.prototype,'contentDocument',function(d){if(d)lock(d.defaultView);});
}catch(e){}

W.addEventListener('click',function(e){
 try{
  var p=e.composedPath?e.composedPath():[e.target],a=null;
  for(var i=0;i<p.length;i++){var n=p[i];if(n&&(n.tagName==='A'||n.tagName==='AREA')){a=n;break;}}
  if(!a)return;
  var href=a.getAttribute('href')||'';
  if(!href||href.charAt(0)==='#'||/^javascript:/i.test(href))return;
  var blank=String(a.target||'').toLowerCase()==='_blank';
  if(blank||foreign(a.href)){e.preventDefault();report('link',a,blank?'blank':'foreign');}
 }catch(x){}
},true);
W.addEventListener('submit',function(e){
 try{var f=e.target;if(f&&String(f.target||'').toLowerCase()==='_blank'){e.preventDefault();report('form',f,'blank');}}catch(x){}
},true);

function bigVideo(){var l=D.getElementsByTagName('video'),b=null,a=-1;for(var i=0;i<l.length;i++){var s=l[i].clientWidth*l[i].clientHeight;if(s>a){a=s;b=l[i];}}return b;}
function mainFrame(){
 var l=D.getElementsByTagName('iframe'),b=null,a=0;
 for(var i=0;i<l.length;i++){
  var f=l[i],r=f.getBoundingClientRect(),s=r.width*r.height;
  if(s<=a||listed(host(f.src),ADS)||zOf(W.getComputedStyle(f))>=Z_TAG)continue;
  a=s;b=f;
 }
 return a>=MAIN*vw*vh?b:null;
}
function player(el){try{return (el.matches&&el.matches(PLAYER))||!!el.querySelector(PLAYER);}catch(e){return false;}}
function bare(el){return !String(el.textContent||'').replace(/\s+/g,'')&&!el.querySelector('img,svg,video,canvas,button,input,iframe,a');}
function linksOut(el){
 var l=el.querySelectorAll('a[href],iframe[src]');
 for(var i=0;i<l.length&&i<10;i++){var u=l[i].href||l[i].src;if(foreign(u)&&!listed(host(u),SAFE))return true;}
 return false;
}
function judge(el,V,F){
 var tag=el.tagName;
 if(!tag||SKIP.indexOf(' '+tag+' ')>=0)return;
 var cs=W.getComputedStyle(el),pos=cs.position;
 if(pos!=='fixed'&&pos!=='absolute')return;
 var z=zOf(cs);
 if(z<Z_AD||cs.display==='none'||cs.visibility==='hidden')return;
 if(el===F||(V&&el.contains(V))||(F&&el.contains(F)))return;
 if((el.matches&&el.matches(SAFEQ))||el.querySelector(SAFEQ))return;
 var r=el.getBoundingClientRect(),big=r.width*r.height>=SHARE*vw*vh,why='';
 var src=(tag==='IFRAME'||tag==='A')?String(el.src||el.href||''):'';
 if(src&&(listed(host(src),ADS)||(foreign(src)&&!listed(host(src),SAFE))))why='foreign';
 else if(!player(el)){
  if(big)why='cover';
  else if(z>=Z_TAG&&(bare(el)||linksOut(el)))why='trap';
 }
 if(!why)return;
 report('sweep',el,why);
 killed++;
 try{el.parentNode.removeChild(el);}catch(e){}
}
function sweep(){
 lastRun=Date.now();
 try{
  if(killed>=MAXKILL)return;
  vw=W.innerWidth||0;vh=W.innerHeight||0;
  if(!vw||!vh||!D.documentElement)return;
  var V=bigVideo(),F=mainFrame(),list=[],i,c;
  c=D.documentElement.children;for(i=0;i<c.length;i++)list.push(c[i]);
  if(D.body){c=D.body.children;for(i=0;i<c.length;i++)list.push(c[i]);}
  c=D.querySelectorAll('[style*="z-index"]');for(i=0;i<c.length&&i<400;i++)list.push(c[i]);
  for(i=0;i<list.length;i++)if(list[i].parentNode)judge(list[i],V,F);
 }catch(e){}
}
function kick(){
 if(tmr)return;
 var d=400-(Date.now()-lastRun);
 tmr=setTimeout(function(){tmr=0;sweep();},d>60?d:60);
}
function start(){
 if(started)return;
 started=true;
 try{new W.MutationObserver(kick).observe(D.documentElement,{childList:true,subtree:true});}catch(e){}
 kick();
}
if(D.readyState==='loading')D.addEventListener('DOMContentLoaded',start);else start();
setInterval(sweep,1500);
}catch(e){}
})();
"""
}

/**
 * The chrome client both web views use. Pages here have no reason to open dialogs or windows, and
 * ad scripts do (alert and confirm boxes, popups), so all of them are refused. A refused dialog is
 * logged once per page under [AdShield.TAG].
 */
internal open class ShieldChromeClient : WebChromeClient() {
    private var noted: String? = null

    private fun note(kind: String, url: String?) {
        if (url == noted) return
        noted = url
        Log.i(AdShield.TAG, "refused a JS $kind from ${runCatching { Uri.parse(url).host }.getOrNull()}")
    }

    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        note("alert", url)
        result?.cancel()
        return true
    }

    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        note("confirm", url)
        result?.cancel()
        return true
    }

    override fun onJsPrompt(
        view: WebView?,
        url: String?,
        message: String?,
        defaultValue: String?,
        result: JsPromptResult?,
    ): Boolean {
        note("prompt", url)
        result?.cancel()
        return true
    }

    /** "Leave this page?" is answered yes with no box: cancelling would trap the player on a page it is leaving. */
    override fun onJsBeforeUnload(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        note("leave-page prompt", url)
        result?.confirm()
        return true
    }

    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: android.os.Message?,
    ): Boolean = false

    override fun onPermissionRequest(request: PermissionRequest?) {
        request?.deny()
    }

    override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
        callback?.invoke(origin, false, false)
    }
}
