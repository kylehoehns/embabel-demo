package com.embabel.template.agent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Condition;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.common.StuckHandler;
import com.embabel.agent.api.common.StuckHandlerResult;
import com.embabel.agent.api.common.StuckHandlingResultCode;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.domain.io.UserInput;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Agent(description = "Assign field positions to a list of baseball players to create a complete lineup")
public class LineupAgent implements StuckHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(LineupAgent.class);
    private static final String LLM_CALLS = "llmCalls";
    private static final String VALID_LINEUP = "validLineup";
    private static final String CAN_CALL_LLM = "canCallLlm";


    @AchievesGoal(
        description = "Generates a baseball lineup by assigning field positions to a list of players",
        tags = {
            "baseball",
            "lineup-generation",
            "position-assignment",
            "sports"
        },
        examples = {
            "Create a baseball lineup from these 10 player names: John, Mike, Sarah, Lily, Alex, Ben, Emma, Chris, Jake, Rachel",
            "Assign positions for this youth baseball team: [Tommy, Brian, Jessica, Kevin, Lily, Drew, Sam, Megan, Luke, Abby]",
            "Fill out a complete lineup using these names: Daniel, Ava, Ryan, Zoe, Leo, Grace, Max, Chloe, Jack"
        }
    )
    @Action(
        pre = {VALID_LINEUP}
    )
    Lineup buildLineup(PotentialLineup lineup) {
        LOGGER.info("buildLineup called");
        return new Lineup(lineup.players());
    }

    @Action(
        canRerun = true,
        pre = {CAN_CALL_LLM},
        post = {VALID_LINEUP}
    )
    PotentialLineup generatePotentialLineup(UserInput userInput, OperationContext context) {
        LOGGER.info("generatePotentialLineup called");
        int llmCalls = getLlmCalls(context);
        setLlmCalls(context, llmCalls + 1);

        String playerList = userInput.getContent();

        String positions = Arrays.stream(Position.values())
            .map(Enum::name)
            .collect(Collectors.joining(", "));

        String prompt = String.format("""
            Assign each of the following baseball players to **exactly one** position from this list:
            %s
            
            Ensure no duplicate positions **except for BENCH**, which can be assigned to any extra players beyond nine.
            
            Players:
            %s
            """, positions, playerList).trim();

        return context.promptRunner().createObject(prompt, PotentialLineup.class);
    }

    @Condition(name = CAN_CALL_LLM)
    @Action
    public boolean canCallLlm(OperationContext context) {
        LOGGER.info("canCallLlm called");
        int llmCalls = getLlmCalls(context);
        return llmCalls <= 3;
    }

    @Condition(name = VALID_LINEUP)
    @Action
    public boolean isValidLineup(PotentialLineup lineup) {
        LOGGER.info("isValidLineup called");
        return lineup.players().size() >= 9;
    }

    @Action(post = {CAN_CALL_LLM})
    public void checkLlmEligibility(OperationContext context) {
        // This is here to set world state prior to initial calls
        LOGGER.info("checkLlmEligibility called");
        if (context.get(LLM_CALLS) == null) {
            setLlmCalls(context, 0);
        }
    }

    private int getLlmCalls(OperationContext context) {
        Integer llmCalls = (Integer) context.get(LLM_CALLS);
        return llmCalls == null ? 0 : llmCalls;
    }

    private void setLlmCalls(OperationContext context, int llmCalls) {
        context.set(LLM_CALLS, llmCalls);
    }

    @NotNull
    @Override
    public StuckHandlerResult handleStuck(@NotNull AgentProcess process) {
        String message = "Unable to complete the lineup: too many LLM attempts. We've hit the retry limit to avoid excessive usage.";
        return new StuckHandlerResult(
            message,
            this,
            StuckHandlingResultCode.NO_RESOLUTION,
            process
        );
    }
}

enum Position {
    PITCHER,
    CATCHER,
    FIRST_BASE,
    SECOND_BASE,
    THIRD_BASE,
    SHORTSTOP,
    LEFT_FIELD,
    CENTER_FIELD,
    RIGHT_FIELD,
    BENCH
}

record Player(String name, Position position) {
}

record Lineup(List<Player> players) {
}

record PotentialLineup(List<Player> players) {
}