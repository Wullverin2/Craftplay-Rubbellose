package de.craftplay.scratchcards;

import de.craftplay.scratchcards.command.ScratchcardCommand;
import de.craftplay.scratchcards.diagnostic.DiagnosticLogger;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.Collection;

public final class PaperScratchcardCommand implements BasicCommand {
    private final String commandName;
    private final ScratchcardCommand delegate;
    private final DiagnosticLogger diagnosticLogger;

    public PaperScratchcardCommand(String commandName, ScratchcardCommand delegate, DiagnosticLogger diagnosticLogger) {
        this.commandName = commandName;
        this.delegate = delegate;
        this.diagnosticLogger = diagnosticLogger;
    }

    @Override
    public void execute(CommandSourceStack commandSourceStack, String[] args) {
        delegate.execute(commandSourceStack.getSender(), commandName, commandName, args);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack commandSourceStack, String[] args) {
        try {
            return delegate.tabComplete(commandSourceStack.getSender(), commandName, args);
        } catch (Throwable throwable) {
            diagnosticLogger.error("Fehler bei Paper/Brigadier-Suggestions fuer /" + commandName, throwable);
            return java.util.List.of();
        }
    }
}
