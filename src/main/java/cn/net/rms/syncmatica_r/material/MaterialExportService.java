package cn.net.rms.syncmatica_r.material;

import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.util.SyncmaticaUtil;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
//#if MC < 12001
import net.minecraft.text.LiteralText;
//#endif
//#if MC >= 12001
//$$ import net.minecraft.text.Text;
//#endif
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class MaterialExportService {

    private static final Logger LOGGER = LogManager.getLogger(MaterialExportService.class);
    private static final DateTimeFormatter FILE_NAME_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private MaterialExportService() {
    }

    public static void exportPlacementToXlsx(final ServerPlacement placement) {
        if (placement == null || placement.getMaterialList() == null) {
            notifyClient("syncmatica_r.gui.label.material.export.error_no_data");
            return;
        }
        final List<SyncmaticaMaterialEntry> entries = new ArrayList<>(placement.getMaterialList().getEntries());
        entries.sort((left, right) -> {
            final int lm = left.getAmountMissing();
            final int rm = right.getAmountMissing();
            if (lm != rm) {
                return Integer.compare(rm, lm);
            }
            final String leftKey = left.getKey() == null ? "" : left.getKey().toString();
            final String rightKey = right.getKey() == null ? "" : right.getKey().toString();
            return leftKey.compareTo(rightKey);
        });
        if (entries.isEmpty()) {
            notifyClient("syncmatica_r.gui.label.material.export.error_no_data");
            return;
        }
        final MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.runDirectory == null) {
            LOGGER.warn("Skipping material export – client run directory unavailable");
            notifyClient("syncmatica_r.gui.label.material.export.error_generic");
            return;
        }
        final Path exportDir = client.runDirectory.toPath().resolve("syncmatica").resolve("exports");
        try {
            Files.createDirectories(exportDir);
        } catch (final IOException e) {
            LOGGER.error("Failed to create Syncmatica_r export directory {}", exportDir, e);
            notifyClient("syncmatica_r.gui.label.material.export.error_generic");
            return;
        }
        final String rawName = placement.getName() == null ? "placement" : placement.getName();
        final String sanitizedName = SyncmaticaUtil.sanitizeFileName(rawName);
        final String timestamp = FILE_NAME_TIME.format(LocalDateTime.now());
        final String fileName = "materials-" + sanitizedName + "-" + timestamp + ".xlsx";
        final Path file = exportDir.resolve(fileName);
        try (OutputStream out = Files.newOutputStream(file)) {
            final XlsxWriter workbook = new XlsxWriter("Materials", 5);
            writeWorkbook(workbook, entries);
            workbook.writeTo(out);
        } catch (final IOException e) {
            LOGGER.error("Failed to write Syncmatica_r material export {}", file, e);
            notifyClient("syncmatica_r.gui.label.material.export.error_generic");
            return;
        }
        notifyClient("syncmatica_r.gui.label.material.export.success", file.toAbsolutePath().toString());
    }

    private static void writeWorkbook(final XlsxWriter workbook, final List<SyncmaticaMaterialEntry> entries) {
        int rowIndex = 0;
        workbook.startRow();
        writeHeaderCell(workbook, 0, "syncmatica_r.gui.label.material.column.material");
        writeHeaderCell(workbook, 1, "syncmatica_r.gui.label.material.column.required");
        writeHeaderCell(workbook, 2, "syncmatica_r.gui.label.material.column.stock");
        writeHeaderCell(workbook, 3, "syncmatica_r.gui.label.material.column.missing");
        writeHeaderCell(workbook, 4, "syncmatica_r.gui.label.material.column.claimed");
        rowIndex++;

        for (final SyncmaticaMaterialEntry entry : entries) {
            workbook.startRow();
            final boolean even = (rowIndex % 2) == 0;
            writeDataRow(workbook, entry, even ? XlsxWriter.STYLE_EVEN : XlsxWriter.STYLE_ODD);
            rowIndex++;
        }
    }

    private static void writeHeaderCell(final XlsxWriter workbook, final int column, final String key) {
        workbook.textCell(column, StringUtils.translate(key), XlsxWriter.STYLE_HEADER);
    }

    private static void writeDataRow(final XlsxWriter workbook,
                                     final SyncmaticaMaterialEntry entry,
                                     final int baseStyle) {
        final boolean finished = entry.isFinished();
        final java.util.List<String> claimers = entry.getClaimers() == null
                ? java.util.Collections.emptyList()
                : entry.getClaimers();
        final boolean claimed = !claimers.isEmpty();

        workbook.textCell(0, resolveDisplayName(entry), baseStyle);
        workbook.numberCell(1, Math.max(0, entry.getAmountRequired()), baseStyle);
        workbook.numberCell(2, Math.max(0, entry.getStockingSupplied()), baseStyle);

        final int missing = Math.max(0, entry.getAmountMissing());
        final int missingStyle;
        if (finished) {
            missingStyle = XlsxWriter.STYLE_FINISHED;
        } else if (claimed) {
            missingStyle = XlsxWriter.STYLE_CLAIMED;
        } else {
            missingStyle = baseStyle;
        }
        workbook.numberCell(3, missing, missingStyle);

        workbook.textCell(4, String.join(", ", claimers), baseStyle);
    }

    private static String resolveDisplayName(final SyncmaticaMaterialEntry entry) {
        if (entry == null || entry.getKey() == null) {
            return "unknown";
        }
        final ItemStack stack = resolveDisplayStack(entry.getKey());
        if (!stack.isEmpty()) {
            return stack.getName().getString();
        }
        return entry.getKey().toString();
    }

    private static ItemStack resolveDisplayStack(final MaterialKey key) {
        if (key == null) {
            return ItemStack.EMPTY;
        }
        //#if MC >= 260100
        //$$ final Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(key.itemId());
        //#else
        final Item item = net.minecraft.util.registry.Registry.ITEM.get(key.itemId());
        //#endif
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }

    private static void notifyClient(final String key, final Object... args) {
        final MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.inGameHud == null) {
            return;
        }
        //#if MC >= 260200
        //$$ final net.minecraft.client.gui.components.ChatComponent chat = client.gui.hud.getChat();
        //#else
        final InGameHud hud = client.inGameHud;
        final ChatHud chat = hud.getChatHud();
        //#endif
        if (chat == null) {
            return;
        }
        final String message = StringUtils.translate(key, args);
        //#if MC >= 12001
        //#if MC >= 260100
        //$$ chat.addClientSystemMessage(Component.literal(message));
        //#else
        //$$ chat.addMessage(Text.literal(message));
        //#endif
        //#else
        chat.addMessage(new LiteralText(message));
        //#endif
    }
}
