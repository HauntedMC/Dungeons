package nl.hauntedmc.dungeons.command;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import nl.hauntedmc.dungeons.repository.RepoService;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

class RepoCommandHandlerTest {
    @Test
    void diffRetainsTheMapAndValidatesTheRequestedPage() {
        try (var paper = new PaperBoundary()) {
            var repo = mock(RepoService.class);
            var actor = mock(CommandSender.class);
            paper.runtime.setContentRepository(repo);
            when(repo.status("crypt", actor, true, 1))
                    .thenReturn(CompletableFuture.completedFuture(List.of()));
            when(repo.status("crypt", actor, true, 2))
                    .thenReturn(CompletableFuture.completedFuture(List.of()));
            var commands = new RepoCommandHandler(paper.runtime);
            commands.handleCommand(actor, new String[] {"diff", "crypt"});
            commands.handleCommand(actor, new String[] {"diff", "crypt", "2"});
            for (String invalid : List.of("0", "-1", "100001", "two")) {
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                commands.handleCommand(
                                        actor, new String[] {"diff", "crypt", invalid}));
            }
            verify(repo).status("crypt", actor, true, 1);
            verify(repo).status("crypt", actor, true, 2);
            verifyNoMoreInteractions(repo);
        }
    }
}
