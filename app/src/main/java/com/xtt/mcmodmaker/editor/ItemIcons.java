package com.xtt.mcmodmaker.editor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import com.xtt.mcmodmaker.core.BlockTextureLib;
import com.xtt.mcmodmaker.core.ItemRegistry;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 物品图标的统一入口。
 *
 * 三种来源，按顺序找：
 *  1. 方块 → 六面贴图实时渲染成立方体（自定义方块也走这条，贴图从工程里取）；
 *  2. 普通物品 → 内置的原版物品贴图（assets/item_icons.json 是 id → 贴图的对照表）；
 *  3. 都没有 → 占位图，物品是灰苹果，方块是灰立方体。
 *
 * 这样同一个格子里永远有图，不会出现「有的有图标有的没有」。
 */
public final class ItemIcons {

    private static List<String> paths;
    private static Map<String, Integer> items;

    /** 渲染结果缓存，键里带工程目录，换工程不会串。 */
    private static final LruCache<String, Bitmap> ICON_CACHE =
            new LruCache<String, Bitmap>(6 * 1024 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    /** 原始贴图缓存，缩放前的 16×16 小图。 */
    private static final LruCache<String, Bitmap> RAW_CACHE =
            new LruCache<String, Bitmap>(1024 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    private static Bitmap grayApple;

    private ItemIcons() {
    }

    /**
     * 取图标。除了连占位图都读不到，否则一定有一张图。
     *
     * @param id         如 minecraft:apple、minecraft:grass_block、ns:custom_block
     * @param projectDir 当前工程根目录，用于读自定义方块的贴图，可为 null
     * @param sizePx     输出边长
     */
    public static Bitmap get(Context context, String id, File projectDir, int sizePx) {
        if (context == null || id == null || id.isEmpty()) return null;
        String key = "i|" + id + "@" + sizePx + "@"
                + (projectDir == null ? "" : projectDir.getAbsolutePath());
        Bitmap cached = ICON_CACHE.get(key);
        if (cached != null) return cached;

        Bitmap out = build(context, id, projectDir, sizePx);
        if (out != null) ICON_CACHE.put(key, out);
        return out;
    }

    private static Bitmap build(Context context, String id, File projectDir, int sizePx) {
        // 1. 方块：有六面数据就渲染；有数据但缺贴图就用灰立方体
        if (BlockTextureLib.resolveFaces(context, id, projectDir) != null) {
            Bitmap cube = BlockIconRenderer.render(context, id, projectDir, sizePx);
            return cube != null ? cube : BlockIconRenderer.placeholder(context, sizePx);
        }

        // 2. 普通物品：内置的原版贴图
        Bitmap tex = itemTexture(context, id);
        if (tex != null) return scale(tex, sizePx);

        // 3. 占位：清单里记着是方块就给灰立方体，否则灰苹果
        if (ItemRegistry.isBlock(context, id)) return BlockIconRenderer.placeholder(context, sizePx);
        return grayApple(context, sizePx);
    }

    /** 物品 id → 内置贴图。带不带 minecraft: 前缀都认。 */
    private static Bitmap itemTexture(Context context, String id) {
        ensureLoaded(context);
        Integer idx = items.get(id);
        if (idx == null) {
            int c = id.indexOf(':');
            String shortId = c >= 0 ? id.substring(c + 1) : id;
            idx = items.get("minecraft:" + shortId);
        }
        if (idx == null || idx < 0 || idx >= paths.size()) return null;

        String name = paths.get(idx);
        Bitmap cached = RAW_CACHE.get(name);
        if (cached != null) return cached;
        try (InputStream in = context.getAssets().open("item_tex/" + name + ".png")) {
            Bitmap bmp = BitmapFactory.decodeStream(in);
            if (bmp != null) RAW_CACHE.put(name, bmp);
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap grayApple(Context context, int sizePx) {
        if (grayApple == null) {
            try (InputStream in = context.getAssets().open("placeholder_item.png")) {
                grayApple = BitmapFactory.decodeStream(in);
            } catch (Exception ignored) {
            }
        }
        return grayApple == null ? null : scale(grayApple, sizePx);
    }

    private static Bitmap scale(Bitmap src, int sizePx) {
        if (src == null) return null;
        if (src.getWidth() == sizePx && src.getHeight() == sizePx) return src;
        // 像素风不能用双线性，会把 16×16 的贴图糊成一团
        return Bitmap.createScaledBitmap(src, sizePx, sizePx, false);
    }

    private static synchronized void ensureLoaded(Context context) {
        if (items != null) return;
        List<String> p = new ArrayList<>();
        Map<String, Integer> m = new HashMap<>();
        try (InputStream in = context.getAssets().open("item_icons.json")) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            JSONObject root = new JSONObject(bos.toString("UTF-8"));
            JSONArray arr = root.optJSONArray("textures");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) p.add(arr.optString(i));
            }
            JSONObject map = root.optJSONObject("items");
            if (map != null) {
                java.util.Iterator<String> it = map.keys();
                while (it.hasNext()) {
                    String id = it.next();
                    m.put(id, map.optInt(id, -1));
                }
            }
        } catch (Exception ignored) {
        }
        paths = p;
        items = m;
    }

    /** 内置物品贴图数量，关于页显示用。 */
    public static int itemTextureCount(Context context) {
        ensureLoaded(context);
        return paths.size();
    }

    public static void clearCache() {
        ICON_CACHE.evictAll();
        RAW_CACHE.evictAll();
    }
}
