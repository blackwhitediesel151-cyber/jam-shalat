package id.blackwhite.jamtv;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;
import android.view.WindowManager;
import org.json.JSONObject;
import org.json.JSONException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Tiny native Canvas clock. No WebView, media, ads, databases, or third-party libraries. */
public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ClockView clockView;
    private android.content.SharedPreferences prefs;
    private volatile String fajr="--:--", sunrise="--:--", dhuhr="--:--", asr="--:--", maghrib="--:--", isha="--:--", hijri="";
    private volatile String syncedDay="";
    private boolean fetching=false;
    private volatile long lastAttemptMs=0L;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            if (!today.equals(syncedDay)) fetchTimings();
            if (clockView != null) clockView.invalidate();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemBars();
        prefs = getSharedPreferences("timings", MODE_PRIVATE);
        loadCache();
        clockView = new ClockView();
        setContentView(clockView);
        fetchTimings();
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    @Override public void onWindowFocusChanged(boolean focus) { super.onWindowFocusChanged(focus); if (focus) hideSystemBars(); }
    @Override protected void onResume() { super.onResume(); handler.removeCallbacks(ticker); handler.post(ticker); }
    @Override protected void onPause() { handler.removeCallbacks(ticker); super.onPause(); }

    private void loadCache() {
        fajr=prefs.getString("fajr","--:--"); sunrise=prefs.getString("sunrise","--:--"); dhuhr=prefs.getString("dhuhr","--:--"); asr=prefs.getString("asr","--:--"); maghrib=prefs.getString("maghrib","--:--"); isha=prefs.getString("isha","--:--"); hijri=prefs.getString("hijri",""); syncedDay=prefs.getString("day","");
    }

    private synchronized void fetchTimings() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        if (fetching || today.equals(syncedDay) || System.currentTimeMillis()-lastAttemptMs < 15L*60L*1000L) return;
        fetching=true; lastAttemptMs=System.currentTimeMillis();
        new Thread(() -> {
            HttpURLConnection connection=null;
            try {
                URL url=new URL("https://api.aladhan.com/v1/timingsByCity?city=Kudus&country=Indonesia&method=20");
                connection=(HttpURLConnection)url.openConnection();
                connection.setRequestMethod("GET"); connection.setConnectTimeout(7000); connection.setReadTimeout(7000); connection.setUseCaches(true);
                try (BufferedReader reader=new BufferedReader(new InputStreamReader(connection.getInputStream(),"UTF-8"))) {
                    StringBuilder body=new StringBuilder(); String line; while((line=reader.readLine())!=null) body.append(line);
                    JSONObject root=new JSONObject(body.toString()); JSONObject data=root.getJSONObject("data"); JSONObject t=data.getJSONObject("timings");
                    String nf=clean(t.optString("Fajr","--:--")); String ns=clean(t.optString("Sunrise","--:--")); String nd=clean(t.optString("Dhuhr","--:--")); String na=clean(t.optString("Asr","--:--")); String nm=clean(t.optString("Maghrib","--:--")); String ni=clean(t.optString("Isha","--:--"));
                    JSONObject h=data.getJSONObject("date").getJSONObject("hijri");
                    String nh=h.optString("day","")+" "+h.getJSONObject("month").optString("en","")+" "+h.optString("year","")+" H";
                    String apiDay=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());
                    fajr=nf; sunrise=ns; dhuhr=nd; asr=na; maghrib=nm; isha=ni; hijri=nh; syncedDay=apiDay;
                    prefs.edit().putString("fajr",nf).putString("sunrise",ns).putString("dhuhr",nd).putString("asr",na).putString("maghrib",nm).putString("isha",ni).putString("hijri",nh).putString("day",apiDay).apply();
                    handler.post(() -> { if(clockView!=null) clockView.invalidate(); });
                }
            } catch(Exception ignored) { /* Keep last valid cached timings; retry next day or relaunch. */ }
            finally { if(connection!=null) connection.disconnect(); fetching=false; }
        },"prayer-api").start();
    }
    private String clean(String s) { if(s==null)return "--:--"; s=s.trim(); return s.length()>=5?s.substring(0,5):"--:--"; }
    private int minutes(String s) { try { String[] p=s.split(":"); return Integer.parseInt(p[0])*60+Integer.parseInt(p[1]); } catch(Exception e){return -1;} }
    private String addMinutes(String s,int add) { int m=minutes(s); if(m<0)return "--:--"; m=(m+add)%1440; return String.format(Locale.US,"%02d:%02d",m/60,m%60); }
    private String[] prayerData() {
        return new String[]{fajr,addMinutes(sunrise,20),dhuhr,asr,maghrib,isha,tahajjud()};
    }
    private String tahajjud() {
        int i=minutes(isha), f=minutes(fajr); if(i<0||f<0)return "--:--";
        int gap=(f+1440-i)%1440; return String.format(Locale.US,"%02d:%02d",((i+(gap*2/3))%1440)/60,(i+(gap*2/3))%60);
    }
    private String nextPrayer() {
        String[] names={"Fajr","Dhuha","Dzuhur","Ashar","Maghrib","Isya","Tahajjud"}; String[] times=prayerData();
        Calendar c=Calendar.getInstance(); int now=c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE); int best=2000; String name="Fajr";
        for(int i=0;i<times.length;i++){int m=minutes(times[i]); if(m>=now&&m<best){best=m;name=names[i];}}
        if(best==2000) name="Fajr"; return name;
    }
    private long countdownMillis() {
        Calendar now=Calendar.getInstance(); int current=now.get(Calendar.HOUR_OF_DAY)*60+now.get(Calendar.MINUTE); String[] times=prayerData(); int best=2000;
        for(String t:times){int m=minutes(t); if(m>current&&m<best)best=m;}
        Calendar target=(Calendar)now.clone();
        if(best==2000){int f=minutes(fajr); if(f<0)return 0; target.add(Calendar.DAY_OF_YEAR,1); target.set(Calendar.HOUR_OF_DAY,f/60); target.set(Calendar.MINUTE,f%60);}
        else {target.set(Calendar.HOUR_OF_DAY,best/60);target.set(Calendar.MINUTE,best%60);}
        target.set(Calendar.SECOND,0);target.set(Calendar.MILLISECOND,0); return Math.max(0,target.getTimeInMillis()-now.getTimeInMillis());
    }

    private class ClockView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String[] names={"FAJR","DHUHA","DZUHUR","ASHAR","MAGHRIB","ISYA","TAHAJJUD"};
        ClockView(){super(MainActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        private void text(Canvas c,String s,float x,float y,float size,int color,boolean bold,Paint.Align align){p.setColor(color);p.setTextSize(size);p.setTypeface(bold?Typeface.create("sans-serif",Typeface.BOLD):Typeface.create("sans-serif",Typeface.NORMAL));p.setTextAlign(align);c.drawText(s,x,y,p);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.drawColor(0xFF000000);float w=getWidth(),h=getHeight();float scale=Math.min(w/1280f,h/720f);canvas.save();canvas.translate((w-1280*scale)/2,(h-720*scale)/2);canvas.scale(scale,scale);
            Calendar c=Calendar.getInstance(); String time=new SimpleDateFormat("HH:mm:ss",Locale.US).format(c.getTime());
            text(canvas,time,640,280,190,0xFFFFFFFF,true,Paint.Align.CENTER);
            String date=new SimpleDateFormat("EEEE, d MMMM yyyy",new Locale("id","ID")).format(c.getTime());
            text(canvas,date,640,335,25,0xFFAAAAAA,false,Paint.Align.CENTER);
            text(canvas,hijri.isEmpty()?"":hijri,640,370,21,0xFF888888,false,Paint.Align.CENTER);
            String next=nextPrayer(); long ms=countdownMillis()/1000; String cd=String.format(Locale.US,"%02d:%02d:%02d",ms/3600,(ms%3600)/60,ms%60);
            text(canvas,"Menuju "+next,640,420,23,0xFFAAAAAA,false,Paint.Align.CENTER); text(canvas,cd,640,475,48,0xFFFFFFFF,true,Paint.Align.CENTER);
            String[] times=prayerData(); float left=35, cell=1210f/7f; for(int i=0;i<7;i++){float x=left+cell*(i+.5f); text(canvas,names[i],x,590,16,0xFF888888,true,Paint.Align.CENTER);text(canvas,times[i],x,622,22,0xFFFFFFFF,false,Paint.Align.CENTER);}
            canvas.restore();
        }
    }
}
