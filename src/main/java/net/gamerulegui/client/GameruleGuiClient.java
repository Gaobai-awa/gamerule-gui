package net.gamerulegui.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.gamerulegui.GameruleGuiMod;
import net.gamerulegui.screen.GameruleScreen;

import java.util.HashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class GameruleGuiClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(GameruleGuiMod.OPEN_GUI, (client, handler, buf, sender) -> {
            client.execute(() -> {
                if (client.player != null)
                    client.setScreen(new GameruleScreen());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(GameruleGuiMod.SYNC_RULES, (client, handler, buf, sender) -> {
            int size = buf.readVarInt();
            Map<String, String> rules = new HashMap<>(size);
            for (int i = 0; i < size; i++)
                rules.put(buf.readString(), buf.readString());
            Map<String, String> fr = rules;
            client.execute(() -> {
                if (client.currentScreen instanceof GameruleScreen s)
                    s.updateRules(fr);
            });
        });
    }
}
