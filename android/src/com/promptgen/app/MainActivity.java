package com.promptgen.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final String TAG = "PromptGen";
    private static final String LD_BASE = "http://127.0.0.1:8081";

    private WebView webView;
    private volatile String lastImageUri = null;
    private volatile byte[] lastImagePng = null;
    private volatile String lastUpscaleError = "";
    private volatile HttpURLConnection aiConn = null;
    private volatile boolean aiAbort = false;
    private MediaPlayer donePlayer = null;
    private Ringtone doneRingtone = null;
    private static final int REQ_PICK_AUDIO = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            initWebView();
        } catch (Throwable t) {
            Log.e(TAG, "init failed", t);
            showError(t);
        }
    }

    private void initWebView() {
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // 站外链接一律丢给系统浏览器，避免把 WebView 导航走
                Uri u = request.getUrl();
                String url = (u == null) ? null : u.toString();
                if (url != null && !url.startsWith("file:") && !url.startsWith("about:")) {
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW, u);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    } catch (Throwable t) {
                        Log.e(TAG, "external link failed", t);
                    }
                    return true;
                }
                return false;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                toast(message);
                result.confirm();
                return true;
            }
        });
        webView.setBackgroundColor(0xFF0E0C18);
        webView.addJavascriptInterface(new Bridge(), "Android");

        webView.loadUrl("file:///android_asset/index.html");
        setContentView(webView);
    }

    private void showError(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String msg = "启动失败，请截图发给我：\n\n" + t.toString() + "\n\n" + sw.toString();

        TextView tv = new TextView(this);
        tv.setBackgroundColor(0xFF0E0C18);
        tv.setTextColor(0xFFF87171);
        tv.setPadding(48, 64, 48, 64);
        tv.setTextSize(13);
        tv.setTextIsSelectable(true);
        tv.setText(msg);

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        sv.addView(tv);
        setContentView(sv);
    }

    private void emitJs(final String code) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try { webView.evaluateJavascript(code, null); } catch (Throwable ignored) {}
            }
        });
    }

    private void toast(final String s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try { Toast.makeText(MainActivity.this, s, Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) {}
            }
        });
    }

    // ================= 画廊存储 =================
    private File galleryDir() {
        File d = new File(getFilesDir(), "gallery");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private byte[] readFile(File f) {
        try {
            if (!f.exists()) return null;
            FileInputStream in = new FileInputStream(f);
            byte[] b = new byte[(int) f.length()];
            int off = 0, r;
            while (off < b.length && (r = in.read(b, off, b.length - off)) > 0) off += r;
            in.close();
            return b;
        } catch (Throwable t) { return null; }
    }

    private boolean writeFile(File f, byte[] data) {
        try {
            FileOutputStream out = new FileOutputStream(f);
            out.write(data);
            out.flush();
            out.close();
            return true;
        } catch (Throwable t) { Log.e(TAG, "write " + f.getName(), t); return false; }
    }

    private byte[] makeThumbJpeg(byte[] png) {
        try {
            Bitmap b = BitmapFactory.decodeByteArray(png, 0, png.length);
            if (b == null) return null;
            int max = 220;
            int w = b.getWidth(), h = b.getHeight();
            float s = Math.min(1f, (float) max / Math.max(w, h));
            Bitmap t = Bitmap.createScaledBitmap(b, Math.max(1, (int) (w * s)), Math.max(1, (int) (h * s)), true);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            t.compress(Bitmap.CompressFormat.JPEG, 72, bos);
            if (t != b) t.recycle();
            b.recycle();
            return bos.toByteArray();
        } catch (Throwable t) { Log.e(TAG, "thumb", t); return null; }
    }

    private JSONArray readIndex() {
        try {
            byte[] b = readFile(new File(galleryDir(), "index.json"));
            if (b == null) return new JSONArray();
            return new JSONArray(new String(b, "UTF-8"));
        } catch (Throwable t) { return new JSONArray(); }
    }

    private void writeIndex(JSONArray a) {
        try { writeFile(new File(galleryDir(), "index.json"), a.toString().getBytes("UTF-8")); }
        catch (Throwable t) { Log.e(TAG, "writeIndex", t); }
    }

    private boolean savePngToAlbum(byte[] png, String name) {
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            if (Build.VERSION.SDK_INT >= 29) {
                cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PromptGen");
            }
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) return false;
            OutputStream os = getContentResolver().openOutputStream(uri);
            os.write(png);
            os.flush();
            os.close();
            return true;
        } catch (Throwable t) { Log.e(TAG, "album save", t); return false; }
    }

    private class Bridge {

        @JavascriptInterface
        public void copyToClipboard(final String text) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("prompt", text));
                    } catch (Throwable t) { Log.e(TAG, "copy failed", t); }
                }
            });
        }

        @JavascriptInterface
        public void shareText(final String text) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        intent.putExtra(Intent.EXTRA_SUBJECT, "AI 提示词");
                        intent.putExtra(Intent.EXTRA_TEXT, text);
                        Intent chooser = Intent.createChooser(intent, "分享提示词");
                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(chooser);
                    } catch (Throwable t) {
                        Log.e(TAG, "share failed", t);
                        try {
                            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            cm.setPrimaryClip(ClipData.newPlainText("prompt", text));
                        } catch (Throwable ignored) {}
                    }
                }
            });
        }

        // ---------- LocalDream ----------

        @JavascriptInterface
        public void ldPing() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String lastErr = "";
                    // 首次连接可能是冷启动/代理探测，重试几次
                    for (int attempt = 0; attempt < 3; attempt++) {
                        try {
                            HttpURLConnection c = openPost("/tokenize", "{\"prompt\":\"ping\"}", 8000, 25000);
                            int code = c.getResponseCode();
                            c.disconnect();
                            if (code == 200) { emitJs("ldPingResult(true,'')"); return; }
                            lastErr = "HTTP " + code;
                        } catch (Throwable t) {
                            lastErr = t.getMessage() == null ? t.toString() : t.getMessage();
                        }
                        try { Thread.sleep(500); } catch (Throwable ignored) {}
                    }
                    emitJs("ldPingResult(false,'" + esc(lastErr) + "')");
                }
            }).start();
        }

        @JavascriptInterface
        public void ldGenerate(final String paramsJson) {
            new Thread(new Runnable() {
                @Override
                public void run() { doGenerate(paramsJson); }
            }).start();
        }

        @JavascriptInterface
        public String getResultImage() {
            return lastImageUri == null ? "" : lastImageUri;
        }

        @JavascriptInterface
        public void ldSaveImage() {
            final byte[] png = lastImagePng;
            if (png == null) { toast("还没有生成的图片"); return; }
            String name = "PromptGen_" + System.currentTimeMillis() + ".png";
            if (savePngToAlbum(png, name)) toast("已保存到相册 Pictures/PromptGen");
            else toast("保存失败");
        }

        /** 调用 LocalDream 的 4× 超分（当前内存里的图） */
        @JavascriptInterface
        public void ldUpscale(final String upscalerPath) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    byte[] src = lastImagePng;
                    if (src == null) { emitJs("ldError('还没有图片可超分')"); return; }
                    int[] dims = new int[2];
                    long[] ms = new long[1];
                    byte[] out = upscalePng(src, upscalerPath, dims, ms);
                    if (out == null) {
                        emitJs("ldError('超分失败：" + esc(lastUpscaleError) + "')");
                        return;
                    }
                    lastImagePng = out;
                    lastImageUri = "data:image/png;base64," + Base64.encodeToString(out, Base64.NO_WRAP);
                    emitJs("ldUpscaleDone(" + dims[0] + "," + dims[1] + "," + ms[0] + ")");
                }
            }).start();
        }

        /** 批量超分画廊图片：结果存为新的画廊条目，原图保留 */
        @JavascriptInterface
        public void galUpscale(final String idsJson, final String upscalerPath) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    int ok = 0, total = 0;
                    StringBuilder errs = new StringBuilder();
                    try {
                        JSONArray ids = new JSONArray(idsJson);
                        total = ids.length();
                        for (int i = 0; i < total; i++) {
                            emitJs("galUpscaleProgress(" + (i + 1) + "," + total + ")");
                            String id = ids.getString(i);
                            File srcFile = new File(galleryDir(), id + ".png");
                            byte[] src = readFile(srcFile);
                            if (src == null) {
                                if (errs.length() == 0) {
                                    errs.append("原图不存在：").append(srcFile.getName());
                                }
                                Log.w(TAG, "galUpscale: missing " + srcFile.getAbsolutePath());
                                continue;
                            }
                            int[] dims = new int[2];
                            byte[] out = upscalePng(src, upscalerPath, dims, null);
                            if (out == null) {
                                if (errs.length() == 0) {
                                    errs.append(lastUpscaleError == null || lastUpscaleError.isEmpty()
                                            ? "超分返回空结果" : lastUpscaleError);
                                }
                                Log.w(TAG, "galUpscale failed: " + lastUpscaleError);
                                continue;
                            }

                            String newId = "img_" + System.currentTimeMillis() + "_" + i;
                            File dst = new File(galleryDir(), newId + ".png");
                            if (!writeFile(dst, out)) {
                                if (errs.length() == 0) errs.append("写入失败：").append(dst.getName());
                                continue;
                            }
                            byte[] thumb = makeThumbJpeg(out);
                            if (thumb != null) writeFile(new File(galleryDir(), newId + ".jpg"), thumb);

                            JSONArray idx = readIndex();
                            JSONObject srcMeta = null;
                            for (int k = 0; k < idx.length(); k++) {
                                JSONObject e = idx.getJSONObject(k);
                                if (id.equals(e.optString("id"))) { srcMeta = e; break; }
                            }
                            JSONObject ne = (srcMeta == null)
                                    ? new JSONObject()
                                    : new JSONObject(srcMeta.toString());
                            ne.remove("thumb");
                            ne.put("id", newId);
                            ne.put("ts", System.currentTimeMillis());
                            ne.put("upscaled", 1);
                            ne.put("w", dims[0]);
                            ne.put("h", dims[1]);
                            ne.put("srcId", id);

                            JSONArray n = new JSONArray();
                            n.put(ne);
                            for (int k = 0; k < idx.length(); k++) n.put(idx.get(k));
                            writeIndex(n);
                            ok++;
                            Log.i(TAG, "galUpscale ok: " + newId + " " + dims[0] + "x" + dims[1]);
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "galUpscale", t);
                        if (errs.length() == 0) errs.append(t.toString());
                    }
                    emitJs("galUpscaleDone(" + ok + "," + total + ",'" + esc(errs.toString()) + "')");
                }
            }).start();
        }

        /** 超分后端自检：拿一张 8×8 纯色图试跑一次 /upscale */
        @JavascriptInterface
        public void ldPingUpscale(final String upscalerPath) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (upscalerPath == null || upscalerPath.trim().isEmpty()) {
                            emitJs("upPingResult(false,'还没填超分模型路径')");
                            return;
                        }
                        int[] dims = new int[2];
                        long[] ms = new long[1];
                        byte[] out = upscalePng(makeTestPng(), upscalerPath.trim(), dims, ms);
                        if (out == null) {
                            emitJs("upPingResult(false,'" + esc(lastUpscaleError) + "')");
                        } else {
                            emitJs("upPingResult(true,'" + dims[0] + "x" + dims[1] + " / " + ms[0] + "ms')");
                        }
                    } catch (Throwable t) {
                        emitJs("upPingResult(false,'" + esc(t.toString()) + "')");
                    }
                }
            }).start();
        }

        /**
         * 自动探测超分模型路径。
         * LocalDream 的固定规则是 <模型目录>/models/<upscalerId>/upscaler.bin，
         * 其中 upscalerId 只会是 upscaler_anime 或 upscaler_realistic。
         */
        @JavascriptInterface
        public void ldProbeUpscaler() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        final String[] ids = {
                                "upscaler_anime",
                                "upscaler_realistic"
                        };
                        final String[] roots = {
                                "/data/data/io.github.xororz.localdream/files",
                                "/storage/emulated/0/Download/LocalDream",
                                "/sdcard/Download/LocalDream",
                                "/storage/emulated/0/Android/data/io.github.xororz.localdream/files",
                                "/storage/emulated/0/Android/media/io.github.xororz.localdream",
                        };
                        byte[] test = makeTestPng();
                        int total = ids.length * roots.length;
                        int n = 0;
                        for (int r = 0; r < roots.length; r++) {
                            for (int k = 0; k < ids.length; k++) {
                                n++;
                                String cand = roots[r] + "/models/" + ids[k] + "/upscaler.bin";
                                if (n % 2 == 1 || n == total) {
                                    emitJs("upProbeProgress(" + n + "," + total + ")");
                                }
                                int[] dims = new int[2];
                                byte[] out = upscalePng(test, cand, dims, null);
                                if (out != null) {
                                    emitJs("upProbeResult(true,'" + esc(cand) + "','"
                                            + dims[0] + "x" + dims[1] + "')");
                                    return;
                                }
                            }
                        }
                        emitJs("upProbeResult(false,'','试了 " + total
                                + " 个候选路径都不行。最后一次错误：" + esc(lastUpscaleError) + "')");
                    } catch (Throwable t) {
                        emitJs("upProbeResult(false,'','" + esc(t.toString()) + "')");
                    }
                }
            }).start();
        }

        // ---------- 画廊 ----------

        /** 把当前结果图存入画廊（完全后台执行，不阻塞界面） */
        @JavascriptInterface
        public String galAdd(final String metaJson) {
            final byte[] png = lastImagePng;
            if (png == null) return "";
            final String id = "img_" + System.currentTimeMillis();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (!writeFile(new File(galleryDir(), id + ".png"), png)) return;
                        byte[] thumb = makeThumbJpeg(png);
                        if (thumb != null) writeFile(new File(galleryDir(), id + ".jpg"), thumb);

                        JSONObject e = new JSONObject(metaJson == null ? "{}" : metaJson);
                        e.put("id", id);
                        e.put("ts", System.currentTimeMillis());

                        JSONArray idx = readIndex();
                        JSONArray n = new JSONArray();
                        n.put(e);
                        for (int i = 0; i < idx.length(); i++) n.put(idx.get(i));
                        writeIndex(n);
                        emitJs("galAdded()");
                    } catch (Throwable t) {
                        Log.e(TAG, "galAdd bg", t);
                    }
                }
            }).start();
            return id;
        }

        /** 列出画廊（带缩略图 data URI） */
        @JavascriptInterface
        public String galList() {
            JSONArray out = new JSONArray();
            try {
                JSONArray idx = readIndex();
                for (int i = 0; i < idx.length(); i++) {
                    JSONObject e = idx.getJSONObject(i);
                    String id = e.optString("id", "");
                    if (id.isEmpty()) continue;
                    byte[] tb = readFile(new File(galleryDir(), id + ".jpg"));
                    if (tb == null) continue;
                    e.put("thumb", "data:image/jpeg;base64," + Base64.encodeToString(tb, Base64.NO_WRAP));
                    out.put(e);
                }
            } catch (Throwable t) { Log.e(TAG, "galList", t); }
            return out.toString();
        }

        /** 取原图 data URI */
        @JavascriptInterface
        public String galFull(String id) {
            try {
                byte[] p = readFile(new File(galleryDir(), id + ".png"));
                if (p == null) return "";
                return "data:image/png;base64," + Base64.encodeToString(p, Base64.NO_WRAP);
            } catch (Throwable t) { return ""; }
        }

        /** 批量删除 */
        @JavascriptInterface
        public int galDelete(String idsJson) {
            int n = 0;
            try {
                JSONArray ids = new JSONArray(idsJson);
                java.util.HashSet<String> kill = new java.util.HashSet<String>();
                for (int i = 0; i < ids.length(); i++) kill.add(ids.getString(i));

                JSONArray idx = readIndex();
                JSONArray keep = new JSONArray();
                for (int i = 0; i < idx.length(); i++) {
                    JSONObject e = idx.getJSONObject(i);
                    String id = e.optString("id", "");
                    if (kill.contains(id)) {
                        new File(galleryDir(), id + ".png").delete();
                        new File(galleryDir(), id + ".jpg").delete();
                        n++;
                    } else {
                        keep.put(e);
                    }
                }
                writeIndex(keep);
            } catch (Throwable t) { Log.e(TAG, "galDelete", t); }
            return n;
        }

        /** 批量保存到系统相册 */
        @JavascriptInterface
        public int galExport(String idsJson) {
            int n = 0;
            try {
                JSONArray ids = new JSONArray(idsJson);
                for (int i = 0; i < ids.length(); i++) {
                    String id = ids.getString(i);
                    byte[] p = readFile(new File(galleryDir(), id + ".png"));
                    if (p == null) continue;
                    if (savePngToAlbum(p, "PromptGen_" + id + ".png")) n++;
                }
            } catch (Throwable t) { Log.e(TAG, "galExport", t); }
            return n;
        }

        /** 画廊占用空间（字节） */
        @JavascriptInterface
        public long galSize() {
            long total = 0;
            File[] fs = galleryDir().listFiles();
            if (fs != null) for (File f : fs) total += f.length();
            return total;
        }

        /** 画廊张数（轻量，不读缩略图） */
        @JavascriptInterface
        public int galCount() {
            return readIndex().length();
        }

        // ================= AI 对话（OpenAI 兼容接口） =================

        /** 用系统浏览器打开链接（获取 API Key 的官网等） */
        @JavascriptInterface
        public void openUrl(final String url) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    } catch (Throwable t) {
                        Log.e(TAG, "openUrl failed", t);
                        Toast.makeText(MainActivity.this, "打不开链接", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        /** 中断正在进行的对话 */
        @JavascriptInterface
        public void aiStop() {
            aiAbort = true;
            HttpURLConnection c = aiConn;
            if (c != null) {
                try { c.disconnect(); } catch (Throwable ignored) {}
            }
        }

        /** 测试 API 连通性（非流式，只请求 8 个 token） */
        @JavascriptInterface
        public void aiPing(final String cfgJson) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    HttpURLConnection c = null;
                    try {
                        JSONObject cfg = new JSONObject(cfgJson);
                        String url = aiEndpoint(cfg.optString("baseUrl", ""));
                        if (url == null) { emitJs("aiPingResult(false," + JSONObject.quote("还没填 Base URL") + ")"); return; }

                        JSONObject body = new JSONObject();
                        body.put("model", cfg.optString("model", "gpt-4o-mini"));
                        JSONArray msgs = new JSONArray();
                        JSONObject m0 = new JSONObject();
                        m0.put("role", "user");
                        m0.put("content", "hi");
                        msgs.put(m0);
                        body.put("messages", msgs);
                        body.put("max_tokens", 8);
                        body.put("stream", false);

                        c = (HttpURLConnection) new URL(url).openConnection(java.net.Proxy.NO_PROXY);
                        c.setRequestMethod("POST");
                        c.setDoOutput(true);
                        c.setRequestProperty("Content-Type", "application/json");
                        String key = cfg.optString("apiKey", "").trim();
                        if (!key.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + key);
                        c.setConnectTimeout(20000);
                        c.setReadTimeout(60000);
                        OutputStream os = c.getOutputStream();
                        os.write(body.toString().getBytes("UTF-8"));
                        os.flush();
                        os.close();

                        int code = c.getResponseCode();
                        if (code == 200) {
                            emitJs("aiPingResult(true," + JSONObject.quote(
                                    "连接正常 · 模型 " + cfg.optString("model", "")) + ")");
                        } else {
                            byte[] eb = readAll(c.getErrorStream());
                            String m = eb == null ? "" : new String(eb, "UTF-8");
                            if (m.length() > 220) m = m.substring(0, 220);
                            emitJs("aiPingResult(false," + JSONObject.quote("HTTP " + code + " " + m) + ")");
                        }
                    } catch (Throwable t) {
                        String m = t.getMessage() == null ? t.toString() : t.getMessage();
                        emitJs("aiPingResult(false," + JSONObject.quote(m) + ")");
                    } finally {
                        if (c != null) { try { c.disconnect(); } catch (Throwable ignored) {} }
                    }
                }
            }).start();
        }

        /** 流式对话：逐段回调 aiDelta，结束回调 aiDone */
        @JavascriptInterface
        public void aiChat(final String cfgJson, final String messagesJson, final String reqId) {
            aiAbort = false;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    HttpURLConnection c = null;
                    StringBuilder full = new StringBuilder();
                    try {
                        JSONObject cfg = new JSONObject(cfgJson);
                        String url = aiEndpoint(cfg.optString("baseUrl", ""));
                        if (url == null) {
                            emitJs("aiError(" + JSONObject.quote(reqId) + ","
                                    + JSONObject.quote("还没填 API Base URL") + ")");
                            return;
                        }

                        JSONObject body = new JSONObject();
                        body.put("model", cfg.optString("model", "gpt-4o-mini"));
                        body.put("messages", new JSONArray(messagesJson));
                        body.put("temperature", cfg.optDouble("temperature", 0.8));
                        body.put("max_tokens", cfg.optInt("maxTokens", 2048));
                        body.put("stream", true);

                        c = (HttpURLConnection) new URL(url).openConnection(java.net.Proxy.NO_PROXY);
                        c.setRequestMethod("POST");
                        c.setDoOutput(true);
                        c.setRequestProperty("Content-Type", "application/json");
                        c.setRequestProperty("Accept", "text/event-stream");
                        String key = cfg.optString("apiKey", "").trim();
                        if (!key.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + key);
                        c.setConnectTimeout(20000);
                        c.setReadTimeout(300000);
                        aiConn = c;

                        OutputStream os = c.getOutputStream();
                        os.write(body.toString().getBytes("UTF-8"));
                        os.flush();
                        os.close();

                        int code = c.getResponseCode();
                        if (code != 200) {
                            byte[] eb = readAll(c.getErrorStream());
                            String m = eb == null ? "" : new String(eb, "UTF-8");
                            if (m.length() > 300) m = m.substring(0, 300);
                            emitJs("aiError(" + JSONObject.quote(reqId) + ","
                                    + JSONObject.quote("HTTP " + code + " " + m) + ")");
                            return;
                        }

                        BufferedReader br = new BufferedReader(
                                new InputStreamReader(c.getInputStream(), "UTF-8"));
                        String line;
                        while ((line = br.readLine()) != null) {
                            if (aiAbort) break;
                            if (!line.startsWith("data:")) continue;
                            String data = line.substring(5).trim();
                            if (data.isEmpty() || "[DONE]".equals(data)) continue;
                            try {
                                JSONObject o = new JSONObject(data);
                                JSONArray ch = o.optJSONArray("choices");
                                if (ch == null || ch.length() == 0) continue;
                                JSONObject d = ch.getJSONObject(0).optJSONObject("delta");
                                if (d == null) continue;
                                String piece = d.optString("content", "");
                                if (piece.isEmpty()) continue;
                                full.append(piece);
                                emitJs("aiDelta(" + JSONObject.quote(reqId) + ","
                                        + JSONObject.quote(piece) + ")");
                            } catch (Throwable ignored) {}
                        }
                        try { br.close(); } catch (Throwable ignored) {}

                        if (aiAbort) {
                            emitJs("aiStopped(" + JSONObject.quote(reqId) + ","
                                    + JSONObject.quote(full.toString()) + ")");
                        } else {
                            emitJs("aiDone(" + JSONObject.quote(reqId) + ","
                                    + JSONObject.quote(full.toString()) + ")");
                        }
                    } catch (Throwable t) {
                        String m = t.getMessage() == null ? t.toString() : t.getMessage();
                        if (aiAbort) {
                            emitJs("aiStopped(" + JSONObject.quote(reqId) + ","
                                    + JSONObject.quote(full.toString()) + ")");
                        } else {
                            emitJs("aiError(" + JSONObject.quote(reqId) + ","
                                    + JSONObject.quote(m) + ")");
                        }
                    } finally {
                        aiConn = null;
                        if (c != null) { try { c.disconnect(); } catch (Throwable ignored) {} }
                    }
                }
            }).start();
        }

        // ================= 完成提示音 =================

        /** 列出系统里的通知音 / 来电铃声 / 闹钟音 */
        @JavascriptInterface
        public void listSystemSounds() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    JSONArray arr = new JSONArray();
                    try {
                        int[] types = {
                                RingtoneManager.TYPE_NOTIFICATION,
                                RingtoneManager.TYPE_RINGTONE,
                                RingtoneManager.TYPE_ALARM
                        };
                        String[] names = {"通知", "来电", "闹钟"};
                        for (int k = 0; k < types.length; k++) {
                            RingtoneManager rm = new RingtoneManager(MainActivity.this);
                            rm.setType(types[k]);
                            Cursor c = rm.getCursor();
                            if (c == null) continue;
                            while (c.moveToNext()) {
                                try {
                                    String title = c.getString(RingtoneManager.TITLE_COLUMN_INDEX);
                                    Uri u = rm.getRingtoneUri(c.getPosition());
                                    if (u == null) continue;
                                    JSONObject o = new JSONObject();
                                    o.put("title", title == null ? "未命名" : title);
                                    o.put("uri", u.toString());
                                    o.put("type", names[k]);
                                    arr.put(o);
                                } catch (Throwable ignored) {}
                            }
                            c.close();
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "listSystemSounds", t);
                    }
                    emitJs("systemSounds(" + arr.toString() + ")");
                }
            }).start();
        }

        /** 播放指定音频；uri 为空则播放系统默认通知音 */
        @JavascriptInterface
        public void playSound(final String uriStr) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    playSoundNow(uriStr);
                }
            });
        }

        /** 停止正在播放的提示音 */
        @JavascriptInterface
        public void stopSound() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    stopSoundNow();
                }
            });
        }

        /** 打开系统文件选择器挑一个音频文件 */
        @JavascriptInterface
        public void pickAudioFile() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("audio/*");
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                        startActivityForResult(i, REQ_PICK_AUDIO);
                    } catch (Throwable t) {
                        Log.e(TAG, "pickAudioFile", t);
                        emitJs("audioPickResult(false,'','打不开文件选择器')");
                    }
                }
            });
        }
    }

    // ================= 音频播放实现 =================
    private void stopSoundNow() {
        if (donePlayer != null) {
            try { if (donePlayer.isPlaying()) donePlayer.stop(); } catch (Throwable ignored) {}
            try { donePlayer.release(); } catch (Throwable ignored) {}
            donePlayer = null;
        }
        if (doneRingtone != null) {
            try { if (doneRingtone.isPlaying()) doneRingtone.stop(); } catch (Throwable ignored) {}
            doneRingtone = null;
        }
    }

    private void playSoundNow(String uriStr) {
        stopSoundNow();
        Uri u;
        if (uriStr == null || uriStr.trim().isEmpty()) {
            u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        } else {
            try { u = Uri.parse(uriStr.trim()); } catch (Throwable t) { u = null; }
        }
        if (u == null) return;

        // 优先 MediaPlayer（用户自选的音频文件也能放）
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mp.setDataSource(this, u);
            mp.prepare();
            mp.start();
            donePlayer = mp;
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer m) {
                    try { m.release(); } catch (Throwable ignored) {}
                    if (donePlayer == m) donePlayer = null;
                }
            });
            return;
        } catch (Throwable t) {
            Log.w(TAG, "MediaPlayer failed, fallback to Ringtone: " + t.getMessage());
        }
        // 回退到 Ringtone（部分系统铃声只有 Ringtone 能放）
        try {
            Ringtone r = RingtoneManager.getRingtone(this, u);
            if (r != null) { r.play(); doneRingtone = r; }
        } catch (Throwable t) {
            Log.e(TAG, "playSound failed", t);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_AUDIO) return;
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri u = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(u,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Throwable ignored) {}
            String title = queryDisplayName(u);
            emitJs("audioPickResult(true," + JSONObject.quote(u.toString()) + ","
                    + JSONObject.quote(title) + ")");
        } else {
            emitJs("audioPickResult(false,'','已取消')");
        }
    }

    /** 从 content:// 查出文件显示名 */
    private String queryDisplayName(Uri u) {
        Cursor c = null;
        try {
            c = getContentResolver().query(u, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String n = c.getString(idx);
                    if (n != null && !n.isEmpty()) return n;
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (c != null) { try { c.close(); } catch (Throwable ignored) {} }
        }
        String last = u.getLastPathSegment();
        return last == null ? "已选音频" : last;
    }

    /** 把 Base URL 规范成 /chat/completions 完整地址；为空返回 null */
    private String aiEndpoint(String base) {
        if (base == null) return null;
        String b = base.trim().replaceAll("/+$", "");
        if (b.isEmpty()) return null;
        if (b.endsWith("/chat/completions")) return b;
        return b + "/chat/completions";
    }

    // ================= HTTP helpers =================
    private byte[] readAll(java.io.InputStream in) {
        if (in == null) return null;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int r;
            while ((r = in.read(buf)) > 0) bos.write(buf, 0, r);
            in.close();
            return bos.toByteArray();
        } catch (Throwable t) { return null; }
    }

    private int safeInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Throwable t) { return def; }
    }

    /** 造一张 8×8 纯色 PNG，用于超分连通性测试 */
    private byte[] makeTestPng() {
        Bitmap b = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        b.eraseColor(0xFF808080);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, bos);
        b.recycle();
        return bos.toByteArray();
    }

    /**
     * 把 PNG 送去 LocalDream 做 4× 超分。
     * 成功返回 PNG 字节；outDims 回填输出宽高，outMs 回填耗时。失败返回 null 并写入 lastUpscaleError。
     */
    private byte[] upscalePng(byte[] srcPng, String upscalerPath, int[] outDims, long[] outMs) {
        HttpURLConnection c = null;
        try {
            Bitmap bmp = BitmapFactory.decodeByteArray(srcPng, 0, srcPng.length);
            if (bmp == null) { lastUpscaleError = "原图解码失败"; return null; }
            int w = bmp.getWidth(), h = bmp.getHeight();
            int[] px = new int[w * h];
            bmp.getPixels(px, 0, w, 0, 0, w, h);
            bmp.recycle();
            byte[] rgb = new byte[w * h * 3];
            for (int i = 0; i < w * h; i++) {
                int p = px[i];
                rgb[i * 3] = (byte) ((p >> 16) & 0xFF);
                rgb[i * 3 + 1] = (byte) ((p >> 8) & 0xFF);
                rgb[i * 3 + 2] = (byte) (p & 0xFF);
            }

            c = (HttpURLConnection) new URL(LD_BASE + "/upscale").openConnection(java.net.Proxy.NO_PROXY);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/octet-stream");
            c.setRequestProperty("X-Image-Width", String.valueOf(w));
            c.setRequestProperty("X-Image-Height", String.valueOf(h));
            c.setRequestProperty("X-Upscaler-Path", upscalerPath);
            c.setConnectTimeout(12000);
            c.setReadTimeout(900000);
            OutputStream os = c.getOutputStream();
            os.write(rgb);
            os.flush();
            os.close();

            int code = c.getResponseCode();
            if (code != 200) {
                byte[] eb = readAll(c.getErrorStream());
                String m = eb == null ? "" : new String(eb, "UTF-8");
                if (m.length() > 130) m = m.substring(0, 130);
                lastUpscaleError = "HTTP " + code + " " + m;
                return null;
            }
            int ow = safeInt(c.getHeaderField("X-Output-Width"), w * 4);
            int oh = safeInt(c.getHeaderField("X-Output-Height"), h * 4);
            if (outMs != null && outMs.length > 0) {
                outMs[0] = safeInt(c.getHeaderField("X-Duration-Ms"), 0);
            }
            byte[] jpeg = readAll(c.getInputStream());
            if (jpeg == null) { lastUpscaleError = "超分结果为空"; return null; }

            Bitmap ub = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
            if (ub == null) { lastUpscaleError = "超分结果解码失败"; return null; }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ub.compress(Bitmap.CompressFormat.PNG, 100, bos);
            ub.recycle();
            if (outDims != null && outDims.length >= 2) { outDims[0] = ow; outDims[1] = oh; }
            lastUpscaleError = "";
            return bos.toByteArray();
        } catch (Throwable t) {
            Log.e(TAG, "upscale failed", t);
            lastUpscaleError = (t.getMessage() == null) ? t.toString() : t.getMessage();
            return null;
        } finally {
            if (c != null) { try { c.disconnect(); } catch (Throwable ignored) {} }
        }
    }

    private HttpURLConnection openPost(String path, String body, int connectMs, int readMs) throws Exception {
        URL url = new URL(LD_BASE + path);
        // 关键：绕过系统代理（VPN/APN 代理会让首次连 127.0.0.1 超时）
        HttpURLConnection c = (HttpURLConnection) url.openConnection(java.net.Proxy.NO_PROXY);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setConnectTimeout(connectMs);
        c.setReadTimeout(readMs);
        OutputStream os = c.getOutputStream();
        os.write(body.getBytes("UTF-8"));
        os.flush();
        os.close();
        return c;
    }

    private boolean isConnError(String s) {
        if (s == null) return false;
        String t = s.toLowerCase();
        return t.indexOf("connect") >= 0 || t.indexOf("timeout") >= 0 || t.indexOf("timed out") >= 0
                || t.indexOf("refused") >= 0 || t.indexOf("unreachable") >= 0
                || t.indexOf("socket") >= 0 || t.indexOf("reset") >= 0 || t.indexOf("econn") >= 0;
    }

    /** 带重试的生成：首次连接超时/后端未就绪时自动重试 */
    private void doGenerate(String paramsJson) {
        String terminal = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            terminal = doGenerateOnce(paramsJson);
            if (terminal != null && terminal.startsWith("ldComplete")) break;
            boolean retryable = (terminal == null) || isConnError(terminal);
            if (!retryable || attempt == 3) break;
            Log.w(TAG, "generate retry " + attempt + " : " + terminal);
            try { Thread.sleep(700); } catch (Throwable ignored) {}
        }
        if (terminal == null) {
            terminal = "ldError('LocalDream 没有返回结果（未加载模型 / 后端被回收 / 端口被占用）')";
        }
        emitJs(terminal);
    }

    /** 返回终止事件的 JS 代码，progress 事件在读取过程中即时回调 */
    private String doGenerateOnce(String paramsJson) {
        HttpURLConnection c = null;
        String terminal = null;
        try {
            c = openPost("/generate", paramsJson, 10000, 900000);
            int code = c.getResponseCode();
            if (code != 200) {
                return "ldError('LocalDream 返回 HTTP " + code + "')";
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            String line, event = null;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("event:")) {
                    event = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    terminal = handleEvent(event, line.substring(5).trim());
                    if (terminal != null) break;   // 收到终止事件，立刻退出读取
                }
            }
            try { br.close(); } catch (Throwable ignored) {}
        } catch (Throwable t) {
            Log.e(TAG, "generate failed", t);
            String m = t.getMessage() == null ? t.toString() : t.getMessage();
            terminal = "ldError('" + esc(m) + "')";
        } finally {
            // 必须在通知前端之前把连接彻底关掉，否则后端的推理锁仍被占着，
            // 批量出图的下一张会卡住甚至超时。
            if (c != null) {
                try { c.disconnect(); } catch (Throwable ignored) {}
            }
        }
        return terminal;
    }

    private String handleEvent(String event, String data) {
        try {
            JSONObject o = new JSONObject(data);
            String type = o.optString("type", event == null ? "" : event);
            if ("progress".equals(type)) {
                emitJs("ldProgress(" + o.optInt("step", 0) + "," + o.optInt("total_steps", 0) + ")");
                return null;
            }
            if ("complete".equals(type)) {
                String b64 = o.optString("image", "");
                int w = o.optInt("width", 512);
                int h = o.optInt("height", 512);
                long seed = o.optLong("seed", 0);
                if (b64.isEmpty()) return "ldError('返回数据里没有图片')";
                byte[] rgb = Base64.decode(b64, Base64.DEFAULT);
                byte[] png = rgbToPng(rgb, w, h);
                if (png == null) return "ldError('图片解码失败')";
                lastImagePng = png;
                lastImageUri = "data:image/png;base64," + Base64.encodeToString(png, Base64.NO_WRAP);
                return "ldComplete(" + seed + ")";
            }
            if ("error".equals(type)) {
                return "ldError('" + esc(o.optString("message", "未知错误")) + "')";
            }
        } catch (Throwable t) {
            Log.e(TAG, "event parse failed: " + data, t);
        }
        return null;
    }

    private byte[] rgbToPng(byte[] rgb, int w, int h) {
        try {
            int n = w * h;
            if (rgb.length < n * 3) return null;
            int[] px = new int[n];
            for (int i = 0; i < n; i++) {
                int r = rgb[i * 3] & 0xFF;
                int g = rgb[i * 3 + 1] & 0xFF;
                int b = rgb[i * 3 + 2] & 0xFF;
                px[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
            Bitmap bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bos);
            bmp.recycle();
            return bos.toByteArray();
        } catch (Throwable t) {
            Log.e(TAG, "rgbToPng failed", t);
            return null;
        }
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", " ");
    }
}
