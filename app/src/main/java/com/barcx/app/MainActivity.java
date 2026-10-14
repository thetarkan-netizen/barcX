package com.barcx.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import org.json.*;
import java.util.*;
import java.io.*;

public class MainActivity extends Activity {
    private static final int IMPORT=70, EXPORT=71;
    private JSONArray stocks=new JSONArray(), counts=new JSONArray();
    private String activeRaf="", activeLoc="", activeDepo="", scanTarget="raf";
    private LinearLayout root,stockArea,recordArea;
    private EditText krlInput,qtyInput,rafInput;
    private Spinner stockSelector;
    private ArrayList<String> choices=new ArrayList<>();
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n);}
    private TextView label(String str,int sp){TextView t=new TextView(this);t.setText(str);t.setTextColor(Color.rgb(30,40,50));t.setTextSize(sp);t.setPadding(0,dp(7),0,dp(7));return t;}
    private Button button(String str,View.OnClickListener fn){Button b=new Button(this);b.setText(str);b.setOnClickListener(fn);return b;}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private JSONArray restore(String key){try{return new JSONArray(getPreferences(MODE_PRIVATE).getString(key,"[]"));}catch(Exception e){return new JSONArray();}}
    private void save(){getPreferences(MODE_PRIVATE).edit().putString("stocks",stocks.toString()).putString("counts",counts.toString()).putString("raf",activeRaf).putString("loc",activeLoc).putString("depo",activeDepo).apply();}
    private JSONObject obj(String stock,String raf,String loc,String depo,String code) throws Exception{
        JSONObject j=new JSONObject();j.put("stock",stock);j.put("raf",raf);j.put("loc",loc);j.put("depo",depo);j.put("barcode",code);return j;
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);stocks=restore("stocks");counts=restore("counts");
        activeRaf=getPreferences(MODE_PRIVATE).getString("raf","");activeLoc=getPreferences(MODE_PRIVATE).getString("loc","");activeDepo=getPreferences(MODE_PRIVATE).getString("depo","");
        if(stocks.length()==0){try{stocks.put(obj("A","86918091","a1-1","DEMO","86918091"));}catch(Exception ignored){}save();}
        render();
    }
    private void scan(String target){
        scanTarget=target;
        new IntentIntegrator(this).setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES)
            .setPrompt(target.equals("raf")?"Raf barkodunu okut":"KRL etiketini okut")
            .setBeepEnabled(true).setOrientationLocked(false).initiateScan();
    }
    private void chooseRaf(String code){
        code=code.trim();JSONObject found=null;
        for(int i=0;i<stocks.length();i++){JSONObject j=stocks.optJSONObject(i);
            if(j!=null&&(code.equals(j.optString("barcode"))||code.equals(j.optString("raf")))){found=j;break;}
        }
        if(found==null){toast("Raf bulunamadı: "+code);return;}
        activeRaf=found.optString("raf");activeLoc=found.optString("loc");activeDepo=found.optString("depo");save();render();
    }
    private void row(LinearLayout p,View v){p.addView(v);}
    private String clean(String s){return s==null?"":s.trim();}
    private void render(){
        ScrollView sc=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(15),dp(18),dp(25));sc.addView(root);setContentView(sc);
        row(root,label("barcX  •  Kör Sayım",25));
        row(root,label("Demo: 86918091  →  Raf 86918091 / a1-1  →  Stok A",13));
        row(root,button("RAF QR / BARKOD OKUT",v->scan("raf")));
        rafInput=new EditText(this);rafInput.setSingleLine(true);rafInput.setHint("Raf barkodunu manuel gir");row(root,rafInput);
        row(root,button("RAF BUL",v->chooseRaf(clean(rafInput.getText().toString()))));
        row(root,button("STOK / RAF VERİSİ YÜKLE (CSV)",v->{Intent in=new Intent(Intent.ACTION_OPEN_DOCUMENT);in.setType("*/*");in.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(in,IMPORT);}));
        if(activeRaf.isEmpty()){row(root,label("Önce raf okutun.",16));return;}
        row(root,label("Aktif raf: "+activeRaf+"   |   Lokasyon: "+activeLoc,19));
        row(root,label("Depo: "+activeDepo+"  •  Sistem adetleri gizli",13));
        choices.clear();for(int i=0;i<stocks.length();i++){JSONObject s=stocks.optJSONObject(i);
            if(s!=null&&activeRaf.equals(s.optString("raf"))&&!choices.contains(s.optString("stock")))choices.add(s.optString("stock"));
        }
        row(root,label("Rafta kayıtlı stoklar: "+android.text.TextUtils.join(", ",choices),15));
        if(choices.isEmpty()){row(root,label("Bu raf için stok kaydı bulunamadı.",14));return;}
        row(root,button("KRL BARKOD OKUT",v->scan("krl")));
        krlInput=new EditText(this);krlInput.setSingleLine(true);krlInput.setHint("KRL benzersiz barkod");row(root,krlInput);
        row(root,label("Stok seç",15));
        stockSelector=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,choices);stockSelector.setAdapter(adapter);row(root,stockSelector);
        qtyInput=new EditText(this);qtyInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);qtyInput.setHint("Fiziki adet");row(root,qtyInput);
        row(root,button("KAYDET",v->storeCount()));
        row(root,label("Bu rafın sayım kayıtları",18));
        int count=0,sum=0;
        for(int i=0;i<counts.length();i++){JSONObject j=counts.optJSONObject(i);if(j==null||!activeRaf.equals(j.optString("raf")))continue;
            count++;sum+=j.optInt("qty");row(root,label(j.optString("krl")+"  |  "+j.optString("stock")+"  |  "+j.optInt("qty")+" adet",14));
        }
        row(root,label("Toplam: "+count+" etiket, "+sum+" adet",16));
        row(root,button("SAYIM KAYITLARINI CSV AKTAR",v->{Intent out=new Intent(Intent.ACTION_CREATE_DOCUMENT);out.addCategory(Intent.CATEGORY_OPENABLE);out.setType("text/csv");out.putExtra(Intent.EXTRA_TITLE,"barcX_sayim.csv");startActivityForResult(out,EXPORT);}));
    }
    private void storeCount(){
        String code=clean(krlInput.getText().toString()),n=clean(qtyInput.getText().toString());
        if(code.isEmpty()||n.isEmpty()){toast("KRL ve adet zorunludur.");return;}
        int qty;try{qty=Integer.parseInt(n);if(qty<0)throw new Exception();}catch(Exception e){toast("Geçerli adet girin.");return;}
        String stock=(String)stockSelector.getSelectedItem();if(stock==null){toast("Stok seçin.");return;}
        for(int i=0;i<counts.length();i++){JSONObject j=counts.optJSONObject(i);if(j!=null&&code.equals(j.optString("krl"))){
            new AlertDialog.Builder(this).setTitle("Mükerrer KRL").setMessage("Bu KRL daha önce kaydedildi. Yeni kayıt oluşturulmadı: "+code).setPositiveButton("Tamam",null).show();return;
        }}
        try{JSONObject j=obj(stock,activeRaf,activeLoc,activeDepo,"");j.put("krl",code);j.put("qty",qty);j.put("time",System.currentTimeMillis());counts.put(j);save();toast("Kayıt tamamlandı");render();}catch(Exception e){toast("Kayıt hatası");}
    }
    private String csv(String s){return "\"" + (s==null?"":s).replace("\"","\"\"") + "\"";}
    private void exportCsv(android.net.Uri uri){
        try(OutputStream out=getContentResolver().openOutputStream(uri);Writer w=new OutputStreamWriter(out,"UTF-8")){
            w.write("\uFEFFStok,Raf,Lokasyon,Depo,KRL,Adet,TarihSaat\n");
            for(int i=0;i<counts.length();i++){JSONObject j=counts.optJSONObject(i);if(j==null)continue;
                String date=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(new Date(j.optLong("time")));
                w.write(csv(j.optString("stock"))+","+csv(j.optString("raf"))+","+csv(j.optString("loc"))+","+csv(j.optString("depo"))+","+csv(j.optString("krl"))+","+j.optInt("qty")+","+csv(date)+"\n");
            }
            toast("CSV kaydedildi");
        }catch(Exception e){toast("Dışa aktarma hatası: "+e.getMessage());}
    }
    private void importCsv(android.net.Uri uri){
        try(InputStream in=getContentResolver().openInputStream(uri);BufferedReader r=new BufferedReader(new InputStreamReader(in,"UTF-8"))){
            String head=r.readLine();if(head==null)throw new Exception("Boş dosya");
            String[] cols=head.replace("\uFEFF","").split("[;,]",-1);
            int s=-1,rf=-1,l=-1,b=-1,d=-1;
            for(int i=0;i<cols.length;i++){String c=cols[i].replace("\"","").trim().toLowerCase(new Locale("tr","TR"));
                if(c.equals("stok"))s=i;else if(c.equals("raf"))rf=i;else if(c.equals("lokasyon"))l=i;else if(c.equals("raf barkodu")||c.equals("raf_barkodu"))b=i;else if(c.equals("depo"))d=i;
            }
            if(s<0||rf<0||l<0)throw new Exception("Stok, Raf, Lokasyon başlıkları gerekli");
            JSONArray newer=new JSONArray();String line;
            while((line=r.readLine())!=null){if(line.trim().isEmpty())continue;String[] p=line.split("[;,]",-1);
                if(p.length<=Math.max(s,Math.max(rf,l)))continue;
                String stock=p[s].trim(),raf=p[rf].trim(),loc=p[l].trim();if(stock.isEmpty()||raf.isEmpty())continue;
                String code=b>=0&&b<p.length&&!p[b].trim().isEmpty()?p[b].trim():raf;
                String depo=d>=0&&d<p.length?p[d].trim():"";
                newer.put(obj(stock,raf,loc,depo,code));
            }
            if(newer.length()==0)throw new Exception("Geçerli veri bulunamadı");
            stocks=newer;activeRaf="";activeLoc="";activeDepo="";save();render();toast(newer.length()+" stok/raf kaydı yüklendi");
        }catch(Exception e){toast("CSV okunamadı: "+e.getMessage());}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==IMPORT){if(result==RESULT_OK&&data!=null)importCsv(data.getData());return;}
        if(request==EXPORT){if(result==RESULT_OK&&data!=null)exportCsv(data.getData());return;}
        IntentResult scan=IntentIntegrator.parseActivityResult(request,result,data);
        if(scan!=null&&scan.getContents()!=null){if(scanTarget.equals("raf"))chooseRaf(scan.getContents());else if(krlInput!=null)krlInput.setText(scan.getContents());}
    }
}
