package cn.net.rms.syncmatica_r.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import org.junit.jupiter.api.Test;

final class HiddenLiteralCommandNodeTest {
    private static final String HIDDEN_COMMAND = "syncmatica_r_hidden_test";

    @Test
    void neverSuggestsItself() {
        final HiddenLiteralCommandNode<Object> node = newNode(() -> {
        });

        final CompletableFuture<Suggestions> suggestions =
                node.listSuggestions(null, new SuggestionsBuilder("", "", 0));

        assertTrue(suggestions.join().getList().isEmpty());
    }

    @Test
    void fabricMergeCopyStaysHidden() throws CommandSyntaxException {
        // Fabric merges client commands into the vanilla suggestion dispatcher by
        // createBuilder() + build(); the copy must also suppress suggestions or the
        // command would leak back into completion on every command tree refresh.
        final HiddenLiteralCommandNode<Object> node = newNode(() -> {
        });
        final CommandNode<Object> copy = node.createBuilder()
                .requires(source -> true)
                .executes(context -> 0)
                .build();

        assertInstanceOf(HiddenLiteralCommandNode.class, copy);
        final CompletableFuture<Suggestions> suggestions =
                copy.listSuggestions(null, new SuggestionsBuilder("", "", 0));
        assertTrue(suggestions.join().getList().isEmpty());
    }

    @Test
    void executesWhenParsedFromRoot() throws CommandSyntaxException {
        final AtomicInteger runs = new AtomicInteger();
        final RootCommandNode<Object> root = new RootCommandNode<>();
        root.addChild(newNode(runs::incrementAndGet));
        final CommandDispatcher<Object> dispatcher = new CommandDispatcher<>(root);

        final int result = dispatcher.execute(HIDDEN_COMMAND, new Object());

        assertEquals(1, result);
        assertEquals(1, runs.get());
    }

    @Test
    void rootCompletionOmitsHiddenCommandButKeepsOthers() {
        final RootCommandNode<Object> root = new RootCommandNode<>();
        root.addChild(newNode(() -> {
        }));
        root.addChild(new LiteralCommandNode<>(
                "visible_command",
                context -> 1,
                source -> true,
                null,
                null,
                false
        ));
        final CommandDispatcher<Object> dispatcher = new CommandDispatcher<>(root);

        final List<String> suggestions = dispatcher
                .getCompletionSuggestions(dispatcher.parse("", new Object()))
                .join()
                .getList()
                .stream()
                .map(Suggestion::getText)
                .toList();

        assertTrue(suggestions.contains("visible_command"));
        assertFalse(suggestions.contains(HIDDEN_COMMAND));
    }

    private static HiddenLiteralCommandNode<Object> newNode(final Runnable command) {
        return new HiddenLiteralCommandNode<>(
                HIDDEN_COMMAND,
                context -> {
                    command.run();
                    return 1;
                },
                source -> true,
                null,
                null,
                false
        );
    }
}
