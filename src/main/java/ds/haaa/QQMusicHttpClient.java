package ds.haaa;

import com.coloryr.allmusic.libs.org.apache.hc.client5.http.classic.methods.HttpGet;
import com.coloryr.allmusic.libs.org.apache.hc.client5.http.classic.methods.HttpPost;
import com.coloryr.allmusic.libs.org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import com.coloryr.allmusic.libs.org.apache.hc.core5.http.ContentType;
import com.coloryr.allmusic.libs.org.apache.hc.core5.http.HttpEntity;
import com.coloryr.allmusic.libs.org.apache.hc.core5.http.io.entity.EntityUtils;
import com.coloryr.allmusic.libs.org.apache.hc.core5.http.io.entity.StringEntity;
import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.music.MusicHttpClient;
import com.coloryr.allmusic.server.core.objs.CookieObj;
import com.coloryr.allmusic.server.core.objs.HttpResObj;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class QQMusicHttpClient {
    public static final String MUSICU_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg";
    public static final String LYRIC_URL = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg";
    public static final String SEARCH_OLD_URL = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp";
    public static final String SMARTBOX_URL = "https://c.y.qq.com/splcloud/fcgi-bin/smartbox_new.fcg";

    private static final String REFERER = "https://y.qq.com/";
    private static final String ORIGIN = "https://y.qq.com";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static final String COOKIE_FILE_NAME = "qqmusic_cookie.json";
    private static String COOKIE_FILE_PATH;
    private static List<CookieObj> qqCookiesList = new ArrayList<>();

    static {
        resolveCookiePath();
        loadQqCookies();
        if (QQSong.debug) {
            log("<gray>QQ音乐Cookie加载完成，使用路径：" + COOKIE_FILE_PATH + "，有效条目数=" + qqCookiesList.size());
        }
    }

    private static void resolveCookiePath() {
        String[] dirs = {"plugins/AllMusic", "plugins/allmusic", "allmusic_server"};
        for (String dir : dirs) {
            File f = new File(dir, COOKIE_FILE_NAME);
            if (f.exists()) {
                COOKIE_FILE_PATH = f.getAbsolutePath();
                return;
            }
        }
        COOKIE_FILE_PATH = new File("allmusic_server", COOKIE_FILE_NAME).getAbsolutePath();
        if (QQSong.debug) {
            log("<yellow>未找到现有QQ音乐Cookie文件，将使用默认路径：" + COOKIE_FILE_PATH);
        }
    }

    private static void loadQqCookies() {
        try {
            Path path = Paths.get(COOKIE_FILE_PATH);
            if (!Files.exists(path)) {
                if (QQSong.debug) {
                    log("<yellow>QQ音乐独立Cookie文件不存在：" + COOKIE_FILE_PATH + "，将使用匿名访问");
                }
                return;
            }
            String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            Type listType = new TypeToken<ArrayList<CookieObj>>() {}.getType();
            List<CookieObj> loaded = AllMusic.gson.fromJson(text, listType);
            if (loaded == null || loaded.isEmpty()) {
                if (QQSong.debug) {
                    log("<yellow>QQ音乐Cookie文件内容为空，将使用匿名访问");
                }
                return;
            }
            loaded.removeIf(cookie -> cookie == null || cookie.name == null || cookie.value == null
                    || cookie.name.trim().isEmpty() || cookie.value.trim().isEmpty());
            qqCookiesList = loaded;
            if (QQSong.debug) {
                log("<green>成功加载QQ音乐Cookie文件：" + COOKIE_FILE_PATH + "，有效条目=" + qqCookiesList.size());
            }
        } catch (Exception e) {
            if (QQSong.debug) {
                log("<red>加载QQ音乐独立Cookie文件失败: " + e.getMessage());
                e.printStackTrace();
            }
            qqCookiesList.clear();
        }
    }

    private static String getCookieValue(String name, String def) {
        if (name == null || name.isEmpty()) {
            return def;
        }
        for (CookieObj cookie : qqCookiesList) {
            if (name.equals(cookie.name)) {
                return cookie.value == null ? def : cookie.value;
            }
        }
        return def;
    }

    private static boolean hasCookieValue(String name) {
        return !getCookieValue(name, "").isEmpty();
    }

    public static boolean hasLoginCookie() {
        return hasCookieValue("qqmusic_key")
                || hasCookieValue("qm_keyst")
                || hasCookieValue("psrf_qqaccess_token")
                || hasCookieValue("psrf_qqrefresh_token")
                || hasCookieValue("wxrefresh_token");
    }

    public static String getUin() {
        String uin = getCookieValue("uin", "");
        if (!uin.isEmpty()) {
            return uin;
        }
        uin = getCookieValue("media_p_uin", "");
        return uin.isEmpty() ? "0" : uin;
    }

    private static String buildCookieHeader() {
        if (qqCookiesList.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        // 按优先级添加常见 cookie，也可以直接遍历全部，但这里保留原有逻辑以控制顺序
        appendCookie(builder, "login_type");
        appendCookie(builder, "tmeLoginType");
        appendCookie(builder, "euin");
        appendCookie(builder, "RK");
        appendCookie(builder, "_qpsvr_localtk");
        appendCookie(builder, "music_ignore_pskey");
        appendCookie(builder, "psrf_qqrefresh_token");

        String uin = getCookieValue("uin", "");
        if (uin.isEmpty()) {
            uin = getCookieValue("media_p_uin", "");
        }
        appendCookie(builder, "uin", uin);

        appendCookie(builder, "pgv_pvid");
        appendCookie(builder, "pgv_info");
        appendCookie(builder, "fqm_sessionid");
        appendCookie(builder, "fqm_pvqid");
        appendCookie(builder, "psrf_access_token_expiresAt");
        appendCookie(builder, "psrf_musickey_createtime");
        appendCookie(builder, "psrf_qqaccess_token");
        appendCookie(builder, "psrf_qqopenid");
        appendCookie(builder, "psrf_qqunionid");
        appendCookie(builder, "ptcz");
        appendCookie(builder, "qm_keyst");
        appendCookie(builder, "qqmusic_key");
        appendCookie(builder, "ts_last");
        appendCookie(builder, "ts_uid");
        appendCookie(builder, "wxunionid");
        appendCookie(builder, "wxrefresh_token");
        appendCookie(builder, "wxopenid");

        return builder.toString();
    }

    private static void appendCookie(StringBuilder builder, String name) {
        String value = getCookieValue(name, "");
        appendCookie(builder, name, value);
    }

    private static void appendCookie(StringBuilder builder, String name, String value) {
        if (name == null || name.isEmpty() || value == null || value.isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("; ");
        }
        builder.append(name).append("=").append(value);
    }

    public static HttpResObj get(String url) {
        return get(url, true);
    }

    public static HttpResObj getAnonymous(String url) {
        return get(url, false);
    }

    private static HttpResObj get(String url, boolean includeCookie) {
        try {
            HttpGet request = new HttpGet(url);
            setHeaders(request, includeCookie);
            if (QQSong.debug) {
                log("<gray>QQ音乐GET: " + url);
            }
            return execute(request, "QQ音乐GET请求失败：" + url);
        } catch (Exception e) {
            if (QQSong.debug) {
                log("<red>QQ音乐GET请求失败：" + url);
                e.printStackTrace();
            }
            return null;
        }
    }

    public static HttpResObj postJson(String json) {
        return postJson(MUSICU_URL, json);
    }

    public static HttpResObj postJson(String url, String json) {
        return postJson(url, json, true);
    }

    public static HttpResObj postJsonAnonymous(String url, String json) {
        return postJson(url, json, false);
    }

    private static HttpResObj postJson(String url, String json, boolean includeCookie) {
        try {
            HttpPost request = new HttpPost(url);
            setHeaders(request, includeCookie);
            request.setHeader("Content-Type", "application/json;charset=UTF-8");
            request.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
            if (QQSong.debug) {
                log("<gray>QQ音乐POST: " + url);
                log("<gray>QQ音乐POST Body: " + cut(json, 1200));
            }
            return execute(request, "QQ音乐POST请求失败：" + url);
        } catch (Exception e) {
            if (QQSong.debug) {
                log("<red>QQ音乐POST请求失败：" + url);
                e.printStackTrace();
            }
            return null;
        }
    }

    private static void setHeaders(
            com.coloryr.allmusic.libs.org.apache.hc.core5.http.HttpMessage request,
            boolean includeCookie
    ) {
        request.setHeader("User-Agent", UA);
        request.setHeader("Referer", REFERER);
        request.setHeader("Origin", ORIGIN);
        request.setHeader("Accept", "application/json, text/plain, */*");
        request.setHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        request.setHeader("Connection", "keep-alive");

        String cookie = includeCookie ? buildCookieHeader() : "";
        if (!cookie.isEmpty()) {
            request.setHeader("Cookie", cookie);
            if (QQSong.debug) {
                log("<gray>QQ音乐Cookie已注入，cookie：" + cookie);
            }
        } else if (!includeCookie) {
            if (QQSong.debug) {
                log("<gray>QQ音乐使用游客搜索请求，不注入Cookie");
            }
        } else {
            if (QQSong.debug) {
                log("<yellow>QQ音乐Cookie为空，将以未登录状态请求");
            }
        }
    }

    private static HttpResObj execute(
            com.coloryr.allmusic.libs.org.apache.hc.client5.http.classic.methods.HttpUriRequestBase request,
            String errorMsg
    ) {
        try (CloseableHttpResponse response = MusicHttpClient.client.execute(request)) {
            int httpCode = response.getCode();
            HttpEntity entity = response.getEntity();

            if (entity == null) {
                if (QQSong.debug) {
                    log("<red>QQ音乐返回空实体，HTTP=" + httpCode);
                }
                return null;
            }

            String body = read(entity.getContent());
            EntityUtils.consume(entity);

            boolean ok = httpCode >= 200 && httpCode < 300;
            if (QQSong.debug) {
                log("<gray>QQ音乐HTTP=" + httpCode + " 返回：" + cut(body, 1200));
            }

            if (!ok && QQSong.debug) {
                log("<red>QQ音乐服务器返回错误：" + cut(body, 1200));
            }

            return new HttpResObj(body, ok);
        } catch (Exception e) {
            if (QQSong.debug) {
                log("<red>" + errorMsg);
                e.printStackTrace();
            }
            return null;
        }
    }

    private static String read(InputStream inputStream) throws Exception {
        if (inputStream == null) {
            return "";
        }
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        inputStream.close();
        return result.toString(StandardCharsets.UTF_8.toString());
    }

    public static String enc(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.toString());
    }

    public static String cut(String value, int max) {
        if (value == null) {
            return "null";
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    public static void log(String msg) {
        if (!QQSong.debug) {
            return;
        }
        AllMusic.log.data("<light_purple>[AllMusic3]" + msg);
    }
}