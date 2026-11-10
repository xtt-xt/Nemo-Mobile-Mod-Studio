package com.xtt.mcmodmaker.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 原版方块的真实形状。
 *
 * 基岩版没有「原版方块模型文件」可抄 —— 官方 bedrock-samples 里 1352 个方块，
 * blocks.json 中带 geometry 的 0 个，models/entity 里 190 个 geo 全是生物。
 * 台阶、楼梯、栅栏、箱子的多边形形状是硬编码在引擎里的，资源包里根本没有。
 *
 * 所以这里按游戏里的实际尺寸把形状写死：每个方块由若干个盒子拼成，
 * 尺寸取整到 1/16 方块，和游戏里的碰撞盒 / 视觉形状一致。
 * 贴图直接用 blocks.json 给的六面路径，靠「锁 UV」从整块贴图里切出对应区域，
 * 所以外观和创造一个模式物品栏里的一致。
 *
 * 覆盖范围：台阶、楼梯、栅栏、栅栏门、墙、门、活板门、按钮、压力板、地毯、
 * 雪层、箱子/潜影盒、告示牌、蜡烛、灯笼、营火、铁砧、漏斗、炼药锅、蛋糕、
 * 花盆、梯子、火把、堆肥桶、讲台、脚手架，共 400 多个原版方块。
 */
public final class VanillaShapes {

    /** 一个形状可以由多个盒子拼成，这里只是给调用方看个标记。 */
    private VanillaShapes() {
    }

    /**
     * 这个方块有没有写死的原版形状。
     *
     * @param blockId 完整 id，如 minecraft:oak_stairs
     */
    public static boolean canShape(String blockId) {
        return kind(blockId) != null;
    }

    /** 形状种类，用来决定怎么拼。 */
    private enum Kind {
        SLAB, STAIRS, FENCE, FENCE_GATE, WALL, DOOR, TRAPDOOR, BUTTON, PRESSURE_PLATE,
        CARPET, SNOW, CHEST, SHULKER, SIGN, CANDLE, LANTERN, CAMPFIRE, ANVIL, HOPPER,
        CAULDRON, CAKE, FLOWER_POT, LADDER, TORCH, COMPOSTER, LECTERN, SCAFFOLDING,
        CROSS, BED, BELL, BREWING, POT, ROD, FRAME, BEACON, PORTAL_FRAME, BANNER
    }

    /**
     * 判断方块属于哪类形状。
     *
     * 顺序有讲究：candle_cake 要先当蛋糕，candle 才是蜡烛；
     * double_slab 要当整块而不是半块。
     */
    private static Kind kind(String id) {
        if (id == null) return null;
        String n = shortName(id);
        if (n.isEmpty()) return null;

        if (n.equals("fence_gate") || n.endsWith("_fence_gate")) return Kind.FENCE_GATE;
        if (n.endsWith("_fence")) return Kind.FENCE;
        if (n.endsWith("_stairs")) return Kind.STAIRS;
        if (n.endsWith("_slab")) return Kind.SLAB;
        if (n.endsWith("_wall")) return Kind.WALL;
        if (n.equals("trapdoor") || n.endsWith("_trapdoor")) return Kind.TRAPDOOR;
        if (n.endsWith("_pressure_plate")) return Kind.PRESSURE_PLATE;
        if (n.endsWith("_button")) return Kind.BUTTON;
        if (n.endsWith("_door")) return Kind.DOOR;
        if (n.endsWith("_hanging_sign") || n.endsWith("_sign")) return Kind.SIGN;
        if (n.endsWith("candle_cake")) return Kind.CAKE;
        if (n.endsWith("candle")) return Kind.CANDLE;
        if (n.endsWith("lantern")) return Kind.LANTERN;
        if (n.endsWith("campfire")) return Kind.CAMPFIRE;
        if (n.endsWith("anvil")) return Kind.ANVIL;
        if (n.endsWith("carpet") || n.endsWith("moss_carpet")) return Kind.CARPET;
        if (n.equals("snow_layer")) return Kind.SNOW;
        if (n.equals("hopper")) return Kind.HOPPER;
        if (n.endsWith("cauldron")) return Kind.CAULDRON;
        if (n.equals("cake")) return Kind.CAKE;
        if (n.equals("flower_pot")) return Kind.FLOWER_POT;
        if (n.equals("ladder")) return Kind.LADDER;
        if (n.endsWith("torch")) return Kind.TORCH;
        if (n.equals("composter")) return Kind.COMPOSTER;
        if (n.equals("lectern")) return Kind.LECTERN;
        if (n.equals("scaffolding")) return Kind.SCAFFOLDING;
        if (n.equals("bed") || n.endsWith("_bed")) return Kind.BED;
        if (n.equals("bell")) return Kind.BELL;
        if (n.equals("brewing_stand")) return Kind.BREWING;
        if (n.endsWith("decorated_pot") || n.equals("flower_pot")) return Kind.POT;
        if (n.equals("end_rod")) return Kind.ROD;
        if (n.equals("lightning_rod") || n.endsWith("_lightning_rod")) return Kind.ROD;
        if (n.endsWith("chain")) return Kind.ROD;
        if (n.equals("frame") || n.equals("glow_frame")) return Kind.FRAME;
        if (n.equals("beacon")) return Kind.BEACON;
        if (n.equals("end_portal_frame")) return Kind.PORTAL_FRAME;
        if (n.equals("wall_banner")) return Kind.BANNER;
        if (isPlant(n)) return Kind.CROSS;
        if (n.endsWith("shulker_box")) return Kind.SHULKER;
        if (n.endsWith("chest") || n.equals("ender_chest")) return Kind.CHEST;
        return null;
    }

    /** 原版里画成十字平面的植物类方块（贴图大量透明，画成立方体会变成实心块）。 */
    private static final String[] PLANTS = {
            "sapling", "flower", "tulip", "poppy", "dandelion", "orchid", "allium", "bluet",
            "daisy", "lily_of_the_valley", "mushroom", "fern", "grass", "seagrass", "kelp",
            "vine", "wheat", "carrot", "potato", "beetroot", "torchflower", "sunflower",
            "lilac", "rose_bush", "peony", "wildflowers", "coral_fan", "coral_wall_fan",
            "dead_bush", "sugar_cane", "sweet_berry", "glow_lichen", "hanging_roots",
            "spore_blossom", "pink_petals", "cactus_flower", "nether_wart", "cocoa",
            "chorus_flower", "azalea", "bamboo_sapling", "wither_rose", "pitcher_plant"
    };

    private static boolean isPlant(String n) {
        if (n.equals("short_grass") || n.equals("double_plant") || n.equals("reeds")) return true;
        for (String k : PLANTS) {
            if (n.contains(k)) return true;
        }
        return false;
    }

    private static String shortName(String id) {
        int c = id.indexOf(':');
        return (c >= 0 ? id.substring(c + 1) : id).toLowerCase();
    }

    /**
     * 生成形状。
     *
     * @param sixFaces 六面贴图路径，顺序 up/down/north/south/east/west
     * @return 没有对应形状时返回 null
     */
    public static BlockModel build(String blockId, String[] sixFaces) {
        Kind k = kind(blockId);
        if (k == null || sixFaces == null || sixFaces.length < 6) return null;
        String n = shortName(blockId);

        // 每个盒子按「顶面 / 底面 / 四个侧面」取贴图，和游戏里一致
        String[] box = {
                sixFaces[0], sixFaces[1], sixFaces[2],
                sixFaces[3], sixFaces[4], sixFaces[5]
        };

        List<BlockModel.Face> f = new ArrayList<>();
        switch (k) {
            case SLAB:
                if (n.contains("double")) {
                    box(f, 0, 0, 0, 16, 16, 16, box, true);
                } else {
                    box(f, 0, 0, 0, 16, 8, 16, box, true);
                }
                break;

            case STAIRS:
                // 下半块整块 + 后半段的上半块（踏步统一朝北，图标看起来最像游戏）
                box(f, 0, 0, 0, 16, 8, 16, box, true);
                box(f, 0, 8, 0, 16, 16, 8, box, true);
                break;

            case FENCE:
                // 柱 4×16×4，两个方向各两根横杆（厚 2、高 3）
                box(f, 6, 0, 6, 10, 16, 10, box, false);
                box(f, 0, 12, 7, 16, 15, 9, box, false);
                box(f, 0, 6, 7, 16, 9, 9, box, false);
                box(f, 7, 12, 0, 9, 15, 16, box, false);
                box(f, 7, 6, 0, 9, 9, 16, box, false);
                break;

            case FENCE_GATE:
                // 两根竖框 + 两块横板
                box(f, 0, 0, 7, 2, 16, 9, box, false);
                box(f, 14, 0, 7, 16, 16, 9, box, false);
                box(f, 2, 11, 7, 14, 15, 9, box, false);
                box(f, 2, 4, 7, 14, 8, 9, box, false);
                break;

            case WALL:
                // 中间柱 8×16×8 + 顶部向两侧探出的压顶
                box(f, 4, 0, 4, 12, 16, 12, box, false);
                box(f, 0, 13, 4, 4, 16, 12, box, false);
                box(f, 12, 13, 4, 16, 16, 12, box, false);
                break;

            case DOOR:
                // 一整面 16×16、厚 3，贴在北侧
                box(f, 0, 0, 0, 16, 16, 3, box, false);
                break;

            case TRAPDOOR:
                // 关着的时候是贴在方块顶部的一块 16×16、厚 3 的板
                box(f, 0, 13, 0, 16, 16, 16, box, false);
                break;

            case BUTTON:
                // 贴在侧面的一块 6×4、厚 2
                box(f, 5, 6, 14, 11, 10, 16, box, false);
                break;

            case PRESSURE_PLATE:
                box(f, 1, 0, 1, 15, 1, 15, box, false);
                break;

            case CARPET:
                box(f, 0, 0, 0, 16, 1, 16, box, false);
                break;

            case SNOW:
                box(f, 0, 0, 0, 16, 2, 16, box, false);
                break;

            case CHEST:
                // 箱体 + 盖子，正面朝南（贴图里 front 给的是 south）
                box(f, 1, 0, 1, 15, 14, 15, box, false);
                box(f, 1, 14, 1, 15, 15, 15, box, false);
                break;

            case SHULKER:
                box(f, 1, 0, 1, 15, 14, 15, box, false);
                box(f, 2, 14, 2, 14, 16, 14, box, false);
                break;

            case SIGN:
                // 立柱 + 一块 14×6 的牌面
                box(f, 7, 0, 7, 9, 10, 9, box, false);
                box(f, 1, 10, 7, 15, 16, 9, box, false);
                break;

            case CANDLE:
                // 蜡烛贴图里实体只有中间一条，按内容区域 1:1 铺，不然会只剩一根看不见的白线
                // 盒子宽高比要和贴图内容区域一致（2×11），否则会把蜡烛拉成一块砖
                BlockModel.addBoxUv(f, 7, 0, 7, 9, 11, 9, box, 0, 5, 2, 11);
                break;

            case LANTERN:
                box(f, 4, 0, 4, 12, 8, 12, box, false);
                box(f, 5, 8, 5, 11, 10, 11, box, false);
                break;

            case CAMPFIRE:
                // 一堆木柴压扁成 16×7 的垛子
                box(f, 0, 0, 0, 16, 7, 16, box, false);
                break;

            case ANVIL:
                // 底 12×4、腰 8×6、顶 10×6，和游戏里的三段一样
                box(f, 2, 0, 2, 14, 4, 14, box, false);
                box(f, 4, 4, 5, 12, 10, 11, box, false);
                box(f, 3, 10, 4, 13, 16, 12, box, false);
                break;

            case HOPPER:
                // 顶口 + 收窄的斗身 + 下方的管子
                box(f, 0, 10, 0, 16, 16, 16, box, false);
                box(f, 2, 8, 2, 14, 10, 14, box, false);
                box(f, 4, 4, 4, 12, 8, 12, box, false);
                box(f, 6, 0, 6, 10, 4, 10, box, false);
                break;

            case CAULDRON:
                // 锅底 + 四面 2 厚的锅壁，中间是空的
                box(f, 0, 0, 0, 16, 3, 16, box, false);
                box(f, 0, 0, 0, 2, 16, 16, box, false);
                box(f, 14, 0, 0, 16, 16, 16, box, false);
                box(f, 2, 0, 0, 14, 16, 2, box, false);
                box(f, 2, 0, 14, 14, 16, 16, box, false);
                break;

            case CAKE:
                // 蛋糕只有 8 高，四周留 1 格边
                box(f, 1, 0, 1, 15, 8, 15, box, false);
                break;

            case FLOWER_POT:
                box(f, 5, 0, 5, 11, 6, 11, box, false);
                break;

            case CROSS:
                // 花、作物、珊瑚扇这类：两片交叉的薄板，原版就是这样画的
                box(f, 0, 0, 7.5f, 16, 16, 8.5f, box, false);
                box(f, 7.5f, 0, 0, 8.5f, 16, 16, box, false);
                break;

            case BED:
                // 床面高 9/16，四周留一点边
                box(f, 0, 0, 0, 16, 9, 16, box, false);
                break;

            case BELL:
                // 钟体 + 顶上的挂钩，整体挂在方块上部
                box(f, 5, 4, 5, 11, 14, 11, box, false);
                box(f, 4, 2, 4, 12, 6, 12, box, false);
                box(f, 7, 14, 7, 9, 16, 9, box, false);
                break;

            case BREWING:
                box(f, 0, 0, 0, 16, 2, 16, box, false);
                box(f, 6, 2, 6, 10, 12, 10, box, false);
                box(f, 0, 12, 0, 16, 15, 16, box, false);
                break;

            case POT:
                // 陶罐/花盆本身就是个圆罐子，用略小一圈的方块近似
                box(f, 2, 0, 2, 14, 16, 14, box, false);
                break;

            case ROD:
                // 末地烛、避雷针、锁链都是细柱
                box(f, 6, 0, 6, 10, 16, 10, box, false);
                break;

            case FRAME:
                // 展示框：贴在墙上的一块薄板
                box(f, 0, 0, 0, 16, 16, 2, box, false);
                break;

            case BEACON:
                // 底座 + 中间的玻璃方块 + 顶盖
                box(f, 3, 0, 3, 13, 4, 13, box, false);
                box(f, 5, 4, 5, 11, 11, 11, box, false);
                box(f, 4, 11, 4, 12, 14, 12, box, false);
                break;

            case PORTAL_FRAME:
                box(f, 0, 0, 0, 16, 13, 16, box, false);
                break;

            case BANNER:
                // 挂在墙上的旗面
                box(f, 0, 0, 0, 16, 16, 2, box, false);
                break;

            case LADDER:
                // 贴墙的一层薄板
                box(f, 0, 0, 0, 16, 16, 2, box, false);
                break;

            case TORCH:
                // 火把贴图里实体只有 x 7..9、y 6..16 那一小条，按这个区域 1:1 铺，
                // 否则整张贴图压到细柱上会只剩一条看不见的线
                BlockModel.addBoxUv(f, 7, 0, 7, 9, 10, 9, box, 7, 6, 2, 10);
                break;

            case COMPOSTER:
                // 桶底 + 四面桶壁
                box(f, 0, 0, 0, 16, 2, 16, box, false);
                box(f, 0, 0, 0, 2, 16, 16, box, false);
                box(f, 14, 0, 0, 16, 16, 16, box, false);
                box(f, 2, 0, 0, 14, 16, 2, box, false);
                box(f, 2, 0, 14, 14, 16, 16, box, false);
                break;

            case LECTERN:
                // 底座 + 立柱 + 斜放的板（这里压平，图标看上去仍是讲台）
                box(f, 0, 0, 0, 16, 2, 16, box, false);
                box(f, 2, 2, 2, 14, 11, 14, box, false);
                box(f, 0, 11, 1, 16, 14, 12, box, false);
                break;

            case SCAFFOLDING:
                // 四根角柱 + 顶板
                box(f, 0, 0, 0, 2, 16, 2, box, false);
                box(f, 14, 0, 0, 16, 16, 2, box, false);
                box(f, 0, 0, 14, 2, 16, 16, box, false);
                box(f, 14, 0, 14, 16, 16, 16, box, false);
                box(f, 0, 13, 0, 16, 16, 16, box, false);
                break;

            default:
                return null;
        }
        if (f.isEmpty()) return null;
        return BlockModel.of(blockId, 16f, 16f, f);
    }

    private static void box(List<BlockModel.Face> out, float x0, float y0, float z0,
                            float x1, float y1, float z1, String[] tex, boolean clipUv) {
        BlockModel.addBox(out, x0, y0, z0, x1, y1, z1, tex, clipUv);
    }
}
