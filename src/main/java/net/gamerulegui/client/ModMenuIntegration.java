package net.gamerulegui.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.gamerulegui.screen.GameruleScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Mod Menu integration — opens a config screen with a button
 * to launch the gamerule editor.
 */
@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new ConfigScreen(parent);
    }

    private static class ConfigScreen extends Screen {
        private final Screen parent;

        protected ConfigScreen(Screen parent) {
            super(Text.translatable("text.gamerulegui.title"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            super.init();
            int cx = width / 2;

            addDrawableChild(ButtonWidget.builder(
                Text.literal("§a✦ Open Gamerule Editor"),
                btn -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player != null && client.getNetworkHandler() != null)
                        client.getNetworkHandler().sendCommand("gamerule-gui");
                    client.setScreen(new GameruleScreen());
                }
            ).dimensions(cx - 85, height / 2 - 30, 170, 20).build());

            addDrawableChild(ButtonWidget.builder(
                Text.translatable("text.gamerulegui.close"),
                btn -> close()
            ).dimensions(cx - 40, height / 2 + 10, 80, 20).build());
        }

        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            renderBackground(ctx);
            super.render(ctx, mx, my, delta);
            ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§l").append(Text.translatable("text.gamerulegui.title")),
                width / 2, height / 2 - 60, 0xFFFFFF);
            ctx.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("text.gamerulegui.config_hint"),
                width / 2, height / 2 - 40, 0xAAAAAA);
        }

        @Override
        public void close() {
            MinecraftClient.getInstance().setScreen(parent);
        }
    }
}
