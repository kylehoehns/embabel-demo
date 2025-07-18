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
import com.embabel.agent.domain.io.SystemOutput;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.event.AgentProcessStuckEvent;
import com.embabel.common.ai.model.AutoModelSelectionCriteria;
import com.embabel.common.ai.model.LlmOptions;
import jinjava.org.jsoup.helper.Validate;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Profile;

import java.util.List;

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


@Agent(description = "Generate a baseball lineup ")
public class LineupAgent implements StuckHandler {

  private static final String VALID_LINEUP = "validLineup";
  private static final String CAN_CALL_LLM = "canCallLlm";
  private static final String LLM_CALLS = "llmCalls";


  @Action(
      description = "calls the LLM to generate a potential baseball lineup",
      canRerun = true,
      cost = 100.0,
      pre = {CAN_CALL_LLM},
      post = {VALID_LINEUP}
  )
  PotentialLineup generatePotentialLineup(UserInput userInput, OperationContext context) {

    System.out.println("generatePotentialLineup called");

    int llmCalls = getLlmCalls(context);
    setLlmCalls(context, llmCalls + 1);

    String playerList = userInput.getContent();
    return context.promptRunner()
        .withLlm(LlmOptions.fromCriteria(AutoModelSelectionCriteria.INSTANCE))
        .createObject(String.format("""
                You are given a list of baseball player names. For each name, assign one of the following positions: PITCHER, CATCHER, FIRST_BASE, SECOND_BASE, THIRD_BASE, SHORTSTOP, LEFT_FIELD, CENTER_FIELD, RIGHT_FIELD, and BENCH.
                Here are the player names:
                %s
                """,
            playerList
        ).trim(), PotentialLineup.class);
  }

  @Condition(name = CAN_CALL_LLM)
  @Action
  public boolean canCallLlm(OperationContext context) {
    int llmCalls = getLlmCalls(context);
    return llmCalls <= 3;
  }

  @AchievesGoal(description = "The lineup has been generated successfully")
  @Action(pre = {VALID_LINEUP})
  Lineup completeLineup(PotentialLineup lineup) {
    System.out.println("completeLineup called");
    return new Lineup(lineup.players());
  }

  @Condition(name = VALID_LINEUP)
  @Action
  public boolean isValidLineup(PotentialLineup lineup) {
    return lineup.players().size() == 9;
  }

  @NotNull
  @Override
  public StuckHandlerResult handleStuck(@NotNull AgentProcess process) {
    String message = "Cannot complete lineup: stuck because we called the LLM too many times and we don't want to run out of money";
    return new StuckHandlerResult(
        message,
        this,
        StuckHandlingResultCode.NO_RESOLUTION,
        process
    );
  }

  private int getLlmCalls(OperationContext context) {
    Integer llmCalls = (Integer) context.get(LLM_CALLS);
    return llmCalls == null ? 0 : llmCalls;
  }

  private void setLlmCalls(OperationContext context, int llmCalls) {
    context.set(LLM_CALLS, llmCalls);
  }
}