package starchroma;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Minimal client for the Razer Chroma SDK REST server that Synapse / Razer Chroma app runs on
 * http://localhost:54235. One session = one "app" in Chroma Connect.
 */
public class ChromaClient {

    public static final String ROOT = "http://localhost:54235/razer/chromasdk";

    private String uri;

    /** why the last request failed (for the log) */
    public static volatile String lastError;

    public boolean isOpen() {
        return uri != null;
    }

    /** registers the app; returns false if Chroma is not running */
    public boolean open() {
        String body = "{\"title\":\"Starsector Chroma\","
            + "\"description\":\"Starsector lighting: abilities, shields, flux and hull damage\","
            + "\"author\":{\"name\":\"toasty-wheeler\",\"contact\":\"https://github.com/toasty-wheeler\"},"
            + "\"device_supported\":[\"keyboard\",\"mouse\",\"headset\",\"mousepad\",\"keypad\",\"chromalink\"],"
            + "\"category\":\"game\"}";
        String res = request("POST", ROOT, body, 6000);
        if (res == null) return false;
        String u = jsonString(res, "uri");
        if (u == null) {
            lastError = "unexpected reply: " + res;
            return false;
        }
        uri = u;
        return true;
    }

    public void close() {
        if (uri == null) return;
        request("DELETE", uri, null, 1000);
        uri = null;
    }

    public boolean heartbeat() {
        if (uri == null) return false;
        return request("PUT", uri + "/heartbeat", null, 1000) != null;
    }

    /** sends one device's colours (0xRRGGBB, row-major); returns false on a connection failure */
    public boolean send(int device, int[] colors) {
        if (uri == null) return false;
        String res = request("PUT", uri + "/" + Devices.NAMES[device], encode(device, colors), 1000);
        return res != null;
    }

    public static String encode(int device, int[] c) {
        StringBuilder sb = new StringBuilder(c.length * 9 + 64);
        sb.append("{\"effect\":\"").append(Devices.EFFECT[device]).append("\",\"param\":[");
        int rows = Devices.ROWS[device];
        int cols = Devices.COLS[device];
        if (rows > 0) {
            for (int r = 0; r < rows; r++) {
                if (r > 0) sb.append(',');
                sb.append('[');
                for (int k = 0; k < cols; k++) {
                    if (k > 0) sb.append(',');
                    sb.append(bgr(c[r * cols + k]));
                }
                sb.append(']');
            }
        } else {
            for (int k = 0; k < c.length; k++) {
                if (k > 0) sb.append(',');
                sb.append(bgr(c[k]));
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    /** Chroma wants 0x00BBGGRR */
    public static int bgr(int rgb) {
        return ((rgb & 0xFF) << 16) | (rgb & 0xFF00) | ((rgb >> 16) & 0xFF);
    }

    static String request(String method, String url, String body, int timeout) {
        HttpURLConnection con = null;
        try {
            // never through a proxy: Chroma is on this PC
            con = (HttpURLConnection) new URL(url).openConnection(java.net.Proxy.NO_PROXY);
            con.setRequestMethod(method);
            con.setConnectTimeout(timeout);
            con.setReadTimeout(timeout);
            con.setUseCaches(false);
            // Chroma's server (http.sys) answers 411 to a PUT without Content-Length, so the
            // heartbeat sends an empty body explicitly
            if (body == null && !"DELETE".equals(method)) body = "";
            if (body != null) {
                byte[] b = body.getBytes("UTF-8");
                con.setDoOutput(true);
                con.setRequestProperty("Content-Type", "application/json");
                con.setFixedLengthStreamingMode(b.length);
                OutputStream out = con.getOutputStream();
                out.write(b);
                out.close();
            }
            int code = con.getResponseCode();
            InputStream in = code >= 400 ? con.getErrorStream() : con.getInputStream();
            // no ByteArrayOutputStream: Starsector only lets mods use a whitelist of java.io classes
            StringBuilder buf = new StringBuilder();
            if (in != null) {
                byte[] tmp = new byte[1024];
                int n;
                while ((n = in.read(tmp)) > 0) buf.append(new String(tmp, 0, n, "ISO-8859-1"));
                in.close();
            }
            if (code >= 400) {
                lastError = method + " " + url + " -> HTTP " + code + " " + buf.toString();
                return null;
            }
            return buf.toString();
        } catch (Throwable t) {
            lastError = method + " " + url + " -> " + t;
            return null;
        }
    }

    /** tiny "key":"value" lookup; enough for the init reply */
    public static String jsonString(String json, String key) {
        int k = json.indexOf("\"" + key + "\"");
        if (0 > k) return null;
        int colon = json.indexOf(':', k);
        int q1 = json.indexOf('"', colon + 1);
        int q2 = json.indexOf('"', q1 + 1);
        if (0 > colon || 0 > q1 || 0 > q2) return null;
        return json.substring(q1 + 1, q2).replace("\\/", "/");
    }
}
