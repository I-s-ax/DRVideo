package com.isa.tiktokdl;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracción de videos por red. Sin dependencias de la interfaz. */
final class Net {

    static final String UA =
        "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36";

    // ---------- X / Twitter ----------

    static final Pattern X_ID = Pattern.compile(
        "(?:twitter\\.com|[/.]x\\.com)/(?:[^/?#]+/){1,2}status(?:es)?/(\\d+)");
    static final String X_BEARER = "AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs"
        + "%3D1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA";

    /** who = {usuario, url de foto de perfil} */
    static List<String[]> fetchX(String url, String cookies) throws Exception {
        boolean logged = cookies != null && cookies.contains("auth_token=");
        if (url.contains("//t.co/")) {
            url = request("GET", url, null, new String[][]{{"User-Agent", "curl/8.0"}}).finalUrl;
        }
        Matcher m = X_ID.matcher(url);
        if (!m.find()) throw new Exception("No reconocí el enlace. Usa el de un post de X (…/status/123…).");
        String id = m.group(1);
        String[] who = {firstGroup(X_USER_URL, url), ""};
        if ("i".equals(who[0])) who[0] = "";

        String[] names = {"x", "fx", "vx", "sesion"};
        StringBuilder log = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            if (i == 3 && !logged) break;
            try {
                List<String> urls = i == 0 ? xSyndication(id, who) : i == 1 ? xFx(id, who)
                    : i == 2 ? xVx(id, who) : xAuth(id, cookies);
                if (!urls.isEmpty()) {
                    List<String[]> out = new ArrayList<String[]>();
                    for (int k = 0; k < urls.size(); k++) {
                        out.add(new String[]{urls.get(k),
                            "x_" + id + (urls.size() > 1 ? "_" + (k + 1) : "") + ".mp4",
                            who[0], who[1].replace("_normal.", "_400x400.")});
                    }
                    return out;
                }
                log.append(names[i]).append("=sin video; ");
            } catch (Exception e) {
                log.append(names[i]).append("=").append(e.getMessage()).append("; ");
            }
        }
        throw new Exception("No encontré video en ese post. Puede ser solo texto o fotos, de una cuenta privada "
            + "o con restricción de edad."
            + (logged ? "" : " Si es de una cuenta privada que sigues: ⚙ → Iniciar sesión en X.")
            + "\n[" + log.toString().trim() + "]");
    }

    /** Mejor variante MP4 de un bloque video_info. */
    static String xBest(JSONObject vi) throws Exception {
        org.json.JSONArray vars = vi == null ? null : vi.optJSONArray("variants");
        String best = null;
        int bestRate = -1;
        for (int k = 0; vars != null && k < vars.length(); k++) {
            JSONObject v = vars.getJSONObject(k);
            if (!"video/mp4".equals(v.optString("content_type", ""))) continue;
            int rate = v.optInt("bitrate", 0);
            if (rate > bestRate) {
                bestRate = rate;
                best = v.optString("url", "");
            }
        }
        return best == null || best.isEmpty() ? null : best;
    }

    /** API pública de X para posts incrustados. */
    static List<String> xSyndication(String id, String[] who) throws Exception {
        String abc = "123456789abcdefghijklmnopqrstuvwxyz";
        StringBuilder tok = new StringBuilder();
        java.util.Random rnd = new java.util.Random();
        for (int i = 0; i < 10; i++) tok.append(abc.charAt(rnd.nextInt(abc.length())));
        Resp r = request("GET", "https://cdn.syndication.twimg.com/tweet-result?id=" + id + "&token=" + tok,
            null, new String[][]{{"User-Agent", IG_UA}});
        if (r.code >= 400) throw new Exception("HTTP " + r.code);
        JSONObject j = new JSONObject(r.body);
        if ("TweetTombstone".equals(j.optString("__typename", ""))) throw new Exception("restringido");
        JSONObject xu = j.optJSONObject("user");
        if (xu != null) {
            if (!xu.optString("screen_name", "").isEmpty()) who[0] = xu.optString("screen_name", "");
            who[1] = xu.optString("profile_image_url_https", "");
        }
        List<String> out = new ArrayList<String>();
        org.json.JSONArray md = j.optJSONArray("mediaDetails");
        for (int i = 0; md != null && i < md.length(); i++) {
            String best = xBest(md.getJSONObject(i).optJSONObject("video_info"));
            if (best != null) out.add(best);
        }
        return out;
    }

    static List<String> xFx(String id, String[] who) throws Exception {
        Resp r = request("GET", "https://api.fxtwitter.com/status/" + id, null,
            new String[][]{{"User-Agent", "TikTokDL/2.6"}});
        if (r.code >= 400) throw new Exception("HTTP " + r.code);
        List<String> out = new ArrayList<String>();
        JSONObject tweet = new JSONObject(r.body).optJSONObject("tweet");
        JSONObject media = tweet == null ? null : tweet.optJSONObject("media");
        JSONObject fa = tweet == null ? null : tweet.optJSONObject("author");
        if (fa != null) {
            if (!fa.optString("screen_name", "").isEmpty()) who[0] = fa.optString("screen_name", "");
            if (who[1].isEmpty()) who[1] = fa.optString("avatar_url", "");
        }
        org.json.JSONArray vids = media == null ? null : media.optJSONArray("videos");
        for (int i = 0; vids != null && i < vids.length(); i++) {
            String v = vids.getJSONObject(i).optString("url", "");
            if (v.startsWith("http")) out.add(v);
        }
        return out;
    }

    static List<String> xVx(String id, String[] who) throws Exception {
        Resp r = request("GET", "https://api.vxtwitter.com/Twitter/status/" + id, null,
            new String[][]{{"User-Agent", "TikTokDL/2.6"}});
        if (r.code >= 400) throw new Exception("HTTP " + r.code);
        List<String> out = new ArrayList<String>();
        JSONObject vj = new JSONObject(r.body);
        if (!vj.optString("user_screen_name", "").isEmpty()) who[0] = vj.optString("user_screen_name", "");
        if (who[1].isEmpty()) who[1] = vj.optString("user_profile_image_url", "");
        org.json.JSONArray me = vj.optJSONArray("media_extended");
        for (int i = 0; me != null && i < me.length(); i++) {
            JSONObject o = me.getJSONObject(i);
            String type = o.optString("type", "");
            String v = o.optString("url", "");
            if (("video".equals(type) || "gif".equals(type)) && v.startsWith("http")) out.add(v);
        }
        return out;
    }

    /** Con sesión iniciada: API de la web de X (sirve para cuentas privadas que sigues). */
    static List<String> xAuth(String id, String cookies) throws Exception {
        String ct0 = cookieValue(cookies, "ct0");
        if (ct0 == null) throw new Exception("sesión incompleta, vuelve a iniciar");
        String[][] h = {
            {"User-Agent", IG_UA},
            {"Authorization", "Bearer " + X_BEARER},
            {"x-csrf-token", ct0},
            {"x-twitter-auth-type", "OAuth2Session"},
            {"x-twitter-active-user", "yes"},
            {"x-twitter-client-language", "en"},
            {"Referer", "https://x.com/"},
            {"Cookie", cookies},
        };
        String vars = "{\"tweetId\":\"" + id + "\",\"withCommunity\":false,"
            + "\"includePromotedContent\":false,\"withVoice\":false}";
        String[] on = {
            "creator_subscriptions_tweet_preview_api_enabled", "tweetypie_unmention_optimization_enabled",
            "responsive_web_edit_tweet_api_enabled", "graphql_is_translatable_rweb_tweet_is_translatable_enabled",
            "view_counts_everywhere_api_enabled", "longform_notetweets_consumption_enabled",
            "freedom_of_speech_not_reach_fetch_enabled", "standardized_nudges_misinfo",
            "tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled",
            "longform_notetweets_rich_text_read_enabled", "longform_notetweets_inline_media_enabled",
            "responsive_web_graphql_exclude_directive_enabled",
            "responsive_web_graphql_timeline_navigation_enabled",
        };
        String[] off = {
            "responsive_web_twitter_article_tweet_consumption_enabled", "tweet_awards_web_tipping_enabled",
            "verified_phone_label_enabled", "responsive_web_media_download_video_enabled",
            "responsive_web_graphql_skip_user_profile_image_extensions_enabled",
            "responsive_web_enhance_cards_enabled",
        };
        StringBuilder feats = new StringBuilder("{");
        for (String f : on) feats.append("\"").append(f).append("\":true,");
        for (String f : off) feats.append("\"").append(f).append("\":false,");
        feats.setCharAt(feats.length() - 1, '}');

        List<String> out = new ArrayList<String>();
        Resp r = request("GET", "https://x.com/i/api/graphql/2ICDjqPd81tulZcYrtpTuQ/TweetResultByRestId?variables="
            + URLEncoder.encode(vars, "UTF-8") + "&features=" + URLEncoder.encode(feats.toString(), "UTF-8"), null, h);
        if (r.code < 400) {
            try {
                xWalk(new JSONObject(r.body), out);
            } catch (Exception ignored) { }
        }
        if (out.isEmpty()) {
            Resp r2 = request("GET", "https://api.x.com/1.1/statuses/show/" + id + ".json?tweet_mode=extended", null, h);
            if (r2.code < 400) {
                try {
                    xWalk(new JSONObject(r2.body), out);
                } catch (Exception ignored) { }
            } else if (r.code >= 400) {
                throw new Exception("HTTP " + r.code + "/" + r2.code);
            }
        }
        return out;
    }

    /** Recorre cualquier respuesta JSON buscando bloques video_info. */
    static void xWalk(Object o, List<String> out) throws Exception {
        if (o instanceof JSONObject) {
            JSONObject j = (JSONObject) o;
            String best = xBest(j.optJSONObject("video_info"));
            if (best != null && !out.contains(best)) out.add(best);
            java.util.Iterator<String> it = j.keys();
            while (it.hasNext()) xWalk(j.opt(it.next()), out);
        } else if (o instanceof org.json.JSONArray) {
            org.json.JSONArray a = (org.json.JSONArray) o;
            for (int i = 0; i < a.length(); i++) xWalk(a.opt(i), out);
        }
    }

    // ---------- TikTok ----------

    static List<String[]> fetchTikTok(String url, String cookies, boolean hd) throws Exception {
        boolean logged = cookies != null && cookies.contains("sessionid=");
        try {
            return ttPublic(url, hd);
        } catch (Exception e) {
            if (!logged) {
                throw new Exception(e.getMessage()
                    + "\nSi es de una cuenta privada que sigues: ⚙ → Iniciar sesión en TikTok.");
            }
            try {
                return ttAuth(url, cookies);
            } catch (Exception e2) {
                throw new Exception("No pude obtener el video ni con tu sesión.\n[api=" + e.getMessage()
                    + "; sesion=" + e2.getMessage() + ";]");
            }
        }
    }

    static List<String[]> ttPublic(String url, boolean hd) throws Exception {
        String api = "https://www.tikwm.com/api/?hd=1&url=" + URLEncoder.encode(url, "UTF-8");
        JSONObject j = new JSONObject(http("GET", api, null, null));
        if (j.optInt("code", -1) != 0) {
            throw new Exception(j.optString("msg", "La API no respondió bien"));
        }
        JSONObject d = j.getJSONObject("data");
        // "hdplay" suele venir en H.265 a 1080p, que algunos teléfonos reproducen a tirones;
        // "play" es H.264, compatible con todo.
        String video = d.optString(hd ? "hdplay" : "play", "");
        if (video.isEmpty()) video = d.optString(hd ? "play" : "hdplay", "");
        if (video.isEmpty()) throw new Exception("No hay video (¿es un carrusel de fotos?)");
        if (video.startsWith("/")) video = "https://www.tikwm.com" + video;

        List<String[]> out = new ArrayList<String[]>();
        out.add(new String[]{video, "tiktok_" + d.optString("id", String.valueOf(System.currentTimeMillis())) + ".mp4"});
        JSONObject author = d.optJSONObject("author");
        String avatar = author == null ? "" : author.optString("avatar", "");
        if (avatar.startsWith("/")) avatar = "https://www.tikwm.com" + avatar;
        return withUser(out, author == null ? "" : author.optString("unique_id", ""), avatar);
    }

    static final Pattern TT_DATA = Pattern.compile(
        "<script[^>]+id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\"[^>]*>(.+?)</script>", Pattern.DOTALL);

    /** Con sesión iniciada: lee la página del video (cuentas privadas que sigues). */
    static List<String[]> ttAuth(String url, String cookies) throws Exception {
        String[][] h = {
            {"User-Agent", IG_UA},
            {"Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"},
            {"Accept-Language", "en-US,en;q=0.9"},
            {"Referer", "https://www.tiktok.com/"},
            {"Cookie", cookies},
        };
        Resp r = request("GET", url, null, h);
        if (r.code >= 400) throw new Exception("HTTP " + r.code);
        Matcher m = TT_DATA.matcher(r.body);
        if (!m.find()) throw new Exception("sin datos");
        JSONObject scope = new JSONObject(m.group(1)).optJSONObject("__DEFAULT_SCOPE__");
        JSONObject det = scope == null ? null : scope.optJSONObject("webapp.video-detail");
        JSONObject info = det == null ? null : det.optJSONObject("itemInfo");
        JSONObject item = info == null ? null : info.optJSONObject("itemStruct");
        if (item == null) {
            String msg = det == null ? "" : det.optString("statusMsg", "");
            throw new Exception(msg.isEmpty() ? "sin video" : msg);
        }
        JSONObject video = item.optJSONObject("video");
        String play = video == null ? "" : video.optString("playAddr", "");
        if (play.isEmpty() && video != null) {
            org.json.JSONArray br = video.optJSONArray("bitrateInfo");
            JSONObject pa = br == null || br.length() == 0 ? null : br.getJSONObject(0).optJSONObject("PlayAddr");
            org.json.JSONArray ul = pa == null ? null : pa.optJSONArray("UrlList");
            if (ul != null && ul.length() > 0) play = ul.optString(0, "");
        }
        if (play.isEmpty()) throw new Exception("sin video");

        JSONObject author = item.optJSONObject("author");
        String user = author == null ? "" : author.optString("uniqueId", "");
        String avatar = author == null ? "" : author.optString("avatarLarger", "");
        String id = item.optString("id", String.valueOf(System.currentTimeMillis()));
        String ck = r.cookies.isEmpty() ? cookies : cookies + "; " + r.cookies;
        List<String[]> out = new ArrayList<String[]>();
        // El CDN de TikTok exige las mismas cookies y Referer para entregar el archivo
        out.add(new String[]{play, "tiktok_" + id + ".mp4", user, avatar, ck, "https://www.tiktok.com/"});
        return out;
    }

    // ---------- Instagram ----------

    static final String IG_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";
    static final String IG_APP_ID = "936619743392459";
    static final String IG_DOC_ID = "8845758582119845";
    static final String IG_LSD = "AVqbxe3J_YA";
    static final Pattern IG_CODE = Pattern.compile(
        "instagr(?:am\\.com|\\.am)/(?:[^/?#]+/)?(?:p|reels?|tv)/([A-Za-z0-9_-]+)");

    /** La publicación existe pero no trae video: no tiene caso seguir intentando. */
    static final class NoVideo extends Exception {
        NoVideo(String m) { super(m); }
    }

    static String igShortcode(String url) {
        Matcher m = IG_CODE.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    static final Pattern IG_STORY = Pattern.compile("instagram\\.com/stories/[^/?#]+/(\\d+)");

    static List<String[]> fetchInstagram(String url, String cookies, String ua) throws Exception {
        boolean logged = cookies != null && cookies.contains("sessionid=");

        Matcher st = IG_STORY.matcher(url);
        if (st.find()) {
            if (!logged) throw new Exception("Las historias requieren sesión. Ve a ⚙ → Iniciar sesión en Instagram.");
            try {
                return igAuth(st.group(1), "story_" + st.group(1), cookies, ua);
            } catch (NoVideo e) {
                throw new Exception("Esa historia es una foto, no un video.");
            }
        }
        if (url.contains("/stories/")) throw new Exception("Abre la historia y copia el enlace de esa historia en concreto.");

        // Los enlaces instagram.com/share/... son cortos: hay que seguir la redirección
        if (url.contains("/share/")) {
            Resp r = request("GET", url, null, new String[][]{
                {"User-Agent", logged ? ua : IG_UA}, {"Cookie", logged ? cookies : ""}});
            if (r.finalUrl.contains("/share/") || igShortcode(r.finalUrl) == null) {
                throw new Exception("No pude abrir ese enlace corto. En Instagram usa ⋯ → Copiar enlace y pégalo aquí.");
            }
            url = r.finalUrl;
        }

        String code = igShortcode(url);
        if (code == null) throw new Exception("No reconocí el enlace. Usa uno de un reel o publicación.");

        StringBuilder log = new StringBuilder();
        if (logged) {
            try {
                List<String[]> out = igAuth(igPk(code), code, cookies, ua);
                if (!out.isEmpty()) return out;
                log.append("sesion=sin video; ");
            } catch (NoVideo e) {
                throw e;
            } catch (Exception e) {
                log.append("sesion=").append(e.getMessage()).append("; ");
            }
        }

        String[] names = {"api", "api2", "embed", "web"};
        for (int i = 0; i < names.length; i++) {
            try {
                List<String[]> out;
                if (i == 0) out = igApi(code);
                else if (i == 1) out = igApiLsd(code);
                else if (i == 2) out = igEmbed(code);
                else out = igWeb(code);
                if (!out.isEmpty()) return out;
                log.append(names[i]).append("=sin video; ");
            } catch (NoVideo e) {
                throw e;
            } catch (Exception e) {
                log.append(names[i]).append("=").append(e.getMessage()).append("; ");
            }
        }
        throw new Exception((logged
            ? "Instagram no entregó el video ni con tu sesión. Si dice que caducó, cierra sesión y vuelve a entrar en ⚙."
            : "Instagram pide iniciar sesión para este video. Ve a ⚙ → Iniciar sesión en Instagram.")
            + "\n[" + log.toString().trim() + "]");
    }

    /** Con sesión iniciada: API de medios, igual que la web de Instagram. */
    static List<String[]> igAuth(String pk, String label, String cookies, String ua) throws Exception {
        String csrf = cookieValue(cookies, "csrftoken");
        String[][] h = {
            {"User-Agent", ua},
            {"Accept", "*/*"},
            {"X-IG-App-ID", IG_APP_ID},
            {"X-ASBD-ID", "198387"},
            {"X-IG-WWW-Claim", "0"},
            {"X-Requested-With", "XMLHttpRequest"},
            {"X-CSRFToken", csrf == null ? "" : csrf},
            {"Origin", "https://www.instagram.com"},
            {"Referer", "https://www.instagram.com/"},
            {"Cookie", cookies},
        };
        Resp r = request("GET", "https://i.instagram.com/api/v1/media/" + pk + "/info/", null, h);
        if (r.code >= 400) {
            Resp r2 = request("GET", "https://www.instagram.com/api/v1/media/" + pk + "/info/", null, h);
            if (r2.code < 400) r = r2;
        }
        if (r.code == 401 || r.code == 403 || r.body.contains("login_required")) {
            throw new Exception("sesión caducada (HTTP " + r.code + ")");
        }
        if (r.code >= 400) throw new Exception("HTTP " + r.code);

        org.json.JSONArray items;
        try {
            items = new JSONObject(r.body).optJSONArray("items");
        } catch (Exception e) {
            throw new Exception("respuesta no JSON");
        }
        if (items == null || items.length() == 0) throw new Exception("sin datos");
        JSONObject it = items.getJSONObject(0);

        List<String[]> out = new ArrayList<String[]>();
        org.json.JSONArray car = it.optJSONArray("carousel_media");
        if (car != null) {
            int n = 0;
            for (int i = 0; i < car.length(); i++) {
                String v = igBestVersion(car.getJSONObject(i));
                if (v != null) {
                    n++;
                    out.add(new String[]{v, "ig_" + label + "_" + n + ".mp4"});
                }
            }
        } else {
            String v = igBestVersion(it);
            if (v != null) out.add(new String[]{v, "ig_" + label + ".mp4"});
        }
        if (out.isEmpty()) throw new NoVideo("Esa publicación no tiene video (solo fotos).");
        JSONObject owner = it.optJSONObject("user");
        return withUser(out, owner == null ? "" : owner.optString("username", ""),
            owner == null ? "" : owner.optString("profile_pic_url", ""));
    }

    static String igBestVersion(JSONObject media) throws Exception {
        org.json.JSONArray vv = media.optJSONArray("video_versions");
        String best = null;
        int bestW = -1;
        for (int i = 0; vv != null && i < vv.length(); i++) {
            JSONObject v = vv.getJSONObject(i);
            String u = v.optString("url", "");
            int w = v.optInt("width", 0);
            if (!u.isEmpty() && w > bestW) {
                bestW = w;
                best = u;
            }
        }
        return best;
    }

    static String[][] igHeaders(String code, String csrf, String cookies) {
        return new String[][]{
            {"User-Agent", IG_UA},
            {"Accept", "*/*"},
            {"X-IG-App-ID", IG_APP_ID},
            {"X-ASBD-ID", "198387"},
            {"X-IG-WWW-Claim", "0"},
            {"X-Requested-With", "XMLHttpRequest"},
            {"X-CSRFToken", csrf == null ? "" : csrf},
            {"Origin", "https://www.instagram.com"},
            {"Referer", "https://www.instagram.com/p/" + code + "/"},
            {"Cookie", cookies == null ? "" : cookies},
        };
    }

    /** Método 1: pide un csrftoken y consulta GraphQL por GET. */
    static List<String[]> igApi(String code) throws Exception {
        Resp pre = request("GET", "https://i.instagram.com/api/v1/web/get_ruling_for_content/"
            + "?content_type=MEDIA&target_id=" + igPk(code), null, igHeaders(code, null, null));
        String csrf = cookieValue(pre.cookies, "csrftoken");

        String vars = "{\"shortcode\":\"" + code + "\",\"child_comment_count\":3,\"fetch_comment_count\":40,"
            + "\"parent_comment_count\":24,\"has_threaded_comments\":true}";
        Resp r = request("GET", "https://www.instagram.com/graphql/query/?doc_id=" + IG_DOC_ID
            + "&variables=" + URLEncoder.encode(vars, "UTF-8"), null, igHeaders(code, csrf, pre.cookies));
        return igParse(r, code);
    }

    /** Método 2: GraphQL por POST con token lsd. */
    static List<String[]> igApiLsd(String code) throws Exception {
        String vars = "{\"shortcode\":\"" + code + "\",\"fetch_tagged_user_count\":null,"
            + "\"hoisted_comment_id\":null,\"hoisted_reply_id\":null}";
        String body = "variables=" + URLEncoder.encode(vars, "UTF-8")
            + "&doc_id=" + IG_DOC_ID + "&lsd=" + IG_LSD;
        String[][] h = {
            {"User-Agent", IG_UA},
            {"Content-Type", "application/x-www-form-urlencoded"},
            {"X-IG-App-ID", IG_APP_ID},
            {"X-FB-LSD", IG_LSD},
            {"X-ASBD-ID", "198387"},
            {"Sec-Fetch-Site", "same-origin"},
            {"Origin", "https://www.instagram.com"},
            {"Referer", "https://www.instagram.com/p/" + code + "/"},
        };
        return igParse(request("POST", "https://www.instagram.com/graphql/query", body, h), code);
    }

    static List<String[]> igParse(Resp r, String code) throws Exception {
        if (r.code >= 400) throw new Exception("HTTP " + r.code + (r.body.contains("login") ? " (pide login)" : ""));
        JSONObject root;
        try {
            root = new JSONObject(r.body);
        } catch (Exception e) {
            throw new Exception("respuesta no JSON");
        }
        JSONObject data = root.optJSONObject("data");
        JSONObject media = data == null ? null : data.optJSONObject("xdt_shortcode_media");
        if (media == null) {
            String msg = root.optString("message", "");
            throw new Exception(msg.isEmpty() ? "sin datos" : msg);
        }

        List<String[]> out = new ArrayList<String[]>();
        boolean flagged = false;
        JSONObject side = media.optJSONObject("edge_sidecar_to_children");
        if (side != null) {
            org.json.JSONArray edges = side.optJSONArray("edges");
            int n = 0;
            for (int i = 0; edges != null && i < edges.length(); i++) {
                JSONObject node = edges.getJSONObject(i).getJSONObject("node");
                if (!node.optBoolean("is_video")) continue;
                flagged = true;
                String v = node.optString("video_url", "");
                if (!v.isEmpty()) {
                    n++;
                    out.add(new String[]{v, "ig_" + code + "_" + n + ".mp4"});
                }
            }
        } else if (media.optBoolean("is_video")) {
            flagged = true;
            String v = media.optString("video_url", "");
            if (!v.isEmpty()) out.add(new String[]{v, "ig_" + code + ".mp4"});
        }
        if (!flagged) throw new NoVideo("Esa publicación no tiene video (solo fotos).");
        JSONObject owner = media.optJSONObject("owner");
        return withUser(out, owner == null ? "" : owner.optString("username", ""),
            owner == null ? "" : owner.optString("profile_pic_url", ""));
    }

    /** Método 3: página embed. */
    static List<String[]> igEmbed(String code) throws Exception {
        Resp r = request("GET", "https://www.instagram.com/p/" + code + "/embed/captioned/", null,
            new String[][]{{"User-Agent", IG_UA}});
        if (r.code >= 400) throw new Exception("HTTP " + r.code);
        return igScrape(r.body, code);
    }

    /** Método 4: página normal de la publicación. */
    static List<String[]> igWeb(String code) throws Exception {
        Resp r = request("GET", "https://www.instagram.com/reel/" + code + "/", null,
            new String[][]{{"User-Agent", IG_UA}, {"Accept", "text/html"}});
        if (r.code >= 400) throw new Exception("HTTP " + r.code);
        if (r.finalUrl.contains("/accounts/login")) throw new Exception("pide login");
        return igScrape(r.body, code);
    }

    static final Pattern[] IG_HTML = {
        Pattern.compile("video_url\\\\*\"\\s*:\\s*\\\\*\"(.+?)\\\\*\""),
        Pattern.compile("video_versions\\\\*\"\\s*:\\s*\\[\\s*\\{.{0,400}?\\\\*\"url\\\\*\"\\s*:\\s*\\\\*\"(.+?)\\\\*\""),
        Pattern.compile("property=\"og:video(?::secure_url)?\"\\s+content=\"(.+?)\""),
    };

    static List<String[]> igScrape(String html, String code) {
        List<String[]> out = new ArrayList<String[]>();
        List<String> seen = new ArrayList<String>();
        for (Pattern p : IG_HTML) {
            Matcher m = p.matcher(html);
            while (m.find()) {
                String v = unescape(m.group(1));
                if (!v.startsWith("http")) continue;
                String key = v.contains("?") ? v.substring(0, v.indexOf('?')) : v;
                if (seen.contains(key)) continue;
                seen.add(key);
                out.add(new String[]{v, "ig_" + code + (seen.size() > 1 ? "_" + seen.size() : "") + ".mp4"});
            }
            if (!out.isEmpty()) break;
        }
        return withUser(out, firstGroup(IG_USER_HTML, html), unescape(firstGroup(IG_PIC_HTML, html)));
    }

    /** Quita los escapes JSON (\/ y \u0026), aunque vengan escapados varias veces. */
    static String unescape(String s) {
        String r = s;
        for (int i = 0; i < 3 && r.contains("\\"); i++) {
            try {
                r = new JSONObject("{\"u\":\"" + r.replace("\"", "") + "\"}").getString("u");
            } catch (Exception e) {
                break;
            }
        }
        return r.replace("&amp;", "&");
    }

    /** Convierte el shortcode (base64 de Instagram) al id numérico. */
    static String igPk(String code) {
        String abc = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        if (code.length() > 28) code = code.substring(0, code.length() - 28);
        java.math.BigInteger n = java.math.BigInteger.ZERO;
        java.math.BigInteger b = java.math.BigInteger.valueOf(64);
        for (int i = 0; i < code.length(); i++) {
            n = n.multiply(b).add(java.math.BigInteger.valueOf(Math.max(0, abc.indexOf(code.charAt(i)))));
        }
        return n.toString();
    }

    static String cookieValue(String cookies, String name) {
        if (cookies == null) return null;
        for (String part : cookies.split(";\\s*")) {
            if (part.startsWith(name + "=")) return part.substring(name.length() + 1);
        }
        return null;
    }

    // ---------- Facebook ----------

    static final String[][] FB_HEADERS = {
        {"User-Agent", IG_UA},
        {"Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"},
        {"Accept-Language", "en-US,en;q=0.9"},
        {"Sec-Fetch-Dest", "document"},
        {"Sec-Fetch-Mode", "navigate"},
        {"Sec-Fetch-Site", "none"},
        {"Sec-Fetch-User", "?1"},
        {"Upgrade-Insecure-Requests", "1"},
    };
    // De mejor a peor calidad
    static final Pattern[] FB_HTML = {
        Pattern.compile("\"progressive_url\":\"([^\"]+)\",\"failure_reason\":[^,]*,\"metadata\":\\{\"quality\":\"HD\"\\}"),
        Pattern.compile("\"browser_native_hd_url\":\"([^\"]+)\""),
        Pattern.compile("\"playable_url_quality_hd\":\"([^\"]+)\""),
        Pattern.compile("\"?hd_src\"?:\"([^\"]+)\""),
        Pattern.compile("\"progressive_url\":\"([^\"]+)\""),
        Pattern.compile("\"browser_native_sd_url\":\"([^\"]+)\""),
        Pattern.compile("\"playable_url\":\"([^\"]+)\""),
        Pattern.compile("\"?sd_src(?:_no_ratelimit)?\"?:\"([^\"]+)\""),
        Pattern.compile("property=\"og:video(?::secure_url|:url)?\"\\s+content=\"([^\"]+)\""),
    };
    static final Pattern FB_VID = Pattern.compile("(?:/videos/(?:[^/?#]+/)?|/reel/|[?&]v=|story_fbid=|/watch/\\?v=)(\\d+)");

    static String fbScrape(String html) {
        for (Pattern p : FB_HTML) {
            Matcher m = p.matcher(html);
            while (m.find()) {
                String v = unescape(m.group(1));
                if (v.startsWith("http")) return v;
            }
        }
        return null;
    }

    static List<String[]> fetchFacebook(String url, String cookies) throws Exception {
        boolean logged = cookies != null && cookies.contains("c_user=");
        try {
            return fbFetch(url, null);
        } catch (Exception e) {
            if (!logged) {
                throw new Exception(e.getMessage() + "\nSi es privado o de un grupo: ⚙ → Iniciar sesión en Facebook.");
            }
            try {
                return fbFetch(url, cookies);
            } catch (Exception e2) {
                throw new Exception(e2.getMessage().replace("No encontré el video.", "No encontré el video ni con tu sesión."));
            }
        }
    }

    static List<String[]> fbFetch(String url, String cookies) throws Exception {
        String[][] headers = new String[FB_HEADERS.length + 1][];
        System.arraycopy(FB_HEADERS, 0, headers, 0, FB_HEADERS.length);
        headers[FB_HEADERS.length] = new String[]{"Cookie", cookies == null ? "" : cookies};

        url = url.replaceFirst("//(?:m|mbasic|web|touch)\\.facebook\\.com", "//www.facebook.com");
        StringBuilder log = new StringBuilder();
        String video = null;
        String owner = "";
        String page = url;

        // 1) Página del video (también resuelve fb.watch y /share/)
        try {
            Resp r = request("GET", url, null, headers);
            page = r.finalUrl;
            if (r.code >= 400) log.append("web=HTTP ").append(r.code).append("; ");
            else {
                video = fbScrape(r.body);
                owner = fbOwner(r.body, r.finalUrl);
                if (video == null) {
                    log.append(r.finalUrl.contains("/login") || r.body.contains("login_form")
                        ? "web=pide login; " : "web=sin video; ");
                }
            }
        } catch (Exception e) {
            log.append("web=").append(e.getMessage()).append("; ");
        }

        // 2) Reproductor incrustable
        if (video == null) {
            try {
                String target = page.contains("/login") ? url : page;
                Resp r = request("GET", "https://www.facebook.com/plugins/video.php?show_text=0&href="
                    + URLEncoder.encode(target, "UTF-8"), null, headers);
                if (r.code >= 400) log.append("embed=HTTP ").append(r.code).append("; ");
                else {
                    video = fbScrape(r.body);
                    if (owner.isEmpty()) owner = fbOwner(r.body, page);
                    if (video == null) log.append("embed=sin video; ");
                }
            } catch (Exception e) {
                log.append("embed=").append(e.getMessage()).append("; ");
            }
        }

        if (video == null) {
            throw new Exception("No encontré el video. Puede ser privado, de un grupo cerrado, "
                + "o Facebook pidió iniciar sesión.\n[" + log.toString().trim() + "]");
        }
        Matcher m = FB_VID.matcher(page);
        String id = m.find() ? m.group(1) : String.valueOf(System.currentTimeMillis());
        List<String[]> out = new ArrayList<String[]>();
        out.add(new String[]{video, "fb_" + id + ".mp4", owner});
        return out;
    }

    // ---------- Nombre del perfil ----------

    /** Agrega el usuario como tercer dato de cada video. */
    static List<String[]> withUser(List<String[]> items, String user) {
        List<String[]> out = new ArrayList<String[]>();
        for (String[] it : items) out.add(new String[]{it[0], it[1], user == null ? "" : user});
        return out;
    }

    /** Agrega usuario y foto de perfil: {url, archivo, usuario, avatar}. */
    static List<String[]> withUser(List<String[]> items, String user, String avatar) {
        List<String[]> out = new ArrayList<String[]>();
        for (String[] it : items) {
            out.add(new String[]{it[0], it[1], user == null ? "" : user, avatar == null ? "" : avatar});
        }
        return out;
    }

    static final Pattern IG_PIC_HTML = Pattern.compile("\\\\*\"profile_pic_url\\\\*\"\\s*:\\s*\\\\*\"(.+?)\\\\*\"");

    /** Descarga un archivo pequeño (foto de perfil). */
    static boolean downloadTo(String u, java.io.File dest) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", IG_UA);
            if (c.getResponseCode() >= 400) return false;
            java.io.File tmp = new java.io.File(dest.getPath() + ".tmp");
            dest.getParentFile().mkdirs();
            InputStream in = c.getInputStream();
            OutputStream os = new java.io.FileOutputStream(tmp);
            byte[] buf = new byte[8192];
            int n, total = 0;
            while ((n = in.read(buf)) > 0 && total < 4000000) {
                os.write(buf, 0, n);
                total += n;
            }
            os.close();
            in.close();
            c.disconnect();
            return total > 0 && tmp.renameTo(dest);
        } catch (Exception e) {
            return false;
        }
    }

    /** Deja el nombre apto para carpeta/archivo. */
    static String cleanName(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < 40; i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_') b.append(c);
            else if (c == ' ' && b.length() > 0 && b.charAt(b.length() - 1) != '_') b.append('_');
        }
        String r = b.toString();
        while (r.startsWith(".") || r.startsWith("_")) r = r.substring(1);
        while (r.endsWith(".") || r.endsWith("_")) r = r.substring(0, r.length() - 1);
        return r;
    }

    static String firstGroup(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : "";
    }

    static final Pattern IG_USER_HTML = Pattern.compile("\\\\*\"username\\\\*\"\\s*:\\s*\\\\*\"([A-Za-z0-9._]+)\\\\*\"");
    static final Pattern X_USER_URL = Pattern.compile("(?:twitter\\.com|[/.]x\\.com)/([A-Za-z0-9_]+)/status");
    static final Pattern FB_USER_URL = Pattern.compile("facebook\\.com/([^/?#]+)/(?:videos|posts)/");
    static final Pattern[] FB_OWNER = {
        Pattern.compile("\"owner\":\\{\"__typename\":\"(?:User|Page)\",[^{}]*?\"name\":\"([^\"]+)\""),
        Pattern.compile("\"video_owner\":\\{[^{}]*?\"name\":\"([^\"]+)\""),
        Pattern.compile("\"owning_profile\":\\{[^{}]*?\"name\":\"([^\"]+)\""),
        Pattern.compile("\"actors\":\\[\\{[^{}]*?\"name\":\"([^\"]+)\""),
    };

    static String fbOwner(String html, String pageUrl) {
        for (Pattern p : FB_OWNER) {
            String n = firstGroup(p, html);
            if (!n.isEmpty()) return unescape(n);
        }
        String u = firstGroup(FB_USER_URL, pageUrl);
        return "watch".equals(u) || "reel".equals(u) ? "" : u;
    }

    // ---------- Red ----------

    static final class Resp {
        int code;
        String body = "";
        String cookies = "";
        String finalUrl = "";
    }

    /** Petición HTTP que no lanza error por códigos 4xx/5xx; devuelve cuerpo, cookies y URL final. */
    static Resp request(String method, String u, String body, String[][] headers) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", UA);
        if (headers != null) {
            for (String[] h : headers) if (!h[1].isEmpty()) c.setRequestProperty(h[0], h[1]);
        }
        if (body != null) {
            c.setDoOutput(true);
            OutputStream os = c.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.close();
        }
        Resp r = new Resp();
        r.code = c.getResponseCode();
        r.finalUrl = c.getURL().toString();

        StringBuilder ck = new StringBuilder();
        for (java.util.Map.Entry<String, List<String>> e : c.getHeaderFields().entrySet()) {
            if (e.getKey() == null || !e.getKey().equalsIgnoreCase("Set-Cookie")) continue;
            for (String v : e.getValue()) {
                String pair = v.contains(";") ? v.substring(0, v.indexOf(';')) : v;
                if (ck.length() > 0) ck.append("; ");
                ck.append(pair);
            }
        }
        r.cookies = ck.toString();

        InputStream in = r.code >= 400 ? c.getErrorStream() : c.getInputStream();
        if (in != null) {
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            r.body = sb.toString();
        }
        c.disconnect();
        return r;
    }

    static String http(String method, String u, String body, String[][] headers) throws Exception {
        Resp r = request(method, u, body, headers);
        if (r.code >= 400) throw new Exception("HTTP " + r.code);
        return r.body;
    }

}
