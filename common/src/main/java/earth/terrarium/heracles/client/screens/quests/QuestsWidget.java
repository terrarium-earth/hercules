package earth.terrarium.heracles.client.screens.quests;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.datafixers.util.Pair;
import com.mojang.math.Axis;
import com.teamresourceful.resourcefullib.client.CloseablePoseStack;
import com.teamresourceful.resourcefullib.client.utils.RenderUtils;
import earth.terrarium.heracles.Heracles;
import earth.terrarium.heracles.client.handlers.ClientQuests;
import earth.terrarium.heracles.client.screens.MouseMode;
import earth.terrarium.heracles.client.utils.ClientUtils;
import earth.terrarium.heracles.client.utils.MouseClick;
import earth.terrarium.heracles.client.widgets.base.BaseWidget;
import earth.terrarium.heracles.common.network.NetworkHandler;
import earth.terrarium.heracles.common.network.packets.quests.OpenQuestPacket;
import earth.terrarium.heracles.common.utils.ModUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Vector2i;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class QuestsWidget extends BaseWidget {

    private static final Vector2i offset = new Vector2i();

    private static final Vector2i MAX = new Vector2i(500, 500);
    private static final Vector2i MIN = new Vector2i(-500, -500);

    private static final ResourceLocation ARROW = new ResourceLocation(Heracles.MOD_ID, "textures/gui/arrow.png");

    private final List<QuestWidget> widgets = new ArrayList<>();
    private final List<ClientQuests.QuestEntry> entries = new ArrayList<>();

    private final int x;
    private final int y;
    private final int fullWidth;
    private final int selectedWidth;
    private int width;
    private final int height;

    private final Vector2i start = new Vector2i();
    private final Vector2i startOffset = new Vector2i();

    private final SelectQuestHandler selectHandler;

    private final Supplier<MouseMode> mouseMode;
    private final BooleanSupplier inspectorOpened;

    private final String group;

    public QuestsWidget(int x, int y, int width, int selectedWidth, int height, BooleanSupplier inspectorOpened, Supplier<MouseMode> mouseMode, Consumer<ClientQuests.QuestEntry> onSelection) {
        this.x = x;
        this.y = y;
        this.fullWidth = width;
        this.selectedWidth = selectedWidth;
        this.width = width;
        this.height = height;
        this.inspectorOpened = inspectorOpened;
        this.mouseMode = mouseMode;
        this.group = ClientUtils.screen() instanceof QuestsScreen screen ? screen.getMenu().group() : "";
        this.selectHandler = new SelectQuestHandler(this.group, onSelection);
    }

    public void update(List<Pair<ClientQuests.QuestEntry, ModUtils.QuestStatus>> quests) {
        this.widgets.clear();
        this.entries.clear();
        for (Pair<ClientQuests.QuestEntry, ModUtils.QuestStatus> quest : quests) {
            this.widgets.add(new QuestWidget(quest.getFirst(), quest.getSecond()));
            this.entries.add(quest.getFirst());
        }
    }

    public void addQuest(ClientQuests.QuestEntry quest) {
        for (QuestWidget widget : this.widgets) {
            if (widget.id().equals(quest.key())) {
                return;
            }
        }
        this.widgets.add(new QuestWidget(quest, ModUtils.QuestStatus.IN_PROGRESS));
        this.entries.add(quest);
    }

    public void removeQuest(ClientQuests.QuestEntry quest) {
        this.widgets.removeIf(widget -> widget.id().equals(quest.key()));
        this.entries.remove(quest);
        QuestWidget questWidget = this.selectHandler.selectedQuest();
        if (questWidget != null && Objects.equals(this.selectHandler.selectedQuest().id(), quest.key())) {
            this.selectHandler.release();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = this.x;
        int y = this.y;
        this.width = inspectorOpened.getAsBoolean() ? this.selectedWidth : this.fullWidth;

        try (var scissor = RenderUtils.createScissor(Minecraft.getInstance(), graphics, x, y, width, height)) {
            x += this.fullWidth / 2;
            y += this.height / 2;
            RenderSystem.setShaderTexture(0, ARROW);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.enableBlend();

            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder buffer = tesselator.getBuilder();

            final Set<Pair<Vector2i, Vector2i>> lines = new HashSet<>();

            for (ClientQuests.QuestEntry entry : this.entries) {
                var position = entry.value().display().position(this.group);

                int px = x + offset.x() + position.x() + 10;
                int py = y + offset.y() + position.y() + 10;

                boolean isHovered = isMouseOver(mouseX, mouseY) && mouseX >= px - 10 && mouseX <= px - 10 + 24 && mouseY >= py - 10 && mouseY <= py - 10 + 24;

                RenderSystem.setShaderColor(0.9F, 0.9F, 0.9F, isHovered ? 0.45f : 0.25F);

                for (ClientQuests.QuestEntry child : entry.children()) {
                    if (!child.value().display().groups().containsKey(this.group)) continue;
                    var childPosition = child.value().display().position(this.group);

                    if (lines.contains(new Pair<>(position, childPosition))) continue;
                    lines.add(new Pair<>(position, childPosition));

                    int cx = x + offset.x() + childPosition.x() + 10;
                    int cy = y + offset.y() + childPosition.y() + 10;

                    float length = Mth.sqrt(Mth.square(cx - px) + Mth.square(cy - py));

                    try (var pose = new CloseablePoseStack(graphics)) {
                        pose.translate(px, py, 0);
                        pose.mulPose(Axis.ZP.rotation((float) Mth.atan2(cy - py, cx - px)));

                        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                        buffer.vertex(pose.last().pose(), 0, 0, 0).uv(0, 0).endVertex();
                        buffer.vertex(pose.last().pose(), 0, 5, 0).uv(0, 1).endVertex();
                        buffer.vertex(pose.last().pose(), length, 5, 0).uv(length / 3f, 1).endVertex();
                        buffer.vertex(pose.last().pose(), length, 0, 0).uv(length / 3f, 0).endVertex();
                        tesselator.end();
                    }
                }
            }

            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();

            for (QuestWidget widget : this.widgets) {
                widget.render(graphics, scissor.stack(), x + offset.x(), y + offset.y(), mouseX, mouseY, isMouseOver(mouseX, mouseY), partialTick);
                if (mouseMode.get().canSelect() && widget == this.selectHandler.selectedQuest()) {
                    graphics.renderOutline(x + offset.x() + widget.x() - 2, y + offset.y() + widget.y() - 2, 28, 28, 0xFFA8EFF0);
                }
            }
        }

        int width = (int) ((this.width - 10) * (offset.x / 500f));
        int height = (int) ((this.height - 10) * (offset.y / 500f));

        if (offset.x > this.width / 4) {
            graphics.fill(this.x + 1 + width, this.y + this.height - 4, this.x - 6 + this.width, this.y + this.height - 2, 0xFFFFFFFF);
        } else if (offset.x < -this.width / 4) {
            graphics.fill(this.x + 1, this.y + this.height - 4, this.x - 6 + this.width + width, this.y + this.height - 2, 0xFFFFFFFF);
        }

        if (offset.y > this.height / 4) {
            graphics.fill(this.x + this.width - 4, this.y + 1 + height, this.x + this.width - 2, this.y - 6 + this.height, 0xFFFFFFFF);
        } else if (offset.y < -this.height / 4) {
            graphics.fill(this.x + this.width - 4, this.y + 1, this.x + this.width - 2, this.y - 6 + this.height + height, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollAmount) {
        if (Screen.hasShiftDown()) {
            offset.add((int) scrollAmount * 10, 0);
        } else {
            offset.add(0, (int) scrollAmount * 10);
        }
        offset.set(Mth.clamp(offset.x(), MIN.x(), MAX.x()), Mth.clamp(offset.y(), MIN.y(), MAX.y()));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        MouseMode mode = this.mouseMode.get();
        if (isMouseOver(mouseX, mouseY)) {
            for (QuestWidget widget : this.widgets) {
                if (widget.isMouseOver(mouseX - (this.x + (this.fullWidth / 2f) + offset.x()), mouseY - (this.y + (this.height / 2f) + offset.y()))) {
                    if (mode.canSelect()) {
                        this.selectHandler.clickQuest(mode, (int) mouseX, (int) mouseY, widget);
                    } else if (mode.canOpen()) {
                        NetworkHandler.CHANNEL.sendToServer(new OpenQuestPacket(
                            this.group, widget.id(), Minecraft.getInstance().screen instanceof QuestsEditScreen
                        ));
                    }
                    return true;
                }
            }
            this.selectHandler.release();
            start.set((int) mouseX, (int) mouseY);
            startOffset.set(offset.x(), offset.y());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.selectHandler.release();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0) return false;
        MouseMode mode = this.mouseMode.get();
        if (mode.canDrag()) {
            int newX = (int) (mouseX - start.x() + startOffset.x());
            int newY = (int) (mouseY - start.y() + startOffset.y());
            offset.set(Mth.clamp(newX, MIN.x(), MAX.x()), Mth.clamp(newY, MIN.y(), MAX.y()));
        } else if (mode.canDragSelection()) {
            this.selectHandler.onDrag((int) mouseX, (int) mouseY);
        }
        return true;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= this.x && mouseX <= this.x + this.width && mouseY >= this.y && mouseY <= this.y + this.height;
    }

    public SelectQuestHandler selectHandler() {
        return this.selectHandler;
    }

    public MouseClick getLocal(MouseClick click) {
        int localX = (int) (click.x() - (this.x + (this.fullWidth / 2f) + offset.x()));
        int localY = (int) (click.y() - (this.y + (this.height / 2f) + offset.y()));
        return new MouseClick(localX, localY, click.button());
    }

    public String group() {
        return this.group;
    }
}
