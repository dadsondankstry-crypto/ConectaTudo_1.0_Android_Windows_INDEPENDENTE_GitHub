package br.app.conectatudo.v1;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

/** Tela nativa; protocolo: HTTP local porta 8765, PIN de seis dígitos. */
public class MainActivity extends Activity {
    private static final int PICK=200, SCREEN=201;
    private LinearLayout page;
    private TextView status, ipLabel, pinLabel, receivedLabel, messagesLabel;
    private EditText remoteIp, remotePin, sendText;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Runnable periodic = new Runnable(){@Override public void run(){updateLocal();}};
    private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newCachedThreadPool();
    private int accent=Color.rgb(110,178,255), bg=Color.rgb(13,23,40), card=Color.rgb(27,40,62);
    private android.content.SharedPreferences prefs;

    @Override public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("config",MODE_PRIVATE);build();readDeeplink(getIntent());
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},100);
        // Inicia somente quando o usuário abre o aplicativo; a notificação avisa que o servidor está ativo.
        launchService(null);ui.postDelayed(periodic,800);
    }
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);readDeeplink(i);}
    @Override protected void onDestroy(){ui.removeCallbacks(periodic);worker.shutdownNow();super.onDestroy();}

    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable rounded(int color){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(15));return g;}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(5),0,dp(5));return t;}
    private void addTitle(String s){TextView t=text(s,18,accent);t.setTypeface(null,1);t.setPadding(0,dp(6),0,dp(10));page.addView(t);}
    private LinearLayout box(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(16),dp(14),dp(16),dp(14));l.setBackground(rounded(card));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(13);page.addView(l,p);return l;}
    private Button button(String title,Runnable action){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setTextSize(14);
        b.setBackground(rounded(Color.rgb(38,104,184)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.topMargin=dp(7);b.setOnClickListener(v->action.run());b.setLayoutParams(p);return b;}
    private EditText input(String hint,String initial){EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(16);e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.rgb(160,174,199));e.setHint(hint);e.setText(initial);e.setPadding(dp(11),dp(9),dp(11),dp(9));
        e.setBackground(rounded(Color.rgb(12,25,43)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(51));p.topMargin=dp(5);p.bottomMargin=dp(6);e.setLayoutParams(p);return e;}
    private void build(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(bg);page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(15),dp(14),dp(15),dp(35));scroll.addView(page);setContentView(scroll);
        TextView title=text("🔗 ConectaTudo 1.0",26,accent);title.setTypeface(null,1);page.addView(title);
        page.addView(text("POCO ↔ Tablet ↔ Legion Go  •  Wi-Fi local sem internet",13,Color.rgb(174,190,210)));
        status=text("Preparando servidor...",13,Color.rgb(135,230,168));page.addView(status);
        addTitle("📡 Este aparelho • Receber");
        LinearLayout own=box();own.addView(text("Conecte os aparelhos à mesma rede Wi-Fi ou hotspot.",14,Color.WHITE));
        ipLabel=text("Endereço local: detectando...",15,Color.WHITE);ipLabel.setTextIsSelectable(true);own.addView(ipLabel);
        pinLabel=text("PIN de acesso: ...",21,Color.rgb(143,229,171));pinLabel.setTypeface(null,1);pinLabel.setTextIsSelectable(true);own.addView(pinLabel);
        own.addView(button("📷 Mostrar QR para outro celular",this::showQr));
        own.addView(button("📋 Copiar meu endereço",this::copyAddress));
        own.addView(button("🌐 Abrir meu painel no navegador",this::ownPanel));
        own.addView(button("📶 Configurar Wi-Fi / Hotspot",()->startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS))));
        own.addView(button("▶ Iniciar meu servidor",()->launchService(null)));
        own.addView(button("⏹️ Parar meu servidor",()->{launchService(LanService.ACTION_STOP);status.setText("Servidor encerrado. Abra novamente o app para iniciar.");}));
        addTitle("🔗 Conectar a outro dispositivo");
        LinearLayout link=box();link.addView(text("O Legion Go não precisa de câmera: basta digitar o IP e o PIN mostrados no Windows.",14,Color.WHITE));
        remoteIp=input("IP do outro aparelho (ex.: 192.168.43.25)",prefs.getString("peer_ip",""));link.addView(remoteIp);
        remotePin=input("PIN de 6 números",prefs.getString("peer_pin",""));remotePin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);link.addView(remotePin);
        link.addView(button("🔎 Procurar dispositivos próximos",this::search));
        link.addView(button("📷 Ler QR de outro aparelho",this::scanQr));
        link.addView(button("✅ Testar conexão",this::connect));
        link.addView(button("🌐 Abrir painel do outro aparelho",()->openRemote("/")));
        addTitle("📤 Compartilhar arquivos e textos");
        LinearLayout send=box();send.addView(button("📂 Escolher e enviar arquivos",this::pickFiles));
        sendText=input("Digite uma mensagem de texto...","");send.addView(sendText);
        send.addView(button("💬 Enviar texto",this::sendMessage));
        send.addView(button("🔄 Atualizar mensagens recebidas",this::refreshMessages));
        messagesLabel=text("Mensagens recebidas aparecem aqui.",13,Color.rgb(197,211,230));send.addView(messagesLabel);
        send.addView(button("📥 Ver arquivos recebidos",this::refreshReceived));
        receivedLabel=text("Arquivos recebidos são salvos em Downloads/ConectaTudo.",13,Color.rgb(197,211,230));send.addView(receivedLabel);
        addTitle("🖥️ Compartilhamento de tela");
        LinearLayout screen=box();screen.addView(text("Compartilhar minha tela exige confirmação do Android. Para ver a tela do Windows, habilite a opção no EXE.",14,Color.WHITE));
        screen.addView(button("▶ Compartilhar minha tela",this::askProjection));
        screen.addView(button("⏹ Parar compartilhamento",()->launchService(LanService.ACTION_STOP_SCREEN)));
        screen.addView(button("👁️ Ver tela do outro dispositivo",()->openRemote("/screen")));
        LinearLayout info=box();info.addView(text("🔒 Uso local: HTTP + PIN. Evite redes públicas. Não há criptografia de ponta a ponta nesta versão. Conexão pela internet e controle remoto ainda não estão implementados.",12,Color.rgb(194,204,222)));
    }
    private void updateLocal(){
        LanService s=LanService.active;
        if(s!=null&&s.isRunning()){
            status.setText("🟢 Servidor ativo • Porta 8765"+(s.isSharing()?" • Tela ativa":""));
            List<String> ips=Net.ownIps();ipLabel.setText("IP local: "+(ips.isEmpty()?"Ative Wi-Fi/hotspot":android.text.TextUtils.join("  •  ",ips)));
            pinLabel.setText("🔑 PIN: "+s.getPin());
        }else{status.setText("🟠 Servidor iniciando ou parado");}
        ui.postDelayed(periodic,2500);
    }
    private void launchService(String action){
        Intent i=new Intent(this,LanService.class);if(action!=null)i.setAction(action);
        if(LanService.ACTION_STOP.equals(action)||LanService.ACTION_STOP_SCREEN.equals(action))startService(i);
        else startForegroundService(i);
    }
    private String peerIp(){return Net.cleanIp(remoteIp.getText().toString());}
    private String peerPin(){String p=remotePin.getText().toString().trim();if(!p.matches("\\d{6}"))throw new IllegalArgumentException("Informe PIN de 6 números");return p;}
    private void savePeer(){prefs.edit().putString("peer_ip",peerIp()).putString("peer_pin",peerPin()).apply();}
    private void job(String action,Task task){status.setText("⏳ "+action+"...");worker.execute(()->{try{String msg=task.call();ui.post(()->{status.setText("✅ "+msg);toast(msg);});}
        catch(Exception ex){ui.post(()->{status.setText("❌ "+action+": "+ex.getMessage());dialog("Erro",ex.getMessage());});}});}
    interface Task{String call()throws Exception;}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private void dialog(String title,String msg){new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("OK",null).show();}
    private void copyAddress(){LanService s=LanService.active;if(s==null)return;
        List<String> ips=Net.ownIps();String ip=ips.isEmpty()?"127.0.0.1":ips.get(0);
        ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("ConectaTudo",Net.url(ip,"/",s.getPin())));
        toast("Endereço copiado. Se houver vários IPs, confira a rede Wi-Fi.");}
    private void ownPanel(){LanService s=LanService.active;if(s==null){toast("Inicie o servidor");return;}
        visit(Net.url("127.0.0.1","/",s.getPin()));}
    private void visit(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){dialog("Navegador",e.toString());}}
    private void showQr(){LanService s=LanService.active;if(s==null){toast("Servidor ainda não iniciado");return;}
        List<String> ips=Net.ownIps();if(ips.isEmpty()){dialog("Sem rede","Conecte-se ao Wi-Fi ou ative o hotspot.");return;}
        String[] opts=ips.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("Selecione IP da rede utilizada").setItems(opts,(d,which)->drawQr(opts[which],s.getPin())).show();
    }
    private void drawQr(String ip,String pin){
        try{
            String uri="conectatudo://connect?ip="+Uri.encode(ip)+"&pin="+Uri.encode(pin);
            BitMatrix matrix=new MultiFormatWriter().encode(uri,BarcodeFormat.QR_CODE,540,540);
            int w=matrix.getWidth(),h=matrix.getHeight();Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)b.setPixel(x,y,matrix.get(x,y)?Color.BLACK:Color.WHITE);
            ImageView img=new ImageView(this);img.setImageBitmap(b);img.setPadding(dp(12),dp(12),dp(12),dp(12));
            new AlertDialog.Builder(this).setTitle("Escaneie com outro Android").setMessage("Ou digite IP: "+ip+" • PIN: "+pin)
                .setView(img).setPositiveButton("Fechar",null).show();
        }catch(Exception e){dialog("QR Code",e.getMessage());}
    }
    private void scanQr(){new IntentIntegrator(this).setDesiredBarcodeFormats(Collections.singletonList("QR_CODE"))
        .setPrompt("Aponte para o QR do ConectaTudo").setBeepEnabled(false).initiateScan();}
    private void readDeeplink(Intent i){if(i==null||i.getData()==null)return;Uri data=i.getData();
        if("conectatudo".equals(data.getScheme())&&"connect".equals(data.getHost())){
            String ip=data.getQueryParameter("ip"),pin=data.getQueryParameter("pin");
            if(ip!=null)remoteIp.setText(ip);if(pin!=null)remotePin.setText(pin);
            toast("Dados do QR preenchidos. Toque em Testar conexão.");
        }
    }
    private void connect(){final String ip,pin;try{ip=peerIp();pin=peerPin();savePeer();}catch(Exception e){dialog("Conexão",e.getMessage());return;}
        job("Conectando",()->{JSONObject obj=new JSONObject(Net.get(Net.url(ip,"/api/info",pin)));
            return "Conectado a "+obj.optString("name",ip)+" • "+obj.optString("platform");});}
    private void search(){job("Procurando na rede",()->{
        List<JSONObject> devices=Net.discover(getApplicationContext());
        ui.post(()->{
            if(devices.isEmpty()){dialog("Busca","Nenhum aparelho encontrado. Informe o IP manualmente. Ambos devem estar no mesmo Wi-Fi/hotspot.");return;}
            String[] names=new String[devices.size()];
            for(int n=0;n<devices.size();n++)names[n]=devices.get(n).optString("name")+" — "+devices.get(n).optString("ip");
            new AlertDialog.Builder(this).setTitle("Selecione um dispositivo").setItems(names,(d,idx)->{
                remoteIp.setText(devices.get(idx).optString("ip"));toast("Digite o PIN mostrado no aparelho escolhido.");
            }).show();
        });return devices.size()+" dispositivo(s) encontrado(s)";
    });}
    private void openRemote(String path){try{String ip=peerIp(),pin=peerPin();savePeer();visit(Net.url(ip,path,pin));}
        catch(Exception e){dialog("Dispositivo remoto",e.getMessage());}}
    private void pickFiles(){try{peerIp();peerPin();savePeer();}catch(Exception e){dialog("Antes de enviar",e.getMessage());return;}
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK);
    }
    private void sendFiles(Intent data){
        ArrayList<Uri> sources=new ArrayList<>();if(data.getClipData()!=null){
            ClipData cd=data.getClipData();for(int k=0;k<cd.getItemCount();k++)sources.add(cd.getItemAt(k).getUri());
        }else if(data.getData()!=null)sources.add(data.getData());
        final String ip=peerIp(),pin=peerPin();
        job("Enviando arquivos",()->{
            for(Uri uri:sources){
                String name="arquivo",type="application/octet-stream";long size=-1;
                try(Cursor c=getContentResolver().query(uri,null,null,null,null)){
                    if(c!=null&&c.moveToFirst()){
                        int n=c.getColumnIndex(OpenableColumns.DISPLAY_NAME),s=c.getColumnIndex(OpenableColumns.SIZE);
                        if(n>=0)name=c.getString(n);if(s>=0&&!c.isNull(s))size=c.getLong(s);
                    }
                }
                if(size<0){try(InputStream in=getContentResolver().openInputStream(uri)){
                    if(in==null)throw new IOException("Arquivo indisponível");byte[] buf=new byte[131072];long n=0;int r;
                    while((r=in.read(buf))!=-1)n+=r;size=n;
                }}
                String url=Net.url(ip,"/api/send?name="+Uri.encode(name),pin);
                HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
                c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(8000);c.setReadTimeout(240000);
                c.setRequestProperty("Content-Type",type);c.setFixedLengthStreamingMode(size);
                try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=c.getOutputStream()){
                    if(in==null)throw new IOException("Arquivo não encontrado");byte[] buf=new byte[131072];int r;
                    while((r=in.read(buf))!=-1)out.write(buf,0,r);
                }
                Net.read(c);
            }return "✅ "+sources.size()+" arquivo(s) enviado(s)";
        });
    }
    private void sendMessage(){final String msg=sendText.getText().toString().trim();if(msg.isEmpty())return;
        try{String ip=peerIp(),pin=peerPin();savePeer();job("Enviando mensagem",()->{
            Net.post(Net.url(ip,"/api/message",pin),msg.getBytes(StandardCharsets.UTF_8));ui.post(()->sendText.setText(""));return "Mensagem entregue";
        });}catch(Exception e){dialog("Mensagem",e.getMessage());}}
    private void refreshMessages(){LanService s=LanService.active;if(s==null)return;
        job("Atualizando mensagens",()->{
            JSONObject obj=new JSONObject(Net.get(Net.url("127.0.0.1","/api/messages",s.getPin())));
            JSONArray arr=obj.optJSONArray("messages");StringBuilder b=new StringBuilder();
            if(arr!=null)for(int k=Math.max(0,arr.length()-12);k<arr.length();k++){
                JSONObject m=arr.getJSONObject(k);b.append("• ").append(m.optString("text")).append("\n\n");
            }
            String result=b.length()==0?"Nenhuma mensagem recebida.":b.toString();ui.post(()->messagesLabel.setText(result));return "Mensagens atualizadas";
        });
    }
    private void refreshReceived(){LanService s=LanService.active;if(s==null)return;
        job("Consultando recebidos",()->{
            JSONObject obj=new JSONObject(Net.get(Net.url("127.0.0.1","/api/files",s.getPin())));
            JSONArray arr=obj.optJSONArray("files");StringBuilder b=new StringBuilder("Salvos em Downloads/ConectaTudo:\n");
            if(arr!=null)for(int k=0;k<Math.min(18,arr.length());k++){
                JSONObject f=arr.getJSONObject(k);b.append("• ").append(f.optString("name")).append("\n");
            }ui.post(()->receivedLabel.setText(b.toString()));return "Lista atualizada";
        });
    }
    private void askProjection(){
        LanService s=LanService.active;if(s==null){dialog("Compartilhamento","Inicie o servidor antes.");return;}
        MediaProjectionManager pm=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(pm.createScreenCaptureIntent(),SCREEN);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==SCREEN){
            if(result==RESULT_OK&&data!=null){
                Intent i=new Intent(this,LanService.class);i.setAction(LanService.ACTION_PROJECTION);
                i.putExtra(LanService.EXTRA_RESULT_CODE,result);i.putExtra(LanService.EXTRA_RESULT_DATA,data);
                startForegroundService(i);toast("Tela autorizada. Pode abrir 'Ver tela' no outro dispositivo.");
            }return;
        }
        if(request==PICK){if(result==RESULT_OK&&data!=null){try{sendFiles(data);}catch(Exception e){dialog("Arquivo",e.getMessage());}}return;}
        IntentResult scan=IntentIntegrator.parseActivityResult(request,result,data);
        if(scan!=null&&scan.getContents()!=null){
            String raw=scan.getContents();Uri u=Uri.parse(raw);
            if("conectatudo".equals(u.getScheme()))readDeeplink(new Intent(Intent.ACTION_VIEW,u));
            else dialog("QR Code","Este código não é do ConectaTudo.");
        }
    }
}
