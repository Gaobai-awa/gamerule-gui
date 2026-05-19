package net.gamerulegui;

import com.mojang.brigadier.arguments.BoolArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class GameruleGuiMod implements ModInitializer {
    public static final String MOD_ID = "gamerulegui";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Identifier OPEN_GUI = new Identifier(MOD_ID, "open_gui");
    public static final Identifier SYNC_RULES = new Identifier(MOD_ID, "sync_rules");
    public static final Identifier UPDATE_RULE = new Identifier(MOD_ID, "update_rule");

    private static final Map<UUID, Boolean> SHOW_LOG = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        LOGGER.info("GameruleGUI Mod initialized!");

        ServerPlayNetworking.registerGlobalReceiver(UPDATE_RULE, (server, player, handler, buf, sender) -> {
            String rule = buf.readString();
            String val = buf.readString();
            server.execute(() -> {
                if (player == null) return;
                if (!player.hasPermissionLevel(2)) {
                    player.sendMessage(Text.literal("§c[GameruleGUI] You need operator rights to modify gamerules."), false);
                    return;
                }
                server.getCommandManager().executeWithPrefix(
                    server.getCommandSource(), "gamerule " + rule + " " + val);
                sendSync(player);
                if (showLog(player)) {
                    String c = val.equals("false") ? "§c" : "§a";
                    String name = Text.translatable("gamerule." + rule).getString();
                    player.sendMessage(
                        Text.translatable("text.gamerulegui.changed", name, c + val), false);
                }
            });
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                literal("gamerule-gui")
                    // No permission requirement — anyone can open the GUI.
                    // Non-op players get a chat warning but can still view.
                    .executes(ctx -> {
                        ServerCommandSource src = ctx.getSource();
                        ServerPlayerEntity p = src.getPlayer();
                        if (p == null) {
                            src.sendFeedback(() -> Text.translatable("text.gamerulegui.only_player"), false);
                            return 0;
                        }
                        if (!p.hasPermissionLevel(2))
                            p.sendMessage(Text.literal("§e[GameruleGUI] You don't have operator rights — you can view but cannot modify gamerules."), false);
                        ServerPlayNetworking.send(p, OPEN_GUI, PacketByteBufs.create());
                        sendSync(p);
                        if (showLog(p))
                            src.sendFeedback(() -> Text.translatable("text.gamerulegui.opening"), false);
                        return 1;
                    })
                    .then(literal("showlog")
                        .executes(ctx -> {
                            ServerCommandSource src = ctx.getSource();
                            ServerPlayerEntity p = src.getPlayer();
                            if (p == null) {
                                src.sendFeedback(() -> Text.translatable("text.gamerulegui.only_player"), false);
                                return 0;
                            }
                            if (!p.hasPermissionLevel(2)) {
                                p.sendMessage(Text.literal("§c[GameruleGUI] You need operator rights to change settings."), false);
                                return 0;
                            }
                            // Show current value
                            src.sendFeedback(() -> Text.literal("§a[GameruleGUI] Chat logging is: " + showLog(p)), false);
                            return 1;
                        })
                        .then(argument("v", BoolArgumentType.bool())
                            .executes(ctx -> {
                                boolean sl = BoolArgumentType.getBool(ctx, "v");
                                ServerCommandSource src = ctx.getSource();
                                ServerPlayerEntity p = src.getPlayer();
                                if (p == null) {
                                    src.sendFeedback(() -> Text.translatable("text.gamerulegui.only_player"), false);
                                    return 0;
                                }
                                if (!p.hasPermissionLevel(2)) {
                                    p.sendMessage(Text.literal("§c[GameruleGUI] You need operator rights to change settings."), false);
                                    return 0;
                                }
                                SHOW_LOG.put(p.getUuid(), sl);
                                src.sendFeedback(() -> Text.translatable("text.gamerulegui.showlog_set", sl), false);
                                return 1;
                            }))));
        });
    }

    public static boolean showLog(ServerPlayerEntity p) {
        return SHOW_LOG.getOrDefault(p.getUuid(), true);
    }

    public static void sendSync(ServerPlayerEntity p) {
        var gr = p.getServerWorld().getGameRules();
        Map<String, String> map = new LinkedHashMap<>();
        gr.accept(new GameRules.Visitor() {
            @Override
            public <T extends GameRules.Rule<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
                map.put(key.getName(), gr.get(key).toString());
            }
        });
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(map.size());
        for (var e : map.entrySet()) {
            buf.writeString(e.getKey());
            buf.writeString(e.getValue());
        }
        ServerPlayNetworking.send(p, SYNC_RULES, buf);
    }
}
