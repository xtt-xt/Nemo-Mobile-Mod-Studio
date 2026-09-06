package com.xtt.mcmodmaker;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import com.xtt.mcmodmaker.util.UiUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import dev1503.oreui.StyleSheet;
import dev1503.oreui.widgets.OreButton;
import dev1503.oreui.widgets.OreTextView;

/** 渠道图的应用内裁剪页。输出仅写入应用缓存，不会修改用户原图。 */
public class ChannelCropActivity extends Activity {
    public static final String EXTRA_SOURCE_URI = "source_uri";
    public static final String EXTRA_WIDTH = "crop_width";
    public static final String EXTRA_HEIGHT = "crop_height";
    public static final String EXTRA_NAME = "source_name";
    public static final String RESULT_CROPPED_URI = "cropped_uri";
    public static final String RESULT_CROPPED_NAME = "cropped_name";

    private CropView cropView;
    private int outWidth, outHeight;
    private String sourceName;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        outWidth = Math.max(1, getIntent().getIntExtra(EXTRA_WIDTH, 992));
        outHeight = Math.max(1, getIntent().getIntExtra(EXTRA_HEIGHT, 558));
        sourceName = getIntent().getStringExtra(EXTRA_NAME);
        Bitmap bitmap = decode(getIntent().getStringExtra(EXTRA_SOURCE_URI));
        if (bitmap == null) { UiUtils.toast(this, "无法读取图片"); finish(); return; }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#202020"));
        root.setPadding(UiUtils.dp(this, 12), UiUtils.dp(this, 10), UiUtils.dp(this, 12), UiUtils.dp(this, 10));
        OreTextView hint = new OreTextView(this);
        hint.setText("拖动图片调整裁剪位置 · 输出 " + outWidth + "×" + outHeight);
        hint.setTextColor(Color.WHITE); hint.setTextSize(14); hint.setGravity(Gravity.CENTER);
        root.addView(hint, new LinearLayout.LayoutParams(-1, UiUtils.dp(this, 42)));
        cropView = new CropView(this, bitmap, (float) outWidth / outHeight);
        root.addView(cropView, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.CENTER); actions.setPadding(0, UiUtils.dp(this, 8), 0, 0);
        OreButton cancel = new OreButton(this); cancel.setText("取消"); cancel.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        cancel.setOnClickListener(v -> finish());
        OreButton ok = new OreButton(this); ok.setText("确认裁剪"); ok.setStyleSheet(StyleSheet.STYLE_GREEN);
        ok.setOnClickListener(v -> saveCrop());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, UiUtils.dp(this, 48), 1));
        actions.addView(ok, new LinearLayout.LayoutParams(0, UiUtils.dp(this, 48), 1));
        root.addView(actions);
        setContentView(root);
    }

    private Bitmap decode(String source) {
        if (source == null) return null;
        InputStream in = null;
        try { in = getContentResolver().openInputStream(Uri.parse(source)); return BitmapFactory.decodeStream(in); }
        catch (Exception ignored) { return null; }
        finally { try { if (in != null) in.close(); } catch (Exception ignored) {} }
    }

    private void saveCrop() {
        try {
            Bitmap result = cropView.createOutput(outWidth, outHeight);
            File dir = new File(getCacheDir(), "channel_crops");
            if (!dir.exists()) dir.mkdirs();
            File output = new File(dir, "channel_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream stream = new FileOutputStream(output);
            if (!result.compress(Bitmap.CompressFormat.JPEG, 92, stream)) throw new Exception("图片编码失败");
            stream.flush(); stream.close();
            Intent data = new Intent();
            data.putExtra(RESULT_CROPPED_URI, Uri.fromFile(output).toString());
            data.putExtra(RESULT_CROPPED_NAME, (sourceName == null ? "channel" : sourceName.replaceAll("\\.[^.]*$", "")) + "_crop.jpg");
            setResult(RESULT_OK, data); finish();
        } catch (Exception e) { UiUtils.toast(this, "裁剪失败：" + e.getMessage()); }
    }

    private static class CropView extends View {
        private final Bitmap bitmap; private final float ratio; private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float scale, x, y, lastX, lastY; private RectF crop = new RectF();
        CropView(Context context, Bitmap bitmap, float ratio) { super(context); this.bitmap = bitmap; this.ratio = ratio; setBackgroundColor(Color.BLACK); }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            float cw = w * .92f, ch = cw / ratio;
            if (ch > h * .82f) { ch = h * .82f; cw = ch * ratio; }
            crop.set((w-cw)/2f, (h-ch)/2f, (w+cw)/2f, (h+ch)/2f);
            scale = Math.max(cw / bitmap.getWidth(), ch / bitmap.getHeight());
            x = (w - bitmap.getWidth() * scale) / 2f; y = (h - bitmap.getHeight() * scale) / 2f;
        }
        private void clamp() {
            float bw=bitmap.getWidth()*scale, bh=bitmap.getHeight()*scale;
            x=Math.min(crop.left, Math.max(crop.right-bw, x)); y=Math.min(crop.top, Math.max(crop.bottom-bh, y));
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); c.drawBitmap(bitmap, null, new RectF(x,y,x+bitmap.getWidth()*scale,y+bitmap.getHeight()*scale), paint);
            paint.setColor(0x99000000); c.drawRect(0,0,getWidth(),crop.top,paint); c.drawRect(0,crop.bottom,getWidth(),getHeight(),paint); c.drawRect(0,crop.top,crop.left,crop.bottom,paint); c.drawRect(crop.right,crop.top,getWidth(),crop.bottom,paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3); paint.setColor(Color.WHITE); c.drawRect(crop,paint); paint.setStyle(Paint.Style.FILL);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if(e.getAction()==MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY();return true;}
            if(e.getAction()==MotionEvent.ACTION_MOVE){x+=e.getX()-lastX;y+=e.getY()-lastY;lastX=e.getX();lastY=e.getY();clamp();invalidate();return true;} return true;
        }
        Bitmap createOutput(int w,int h) {
            Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            Canvas c=new Canvas(out);
            int left=Math.max(0, Math.round((crop.left-x)/scale));
            int top=Math.max(0, Math.round((crop.top-y)/scale));
            int right=Math.min(bitmap.getWidth(), Math.round((crop.right-x)/scale));
            int bottom=Math.min(bitmap.getHeight(), Math.round((crop.bottom-y)/scale));
            if (right <= left || bottom <= top) throw new IllegalStateException("裁剪区域无效");
            c.drawBitmap(bitmap, new android.graphics.Rect(left, top, right, bottom),
                    new RectF(0,0,w,h), paint);
            return out;
        }
    }
}
