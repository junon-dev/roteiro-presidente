package app.roteiro.presidente;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Roteiro do Presidente — tela única com o app em HTML (assets/www/index.html).
 * O conteúdo é servido em https://appassets.androidplatform.net/ para que
 * armazenamento local, pdf.js e Web Workers funcionem como num site seguro.
 * A ponte "AndroidApp" oferece ao HTML: salvar arquivo, imprimir, manter a tela
 * ligada, vibrar e bipe.
 */
public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String URL_INICIAL = "https://" + HOST + "/assets/www/index.html";
    private static final int REQ_ABRIR = 1;
    private static final int REQ_SALVAR = 2;
    private static final int COR_BARRA = Color.rgb(0x1f, 0x3b, 0x4d);

    private WebView web;
    private WebViewAssetLoader assets;
    private ValueCallback<Uri[]> arquivosCallback;
    private String conteudoPendente;
    private long ultimoVoltar = 0;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        // Desenha sob as barras do sistema em todas as versões (padrão no Android 15+);
        // o espaço das barras é reservado pelo listener de insets abaixo.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            w.setDecorFitsSystemWindows(false);
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);

        FrameLayout raiz = new FrameLayout(this);
        raiz.setBackgroundColor(COR_BARRA);
        web = new WebView(this);
        raiz.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(raiz);

        // Android 15+ desenha atrás das barras do sistema: reserva o espaço delas.
        raiz.setOnApplyWindowInsetsListener((v, insets) -> {
            int esq, topo, dir, base;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets i = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                esq = i.left; topo = i.top; dir = i.right; base = i.bottom;
            } else {
                esq = insets.getSystemWindowInsetLeft(); topo = insets.getSystemWindowInsetTop();
                dir = insets.getSystemWindowInsetRight(); base = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(esq, topo, dir, base);
            return insets;
        });

        assets = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setSupportZoom(false);

        web.addJavascriptInterface(new Ponte(), "AndroidApp");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assets.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (ActivityNotFoundException ignored) { }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                return abrirSeletor(callback, params);
            }
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        if (web.getUrl() == null) web.loadUrl(URL_INICIAL);
    }

    /* ---------- Seleção de arquivos (<input type="file">) ---------- */
    private boolean abrirSeletor(ValueCallback<Uri[]> callback, WebChromeClient.FileChooserParams params) {
        if (arquivosCallback != null) arquivosCallback.onReceiveValue(null);
        arquivosCallback = callback;
        boolean pdf = false;
        String[] tipos = params.getAcceptTypes();
        if (tipos != null) for (String t : tipos) if (t != null && t.toLowerCase().contains("pdf")) pdf = true;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(pdf ? "application/pdf" : "*/*");
        if (params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        try {
            startActivityForResult(i, REQ_ABRIR);
            return true;
        } catch (ActivityNotFoundException e) {
            arquivosCallback = null;
            Toast.makeText(this, R.string.sem_seletor, Toast.LENGTH_LONG).show();
            return false;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ABRIR) {
            if (arquivosCallback == null) return;
            Uri[] resultado = null;
            if (resultCode == RESULT_OK && data != null) {
                List<Uri> lista = new ArrayList<>();
                ClipData clip = data.getClipData();
                if (clip != null) {
                    for (int k = 0; k < clip.getItemCount(); k++) lista.add(clip.getItemAt(k).getUri());
                } else if (data.getData() != null) {
                    lista.add(data.getData());
                }
                if (!lista.isEmpty()) resultado = lista.toArray(new Uri[0]);
            }
            arquivosCallback.onReceiveValue(resultado);
            arquivosCallback = null;
        } else if (requestCode == REQ_SALVAR) {
            boolean ok = false;
            if (resultCode == RESULT_OK && data != null && data.getData() != null && conteudoPendente != null) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData(), "wt")) {
                    if (out != null) {
                        out.write(conteudoPendente.getBytes(StandardCharsets.UTF_8));
                        ok = true;
                    }
                } catch (Exception e) {
                    ok = false;
                }
            }
            conteudoPendente = null;
            if (resultCode == RESULT_OK) {
                web.evaluateJavascript("window.aoSalvarArquivo && window.aoSalvarArquivo(" + ok + ")", null);
            }
        }
    }

    /* ---------- Botão voltar ---------- */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        web.evaluateJavascript("window.voltarApp ? window.voltarApp() : 'sair'", valor -> {
            if (valor != null && valor.contains("ok")) return;
            long agora = System.currentTimeMillis();
            if (agora - ultimoVoltar < 2000) {
                finish();
            } else {
                ultimoVoltar = agora;
                Toast.makeText(this, R.string.voltar_sair, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.stopLoading();
            web.destroy();
        }
        super.onDestroy();
    }

    /* ---------- Ponte JavaScript -> Android ---------- */
    private class Ponte {

        @JavascriptInterface
        public String versao() {
            return BuildConfig.VERSION_NAME;
        }

        @JavascriptInterface
        public void salvarArquivo(String nome, String mime, String conteudo) {
            ui.post(() -> {
                conteudoPendente = conteudo;
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(mime == null || mime.isEmpty() ? "application/octet-stream" : mime);
                i.putExtra(Intent.EXTRA_TITLE, nome);
                try {
                    startActivityForResult(i, REQ_SALVAR);
                } catch (ActivityNotFoundException e) {
                    conteudoPendente = null;
                    Toast.makeText(MainActivity.this, R.string.sem_seletor, Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void imprimir(String titulo) {
            ui.post(() -> {
                PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                if (pm == null) return;
                String nome = (titulo == null || titulo.isEmpty()) ? getString(R.string.app_name) : titulo;
                PrintDocumentAdapter adapter = web.createPrintDocumentAdapter(nome);
                pm.print(nome, adapter, new PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4).build());
            });
        }

        @JavascriptInterface
        public void manterTela(boolean ligar) {
            ui.post(() -> {
                if (ligar) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        @JavascriptInterface
        public void vibrar() {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            v.vibrate(VibrationEffect.createWaveform(new long[]{0, 250, 120, 250}, -1));
        }

        @JavascriptInterface
        public void bipe() {
            ui.post(() -> {
                try {
                    ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_MUSIC, 70);
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 500);
                    ui.postDelayed(tg::release, 900);
                } catch (RuntimeException ignored) { }
            });
        }
    }
}
