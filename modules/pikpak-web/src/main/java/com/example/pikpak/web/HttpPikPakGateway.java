package com.example.pikpak.web;

import com.example.pikpak.*;
import com.example.pikpak.PikPakModels.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import static com.example.pikpak.PikPakException.Code.*;

/** Community share API adapter. No account login, transfer, deletion or arbitrary URL fetch. */
public final class HttpPikPakGateway implements PikPakShareGateway {
    private static final String CLIENT="YUMx5nI8ZU8Ap8pm", API="https://api-drive.mypikpak.com", USER="https://user.mypikpak.com";
    // Web protocol parameters referenced from TeamBreakerr/gopeed-extension-pikpak (MIT), see THIRD_PARTY_NOTICES.md.
    private static final String[] SALTS={"C9qPpZLN8ucRTaTiUMWYS9cQvWOE","+r6CQVxjzJV6LCV","F","pFJRC","9WXYIDGrwTCz2OiVlgZa90qpECPD6olt","/750aCr4lm/Sly/c","RB+DT/gZCrbV","","CyLsf7hdkIRxRm215hl","7xHvLi2tOYP0Y92b","ZGTXXxu8E/MIWaEDB+Sm/","1UI3","E7fP5Pfijd+7K+t6Tg/NhuLq0eEUVChpJSkrKxpO","ihtqpG6FMt65+Xk+tWUH2","NhXXU9rg4XXdzo7u5o"};
    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient http;
    public HttpPikPakGateway() { this(""); }
    public HttpPikPakGateway(String proxyUrl) {
        var builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER);
        if (proxyUrl != null && !proxyUrl.isBlank()) builder.proxy(ProxySelector.of(proxyAddress(proxyUrl)));
        http = builder.build();
    }
    static InetSocketAddress proxyAddress(String value) {
        try {
            URI uri = URI.create(value);
            if (!"http".equals(uri.getScheme()) || uri.getHost() == null || uri.getPort() < 1 || uri.getPort() > 65535
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) throw new IllegalArgumentException();
            return new InetSocketAddress(uri.getHost(), uri.getPort());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("pikpak.proxy-url must be an HTTP proxy URL with an explicit port and no credentials or path");
        }
    }
    private final Semaphore permits = new Semaphore(4);
    private record Context(String device, String captcha) {}
    @Override public Page list(PikPakShareLink share,String passcode,String parent,String cursor) {
        return call(() -> {
            Context ctx = context("GET:/drive/v1/share/detail");
            String token=passToken(ctx,share,passcode);
            JsonNode root=request(ctx,"GET",API+"/drive/v1/share/detail",Map.of("share_id",share.shareId(),"parent_id",parent,"page_token",cursor,"limit","100","with_audit","true","pass_code_token",token),null);
            check(root);
            List<Entry> entries=new ArrayList<>();
            for(JsonNode f:root.path("files")) {
                String mime=f.path("mime_type").asText(""); String name=f.path("name").asText("");
                Kind kind="drive#folder".equals(f.path("kind").asText())?Kind.FOLDER:
                    (mime.startsWith("video/")||name.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|mkv|webm|mov|avi|ts|m4v)$")?Kind.VIDEO:Kind.OTHER);
                Long size=f.path("size").isMissingNode()?null:nonnegative(f.path("size").asText(""));
                entries.add(new Entry(f.path("id").asText(),f.path("parent_id").asText(""),name,kind,size));
            }
            return new Page(entries,root.path("next_page_token").asText(""));
        });
    }
    @Override public List<Media> resolve(PikPakShareLink share,String passcode,String fileId) {
        return call(() -> {
            Context ctx=context("GET:/drive/v1/share/file_info");
            JsonNode root=request(ctx,"GET",API+"/drive/v1/share/file_info",Map.of("share_id",share.shareId(),"file_id",fileId,"pass_code_token",passToken(ctx,share,passcode)),null);
            check(root);
            JsonNode f=root.path("file_info");
            if(!fileId.equals(f.path("id").asText()))throw new PikPakException(FILE_UNAVAILABLE,"分享内没有此文件");
            List<Media> variants=new ArrayList<>();
            for(JsonNode m:f.path("medias")) {
                if(m.path("need_more_quota").asBoolean(false))continue;
                String url=m.path("link").path("url").asText(""); if(url.isBlank())continue;
                JsonNode video=m.path("video");
                String type=video.path("video_type").asText("");
                Protocol protocol=url.contains(".m3u8")?Protocol.HLS:(type.equals("mpegts")?Protocol.MPEG_TS:Protocol.PROGRESSIVE);
                try { variants.add(new Media(m.path("media_id").asText(),m.path("media_name").asText("视频"),mediaUri(url),protocol,
                    protocol==Protocol.HLS?"application/vnd.apple.mpegurl":protocol==Protocol.MPEG_TS?"video/mp2t":"video/mp4",
                    expiry(m.path("link").path("expire").asText("")),video.path("video_codec").asText(""),nonnegative(video.path("duration").asText(""))));
                } catch(IllegalArgumentException ignored) { /* unsupported candidate; do not log its signed URL */ }
            }
            if(variants.isEmpty()&&!f.path("web_content_link").asText("").isBlank())
                variants.add(new Media("original","原始文件",mediaUri(f.path("web_content_link").asText()),Protocol.PROGRESSIVE,f.path("mime_type").asText("video/mp4"),null));
            return List.copyOf(variants);
        });
    }
    public static URI mediaUri(String value) {
        URI uri=URI.create(value); String host=uri.getHost();
        if(!"https".equals(uri.getScheme())||host==null||!host.toLowerCase(Locale.ROOT).matches("dl-[a-z0-9-]+\\.mypikpak\\.(com|net)")
            ||uri.getUserInfo()!=null||(uri.getPort()!=-1&&uri.getPort()!=443)||uri.getFragment()!=null)
            throw new IllegalArgumentException("Unsupported media host");
        return uri;
    }
    private Context context(String action) {
        String device=UUID.randomUUID().toString().replace("-","");String ts=Long.toString(System.currentTimeMillis());
        String sign=CLIENT+"2.0.0mypikpak.com"+device+ts;
        try { for(String salt:SALTS)sign=HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest((sign+salt).getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e){throw new IllegalStateException("Digest unavailable");}
        Context initial=new Context(device,"");
        JsonNode result=request(initial,"POST",USER+"/v1/shield/captcha/init",Map.of(),Map.of("action",action,"captcha_token","","client_id",CLIENT,"device_id",device,
            "meta",Map.of("captcha_sign","1."+sign,"client_version","2.0.0","package_name","mypikpak.com","timestamp",ts,"user_id",""),"redirect_uri",""));
        if(!result.path("url").asText("").isEmpty())throw new PikPakException(UPSTREAM_CHALLENGE,"PikPak要求交互验证，暂时无法解析");
        String captcha=result.path("captcha_token").asText("");
        if(captcha.isEmpty())throw new PikPakException(UPSTREAM_UNAVAILABLE,"PikPak没有返回访问凭据");
        return new Context(device,captcha);
    }
    private String passToken(Context ctx,PikPakShareLink share,String passcode) {
        if(passcode==null||passcode.isBlank())return "";
        JsonNode result=request(ctx,"GET",API+"/drive/v1/share",Map.of("share_id",share.shareId(),"pass_code",passcode,"limit","100"),null);
        check(result);String token=result.path("pass_code_token").asText("");
        if(token.isEmpty())throw new PikPakException(WRONG_PASSWORD,"提取码未通过验证");return token;
    }
    private JsonNode request(Context ctx,String method,String address,Map<String,String> params,Object body) {
        StringJoiner query=new StringJoiner("&");params.forEach((k,v)->{if(v!=null&&!v.isEmpty())query.add(URLEncoder.encode(k,StandardCharsets.UTF_8)+"="+URLEncoder.encode(v,StandardCharsets.UTF_8));});
        URI uri=URI.create(address+(query.length()>0?"?"+query:""));
        HttpRequest.Builder b=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).header("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/117.0.0.0 Safari/537.36")
            .header("X-Client-ID",CLIENT).header("X-Device-ID",ctx.device()).header("X-Captcha-Token",ctx.captcha()).header("Content-Type","application/json");
        b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        try {
            var pending=http.sendAsync(b.build(),info -> new BoundedBodySubscriber(2*1024*1024));
            HttpResponse<byte[]> response;
            try { response=pending.get(15,java.util.concurrent.TimeUnit.SECONDS); }
            finally { if(!pending.isDone())pending.cancel(true); }
                if(response.statusCode()==429)throw new PikPakException(UPSTREAM_RATE_LIMITED,"PikPak请求过于频繁，请稍后重试");
                if(response.statusCode()==401||response.statusCode()==403)throw new PikPakException(UPSTREAM_AUTH_REQUIRED,"PikPak拒绝当前访问，可能需要授权或验证");
                if(response.statusCode()!=200)throw new PikPakException(UPSTREAM_UNAVAILABLE,"PikPak暂时无法响应");
                JsonNode node=json.readTree(response.body());check(node);return node;
        }catch(PikPakException e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new PikPakException(UPSTREAM_UNAVAILABLE,"请求已取消");}
        catch(Exception e){throw new PikPakException(UPSTREAM_UNAVAILABLE,"连接PikPak失败，请检查服务器网络后重试");}
    }
    static void check(JsonNode node) {
        int error=node.path("error_code").asInt(0);
        if(error==9)throw new PikPakException(UPSTREAM_CHALLENGE,"上游访问验证过期，请重试");
        if(error==10)throw new PikPakException(UPSTREAM_RATE_LIMITED,"上游请求频繁，请稍后重试");
        if(error!=0)throw new PikPakException(UPSTREAM_UNAVAILABLE,"PikPak返回接口错误");
        String status=node.path("share_status").asText("");
        if(status.equals("PASS_CODE_EMPTY"))throw new PikPakException(PASSWORD_REQUIRED,"此分享需要提取码");
        if(status.equals("PASS_CODE_ERROR"))throw new PikPakException(WRONG_PASSWORD,"提取码不正确");
        if(!status.isEmpty()&&!status.equals("OK"))throw new PikPakException(SHARE_UNAVAILABLE,"分享不可访问，请检查链接或稍后重试");
    }
    private static Long nonnegative(String value){try{long n=Long.parseLong(value);return n>=0?n:null;}catch(Exception e){return null;}}
    private static Instant expiry(String value){try{return Instant.parse(value);}catch(Exception e){return null;}}
    private <T>T call(java.util.function.Supplier<T> action){
        if(!permits.tryAcquire())throw new PikPakException(UPSTREAM_RATE_LIMITED,"正在处理其他分享，请稍后重试");
        try{return action.get();}finally{permits.release();}
    }
}
