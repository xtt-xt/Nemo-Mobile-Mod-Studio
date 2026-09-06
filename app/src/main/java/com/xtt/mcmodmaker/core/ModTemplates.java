package com.xtt.mcmodmaker.core;

import java.util.UUID;

/** 网易基岩版模组文件模板生成器。 */
public final class ModTemplates {

    private ModTemplates() {
    }

    /** 生成脚本 modMain.py 模板。 */
    public static String scriptMain(String folderName) {
        return "# -*- coding: utf-8 -*-\n\n"
                + "from mod.common.mod import Mod\n\n\n"
                + "@Mod.Binding(name=\"" + folderName + "\", version=\"0.0.1\")\n"
                + "class " + folderName + "(object):\n\n"
                + "    def __init__(self):\n"
                + "        pass\n\n"
                + "    @Mod.InitServer()\n"
                + "    def " + folderName + "ServerInit(self):\n"
                + "        pass\n\n"
                + "    @Mod.DestroyServer()\n"
                + "    def " + folderName + "ServerDestroy(self):\n"
                + "        pass\n\n"
                + "    @Mod.InitClient()\n"
                + "    def " + folderName + "ClientInit(self):\n"
                + "        pass\n\n"
                + "    @Mod.DestroyClient()\n"
                + "    def " + folderName + "ClientDestroy(self):\n"
                + "        pass\n";
    }

    /** 生成行为包或资源包 manifest.json（type: data/resources）。 */
    public static String packManifest(String type) {
        return "{\n"
                + "    \"format_version\": 1,\n"
                + "    \"header\": {\n"
                + "        \"min_engine_version\": [1, 18, 0],\n"
                + "        \"uuid\": \"" + UUID.randomUUID() + "\",\n"
                + "        \"version\": [0, 0, 1]\n"
                + "    },\n"
                + "    \"modules\": [{\n"
                + "        \"type\": \"" + type + "\",\n"
                + "        \"uuid\": \"" + UUID.randomUUID() + "\",\n"
                + "        \"version\": [0, 0, 1]\n"
                + "    }]\n"
                + "}";
    }

    /** 生成 studio.json。 */
    public static String studioJson(String id, String name, String namespace) {
        return "{\n"
                + "  \"id\": \"" + id + "\",\n"
                + "  \"name\": \"" + name + "\",\n"
                + "  \"namespace\": \"" + namespace + "\"\n"
                + "}";
    }

    /** 生成配方模板 JSON。 */
    public static String recipeTemplate(String recipeType, String namespace, String name) {
        String identifier = "\"identifier\": \"" + namespace + ":" + name + "\"";
        switch (recipeType) {
            case "recipe_shaped":
                return "{\n  \"format_version\": \"1.20.10\",\n  \"minecraft:recipe_shaped\": {\n"
                        + "    \"description\": {" + identifier + "},\n"
                        + "    \"key\": {},\n    \"pattern\": [],\n"
                        + "    \"result\": {\"count\": 1, \"item\": \"minecraft:apple\"},\n"
                        + "    \"tags\": [\"crafting_table\"],\n"
                        + "    \"unlock\": {\"context\": \"AlwaysUnlocked\"}\n  }\n}";
            case "recipe_shapeless":
                return "{\n  \"format_version\": \"1.20.10\",\n  \"minecraft:recipe_shapeless\": {\n"
                        + "    \"description\": {" + identifier + "},\n"
                        + "    \"ingredients\": [],\n"
                        + "    \"result\": {\"count\": 1, \"item\": \"minecraft:apple\"},\n"
                        + "    \"tags\": [\"crafting_table\"],\n"
                        + "    \"unlock\": {\"context\": \"AlwaysUnlocked\"}\n  }\n}";
            case "recipe_furnace":
                return "{\n  \"format_version\": \"1.20.10\",\n  \"minecraft:recipe_furnace\": {\n"
                        + "    \"description\": {" + identifier + "},\n"
                        + "    \"input\": {\"count\": 1, \"item\": \"minecraft:apple\"},\n"
                        + "    \"output\": {\"count\": 1, \"item\": \"minecraft:apple\"},\n"
                        + "    \"tags\": [\"furnace\"],\n"
                        + "    \"unlock\": {\"context\": \"AlwaysUnlocked\"}\n  }\n}";
            case "recipe_brewing_mix":
                return "{\n  \"format_version\": \"1.20.10\",\n  \"minecraft:recipe_brewing_mix\": {\n"
                        + "    \"description\": {" + identifier + "},\n"
                        + "    \"input\": \"minecraft:potion:4\",\n"
                        + "    \"output\": \"minecraft:potion:31\",\n"
                        + "    \"reagent\": \"minecraft:blaze_powder:0\",\n"
                        + "    \"tags\": [\"brewing_stand\"]\n  }\n}";
            case "recipe_smithing_transform":
                return "{\n  \"format_version\": \"1.20.10\",\n  \"minecraft:recipe_smithing_transform\": {\n"
                        + "    \"description\": {" + identifier + "},\n"
                        + "    \"template\": \"minecraft:netherite_upgrade_smithing_template\",\n"
                        + "    \"base\": \"minecraft:diamond_boots\",\n"
                        + "    \"addition\": \"minecraft:netherite_ingot\",\n"
                        + "    \"result\": \"minecraft:netherite_boots\",\n"
                        + "    \"tags\": [\"smithing_table\"]\n  }\n}";
            default:
                return "";
        }
    }
}