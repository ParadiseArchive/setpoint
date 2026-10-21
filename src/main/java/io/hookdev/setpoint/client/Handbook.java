package io.hookdev.setpoint.client;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.hookdev.setpoint.Setpoint;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The handbook's content, read from assets/setpoint/handbook/en_us.json (written by tools/gen-assets.mjs,
 * recipes included). The screen lays the elements out into pages.
 */
public final class Handbook {
    public sealed interface Element permits Heading, Paragraph, Tip, ItemRow, Craft, Cook {}
    public record Heading(Component text) implements Element {}
    public record Paragraph(Component text) implements Element {}
    public record Tip(Component text) implements Element {}
    public record ItemRow(List<ItemStack> items) implements Element {}
    public record Craft(List<ItemStack> grid, ItemStack result) implements Element {}
    public record Cook(ItemStack input, ItemStack result, double seconds) implements Element {}

    public record Chapter(String id, String title, ItemStack icon, List<Element> content) {}

    private final String title;
    private final List<Chapter> chapters;

    private Handbook(String title, List<Chapter> chapters) {
        this.title = title;
        this.chapters = chapters;
    }

    public String title() { return title; }
    public List<Chapter> chapters() { return chapters; }

    public static Handbook load() {
        Identifier file = Setpoint.id("handbook/en_us.json");
        try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(file)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            List<Chapter> chapters = new ArrayList<>();
            for (JsonElement c : root.getAsJsonArray("chapters")) {
                JsonObject chapter = c.getAsJsonObject();
                List<Element> content = new ArrayList<>();
                for (JsonElement e : chapter.getAsJsonArray("content")) content.add(element(e.getAsJsonObject()));
                chapters.add(new Chapter(chapter.get("id").getAsString(), chapter.get("title").getAsString(),
                        stack(chapter.get("icon").getAsString(), 1), content));
            }
            return new Handbook(root.get("title").getAsString(), chapters);
        } catch (Exception e) {
            Setpoint.LOGGER.error("Could not read the Setpoint handbook", e);
            return new Handbook("Setpoint Handbook", List.of(new Chapter("error", "Missing", ItemStack.EMPTY,
                    List.of(new Paragraph(Component.literal("The handbook could not be loaded."))))));
        }
    }

    private static Element element(JsonObject e) {
        return switch (e.get("type").getAsString()) {
            case "h" -> new Heading(Component.literal(e.get("text").getAsString()));
            case "tip" -> new Tip(styled(e.get("text").getAsString()));
            case "items" -> new ItemRow(stacks(e.getAsJsonArray("items")));
            case "craft" -> new Craft(stacks(e.getAsJsonArray("grid")), stack(e.get("result").getAsString(), e.get("count").getAsInt()));
            case "cook" -> new Cook(stack(e.get("input").getAsString(), 1), stack(e.get("result").getAsString(), 1), e.get("seconds").getAsDouble());
            default -> new Paragraph(styled(e.get("text").getAsString()));
        };
    }

    /** Plain text with **bold** parts. */
    private static Component styled(String text) {
        MutableComponent out = Component.empty();
        String[] parts = text.split("\\*\\*", -1);
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            MutableComponent part = Component.literal(parts[i]);
            out.append(i % 2 == 1 ? part.withStyle(ChatFormatting.BOLD) : part);
        }
        return out;
    }

    private static List<ItemStack> stacks(JsonArray ids) {
        List<ItemStack> list = new ArrayList<>();
        for (JsonElement id : ids) list.add(stack(id.getAsString(), 1));
        return list;
    }

    private static ItemStack stack(String id, int count) {
        if (id.isEmpty()) return ItemStack.EMPTY;
        var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, count);
    }
}
