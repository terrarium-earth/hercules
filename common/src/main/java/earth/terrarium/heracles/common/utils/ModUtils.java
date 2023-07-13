package earth.terrarium.heracles.common.utils;

import com.mojang.serialization.Codec;
import earth.terrarium.heracles.Heracles;
import earth.terrarium.heracles.common.handlers.progress.QuestProgressHandler;
import earth.terrarium.heracles.common.handlers.progress.QuestsProgress;
import earth.terrarium.heracles.common.handlers.quests.QuestHandler;
import earth.terrarium.heracles.common.menus.quest.QuestContent;
import earth.terrarium.heracles.common.menus.quests.QuestsContent;
import earth.terrarium.heracles.common.network.NetworkHandler;
import earth.terrarium.heracles.common.network.packets.screens.OpenQuestScreenPacket;
import earth.terrarium.heracles.common.network.packets.screens.OpenQuestsScreenPacket;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import org.joml.Vector2i;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModUtils {

    public static final Codec<Vector2i> VECTOR2I = Codec.INT
        .listOf()
        .comapFlatMap(
            list -> Util.fixedSize(list, 2).map(listx -> new Vector2i(listx.get(0), listx.get(1))),
            vector3f -> List.of(vector3f.x(), vector3f.y())
        );

    public static <T> List<T> getValue(ResourceKey<? extends Registry<T>> key, TagKey<T> tag) {
        return Heracles.getRegistryAccess().registry(key)
            .map(registry ->
                registry.getTag(tag).map(v ->
                    v.stream().filter(Holder::isBound).map(Holder::value).toList()
                ).orElse(List.of())
            )
            .orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    public static <T, U> U cast(T value) {
        return (U) value;
    }

    public static void openQuest(ServerPlayer player, String group, String id) {
        NetworkHandler.CHANNEL.sendToPlayer(new OpenQuestScreenPacket(
            false,
            new QuestContent(
                id,
                group,
                QuestProgressHandler.getProgress(player.server, player.getUUID()).getProgress(id),
                getQuests(player)
            )
        ), player);
    }

    public static void openEditQuest(ServerPlayer player, String group, String id) {
        NetworkHandler.CHANNEL.sendToPlayer(new OpenQuestScreenPacket(
            true,
            new QuestContent(
                id,
                group,
                QuestProgressHandler.getProgress(player.server, player.getUUID()).getProgress(id),
                getQuests(player)
            )
        ), player);
    }

    public static void openGroup(ServerPlayer player, String group) {
        if (!QuestHandler.groups().contains(group)) {
            player.sendSystemMessage(Component.literal("Not a group " + group));
            return;
        }
        NetworkHandler.CHANNEL.sendToPlayer(new OpenQuestsScreenPacket(
            false,
            new QuestsContent(group, getQuests(player), player.hasPermissions(2))
        ), player);
    }

    public static void editGroup(ServerPlayer player, String group) {
        if (!QuestHandler.groups().contains(group)) {
            player.sendSystemMessage(Component.literal("Not a group " + group));
            player.closeContainer();
            return;
        }
        NetworkHandler.CHANNEL.sendToPlayer(new OpenQuestsScreenPacket(
            true,
            new QuestsContent(group, getQuests(player), player.hasPermissions(2))
        ), player);
    }

    private static Map<String, QuestStatus> getQuests(ServerPlayer player) {
        Map<String, QuestStatus> quests = new HashMap<>();
        QuestsProgress progress = QuestProgressHandler.getProgress(player.server, player.getUUID());
        for (String quest : progress.completableQuests().getQuests(progress)) {
            quests.put(quest, QuestStatus.IN_PROGRESS);
        }
        for (String quest : QuestHandler.quests().keySet()) {
            if (!quests.containsKey(quest)) {
                quests.put(quest, progress.isComplete(quest) ? QuestStatus.COMPLETED : QuestStatus.LOCKED);
            }
        }
        return quests;
    }

    public enum QuestStatus {
        COMPLETED,
        IN_PROGRESS,
        LOCKED
    }
}
