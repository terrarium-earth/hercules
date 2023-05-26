package earth.terrarium.heracles.api.tasks.client.defaults;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teamresourceful.resourcefullib.client.scissor.ScissorBoxStack;
import com.teamresourceful.resourcefullib.client.screens.CursorScreen;
import com.teamresourceful.resourcefullib.client.utils.CursorUtils;
import earth.terrarium.heracles.api.client.DisplayWidget;
import earth.terrarium.heracles.api.client.WidgetUtils;
import earth.terrarium.heracles.api.tasks.QuestTaskDisplayFormatter;
import earth.terrarium.heracles.api.tasks.client.display.TaskTitleFormatter;
import earth.terrarium.heracles.api.tasks.defaults.AdvancementTask;
import earth.terrarium.heracles.common.constants.ConstantComponents;
import earth.terrarium.heracles.common.handlers.progress.TaskProgress;
import net.minecraft.Optionull;
import net.minecraft.advancements.AdvancementList;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.nbt.ByteTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AdvancementTaskWidget implements DisplayWidget {

    private static final String DESC_SINGULAR = "task.heracles.advancement.desc.singular";
    private static final String DESC_PLURAL = "task.heracles.advancement.desc.plural";

    private final AdvancementTask task;
    private final TaskProgress<ByteTag> progress;
    private final Component title;
    private final List<ItemStack> icons;
    private final List<Component> titles;

    private boolean isOpened = false;

    public AdvancementTaskWidget(
        AdvancementTask task, TaskProgress<ByteTag> progress,
        Component title,
        List<ItemStack> icons, List<Component> titles
    ) {
        this.task = task;
        this.progress = progress;
        this.title = title;
        this.icons = icons;
        this.titles = titles;
    }

    public AdvancementTaskWidget(AdvancementTask task, TaskProgress<ByteTag> progress) {
        this(task, progress, TaskTitleFormatter.create(task), getAdvancementIcons(task), getAdvancementTitles(task));
    }

    @Override
    public void render(PoseStack pose, ScissorBoxStack scissor, int x, int y, int width, int mouseX, int mouseY, boolean hovered, float partialTicks) {
        int height = getHeight(width);
        int actualY = y;

        Font font = Minecraft.getInstance().font;
        Gui.fill(pose, x, y, x + width, y + height, 0x80808080);
        Gui.renderOutline(pose, x, y, width, height, 0xFF909090);

        int iconSize = (int) (width * 0.1f);
        Minecraft.getInstance().getItemRenderer().renderGuiItem(pose, getCurrentItem(), x + 5 + (int) (iconSize / 2f) - 8, y + 5 + (int) (iconSize / 2f) - 8);
        String desc = this.task.advancements().size() == 1 ? DESC_SINGULAR : DESC_PLURAL;
        Object text = this.task.advancements().size() == 1 ? this.titles.isEmpty() ? "" : this.titles.get(0) : isOpened ? "▼" : "▶";
        font.draw(pose, this.title, x + iconSize + 10, y + 5, 0xFFFFFFFF);
        font.draw(pose, Component.translatable(desc, text), x + iconSize + 10, y + 7 + font.lineHeight, 0xFF808080);
        String progress = QuestTaskDisplayFormatter.create(this.task, this.progress);
        font.draw(pose, progress, x + width - 5 - font.width(progress), y + 5, 0xFFFFFFFF);

        if (titles.size() > 1 && hovered && mouseY - y >= 7 + font.lineHeight && mouseY - y <= 7 + font.lineHeight * 2 && mouseX - x > (int) (width * 0.1f) && mouseX - x <= width) {
            CursorUtils.setCursor(true, CursorScreen.Cursor.POINTER);
        }

        y += 5 + (font.lineHeight + 2) * 2;

        if (isOpened) {
            for (Component title : titles) {
                font.draw(pose, ConstantComponents.DOT.copy().append(title), x + iconSize + 13, y, 0xFFa0a0a0);
                y += font.lineHeight + 2;
            }
        }

        WidgetUtils.drawProgressBar(pose, x + iconSize + 10, actualY + height - font.lineHeight + 2, x + width - 5, actualY + height - 2, this.task, this.progress);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton, int width) {
        if (mouseY < 0 || mouseY > getHeight(width)) return false;
        if (mouseX < (int) (width * 0.1f) || mouseX > width) return false;
        if (mouseButton != 0) return false;
        if (this.titles.size() <= 1) return false;
        Font font = Minecraft.getInstance().font;
        if (mouseY >= 7 + font.lineHeight && mouseY <= 7 + font.lineHeight * 2) {
            this.isOpened = !this.isOpened;
            return true;
        }
        return false;
    }

    @Override
    public int getHeight(int width) {
        if (isOpened) {
            return (int) (width * 0.1f) + 10 + (Minecraft.getInstance().font.lineHeight + 2) * (this.titles.size());
        }
        return (int) (width * 0.1f) + 10;
    }

    private ItemStack getCurrentItem() {
        if (this.icons.size() == 0) {
            return Items.KNOWLEDGE_BOOK.getDefaultInstance();
        }
        int index = Math.max(0, (int) ((System.currentTimeMillis() / 1000) % this.icons.size()));
        return this.icons.get(index);
    }

    private static List<Component> getAdvancementTitles(AdvancementTask task) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return List.of();
        AdvancementList list = connection.getAdvancements().getAdvancements();
        List<Component> titles = new ArrayList<>();
        for (ResourceLocation id : task.advancements()) {
            titles.add(
                Optionull.mapOrDefault(
                    list.get(id),
                    advancement -> Optionull.mapOrDefault(advancement.getDisplay(), DisplayInfo::getTitle, getTranslation(id)),
                    getTranslation(id)
                )
            );
        }
        titles.removeIf(Objects::isNull);
        return titles;
    }

    private static Component getTranslation(ResourceLocation id) {
        return Component.translatableWithFallback(
            "advancements." + id.getPath().replace("/", ".") + ".title",
            id.toString()
        );
    }

    private static List<ItemStack> getAdvancementIcons(AdvancementTask task) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return List.of();
        AdvancementList list = connection.getAdvancements().getAdvancements();
        List<ItemStack> icons = new ArrayList<>();
        for (ResourceLocation id : task.advancements()) {
            icons.add(
                Optionull.map(list.get(id), advancement -> Optionull.map(advancement.getDisplay(), DisplayInfo::getIcon))
            );
        }
        icons.removeIf(Objects::isNull);
        return icons;
    }

}
