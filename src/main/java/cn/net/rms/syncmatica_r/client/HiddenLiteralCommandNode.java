package cn.net.rms.syncmatica_r.client;

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;

/**
 * A literal command node that stays executable but is never offered by command
 * completion. Used for commands whose only entry point is a chat click event and
 * that should not clutter the suggestion list.
 *
 * <p>Completion content comes from each node's {@code listSuggestions} (see
 * {@code CommandDispatcher#getCompletionSuggestions}), so suppressing it here hides
 * the node from every completion surface. Fabric API merges client commands into the
 * vanilla suggestion dispatcher through {@link #createBuilder()}, so the builder must
 * produce hidden nodes as well or the merged copy would leak back into suggestions.</p>
 */
final class HiddenLiteralCommandNode<S> extends LiteralCommandNode<S> {
    HiddenLiteralCommandNode(
            final String literal,
            final Command<S> command,
            final Predicate<S> requirement,
            final CommandNode<S> redirect,
            final RedirectModifier<S> modifier,
            final boolean forks
    ) {
        super(literal, command, requirement, redirect, modifier, forks);
    }

    @Override
    public CompletableFuture<Suggestions> listSuggestions(final CommandContext<S> context, final SuggestionsBuilder builder) {
        return Suggestions.empty();
    }

    @Override
    public HiddenLiteralBuilder<S> createBuilder() {
        final HiddenLiteralBuilder<S> builder = new HiddenLiteralBuilder<>(getLiteral());
        builder.requires(getRequirement());
        builder.forward(getRedirect(), getRedirectModifier(), isFork());
        if (getCommand() != null) {
            builder.executes(getCommand());
        }
        return builder;
    }

    static final class HiddenLiteralBuilder<S> extends LiteralArgumentBuilder<S> {
        HiddenLiteralBuilder(final String literal) {
            super(literal);
        }

        @Override
        public HiddenLiteralCommandNode<S> build() {
            return new HiddenLiteralCommandNode<>(
                    getLiteral(),
                    getCommand(),
                    getRequirement(),
                    getRedirect(),
                    getRedirectModifier(),
                    isFork()
            );
        }
    }
}
