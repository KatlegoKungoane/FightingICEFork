package katai.mcts.basic;

import java.util.ArrayDeque;
import java.util.ArrayList;

import fighting.Fighting;
import fighting.Motion;
import katai.mcts.Common;
import katai.mcts.Common.FilteredActionList;
import setting.GameSetting;
import setting.LaunchSetting;
import simulator.Simulator;
import struct.FrameData;
import struct.MotionData;
import struct.CharacterData;

import enumerate.Action;
import enumerate.State;

public class MCTSNode extends Fighting {
    FrameData state;
    MCTSNode parent;
    boolean playerNumber;
    boolean justCreated;
    ArrayList<MCTSNode> children;
    int visits;
    int depth;
    double totalReward;
    Action resultingAction;
    ArrayDeque<Action> activePlayerActions;
    ArrayDeque<Action> opponentPlayerActions;
    Weights weights;

    public static class HitPointsWeights {
        public double agentWeight;
        public double opponentWeight;

        public HitPointsWeights(double playerOneWeight, double playerTwoWeight) {
            this.agentWeight = playerOneWeight;
            this.opponentWeight = playerTwoWeight;
        }
    }

    public static class Weights {
        public HitPointsWeights hitPoints;
        public double time;
        public double distance;

        public Weights(
                HitPointsWeights hitPoints,
                double time,
                double distance) {

            this.hitPoints = hitPoints;
            this.time = time;
            this.distance = distance;
        }
    }

    public MCTSNode(
            FrameData frameData,
            MCTSNode parent,
            boolean playerNumber,
            Action action,
            Weights weights) {
        this.state = frameData;
        this.parent = parent;
        this.resultingAction = action;
        this.playerNumber = playerNumber;
        this.children = new ArrayList<>();
        this.visits = 0;
        this.totalReward = 0;
        this.depth = this.parent != null ? this.parent.depth + 1 : 0;

        this.activePlayerActions = new ArrayDeque<>();
        this.opponentPlayerActions = new ArrayDeque<>();

        this.justCreated = parent != null;

        this.weights = weights;
    }

    public double ucb1() {
        double c = Math.sqrt(2);

        if (this.visits == 0) {
            return Double.POSITIVE_INFINITY;
        }

        // MCTSAgent.writeToBuffer(
        // String.format(
        // "reward: %f%nvisits: %d%np-visits: %d",
        // this.totalReward,
        // this.visits,
        // this.parent != null ? this.parent.visits : -1));

        return (this.totalReward / this.visits)
                + c * Math.sqrt(Math.log(this.parent.visits) / this.visits);
    }

    private int getMaxFrames(
            Motion selectedMotionData,
            CharacterData selectedCharacterData) {
        if (selectedMotionData.getCancelAbleFrame() != -1) {
            return selectedMotionData.getCancelAbleFrame();
        }

        if (selectedCharacterData.getState() != State.DOWN && selectedCharacterData.getRemainingFrame() > 0) {
            return selectedCharacterData.getRemainingFrame();
        }

        return selectedMotionData.getFrameNumber();
    }

    public double rollout(
            int maxDepth,
            Simulator simulator,
            Common playerOneCommon,
            Common playerTwoCommon,
            Motion[] playerOneMotionList,
            Motion[] playerTwoMotionList) {

        Motion[] selectedMotionList = this.playerNumber
                ? playerOneMotionList
                : playerTwoMotionList;

        Motion[] opponentMotionList = this.playerNumber
                ? playerTwoMotionList
                : playerOneMotionList;

        // We aren't going to simulate every node on creation, so this is the workaround
        if (this.justCreated) {
            // MCTSAgent.writeToBuffer("This node was just created, going to find opponents
            // move then perform ours");
            this.activePlayerActions.clear();
            this.opponentPlayerActions.clear();

            // Get random opponent action, right now, we are playing against a statue
            Common opponentCommon = this.playerNumber
                    ? playerTwoCommon
                    : playerOneCommon;

            CharacterData opponentCharacter = this.state.getCharacter(!this.playerNumber);
            CharacterData myCharacter = this.state.getCharacter(this.playerNumber);

            FilteredActionList opponentActionInformation = opponentCommon.getFilteredActions(this.state);
            // MCTSAgent.writeToBuffer(String.format("Opponent energy: %d%nOpponent max
            // action index: %d",
            // opponentCharacter.getEnergy(), opponentActionInformation.maxIndex));
            // In theory, opponent can cancel action, but we arent going to be that
            // technical to be honest
            int opponentActionIndex = opponentCharacter.getState() == State.DOWN
                    || opponentActionInformation.maxIndex <= 0
                    || opponentCharacter.getRemainingFrame() > 0
                            ? 0
                            : -1;

            // MCTSAgent.writeToBuffer(String.format("Opp state: %s%nOpp init action index:
            // %d",
            // this.state.getCharacter(!this.playerNumber).getState().name(),
            // opponentActionIndex));

            // TODO: Formalize me, this is sloppy
            int missCount = 0;
            while (opponentActionIndex == -1) {
                opponentActionIndex = LaunchSetting.rng.nextInt(opponentActionInformation.maxIndex);
                // MCTSAgent.writeToBuffer(String.format("Picked random action: %s",
                // opponentActionInformation.actionList[opponentActionIndex].name()));
                if (!MCTSTree.ableAction(
                        !this.playerNumber,
                        this.state.getCharacter(!this.playerNumber),
                        opponentActionInformation.actionList[opponentActionIndex],
                        playerOneMotionList,
                        playerTwoMotionList)) {

                    // MCTSAgent.writeToBuffer(String.format("not possible"));
                    opponentActionIndex = -1;
                }
                missCount++;

                if (missCount >= 1000) {
                    // MCTSAgent.writeToBuffer(String.format("Opp failed to find action, going to
                    // default to neutral"));
                    // MCTSAgent.writeToBuffer(
                    // String.format("Remaining frames!: %d",
                    // opponentCharacter.getRemainingFrame()));
                    System.out.println("Find Opp move MISS");
                    System.out.println(this.state.getCharacter(!this.playerNumber).getState().name());

                    if (opponentActionIndex == -1) {
                        opponentActionIndex = 0;
                    }
                }
            }

            Action opponentAction = opponentActionInformation.actionList[opponentActionIndex];
            // MCTSAgent.writeToBuffer(String.format("Selected opp action: %s",
            // opponentAction.name()));

            this.activePlayerActions.add(this.resultingAction);
            this.opponentPlayerActions.add(opponentAction);

            int maxFrameCount = this.getMaxFrames(selectedMotionList[this.resultingAction.ordinal()], myCharacter);

            // MCTSAgent.writeToBuffer(String.format("Max frame duration of actions: %d",
            // maxFrameCount));

            this.state = simulator.simulate(
                    this.state,
                    this.playerNumber,
                    this.activePlayerActions,
                    this.opponentPlayerActions,
                    maxFrameCount);

            // MCTSAgent.writeToBuffer(String.format("Just simulated, mine and opp, so no
            // need to swap player numbers"));

            this.justCreated = false;
        } else {
            // MCTSAgent.writeToBuffer(String.format("Not just created, going to perform
            // calcs"));
        }

        int currentDepth = 0;
        FrameData currentState = this.state;

        // MCTSAgent.writeToBuffer(
        // String.format("Currently not implementing deep rollout, just doing surface
        // level move then moving on"));
        while (!MCTSNode.isTerminal(currentState) && currentDepth < maxDepth) {
            Common myCommon;
            Common opponentCommon;

            if (this.playerNumber) {
                myCommon = playerOneCommon;
                opponentCommon = playerTwoCommon;
            } else {
                myCommon = playerTwoCommon;
                opponentCommon = playerOneCommon;
            }

            FilteredActionList myActionInformation = myCommon.getFilteredActions(currentState);
            FilteredActionList opponentActionInformation = opponentCommon.getFilteredActions(currentState);

            CharacterData myCharacterData = currentState.getCharacter(this.playerNumber);
            CharacterData opponentCharacterData = currentState.getCharacter(!this.playerNumber);

            int myActionIndex = myCharacterData.getState() == State.DOWN
                    || myActionInformation.maxIndex <= 0
                    || myCharacterData.getRemainingFrame() > 0
                            ? 0
                            : -1;
            int opponentActionIndex = opponentCharacterData.getState() == State.DOWN
                    || opponentCharacterData.getRemainingFrame() > 0
                    || opponentActionInformation.maxIndex <= 0
                            ? 0
                            : -1;

            // TODO, lazy method, need to find fool proof method for making a move,
            // sometimes gets stuck
            int missCount = 0;
            while (myActionIndex == -1 || opponentActionIndex == -1) {
                if (myActionIndex == -1) {
                    myActionIndex = LaunchSetting.rng.nextInt(myActionInformation.maxIndex);
                    if (!MCTSTree.ableAction(
                            this.playerNumber,
                            currentState.getCharacter(this.playerNumber),
                            myActionInformation.actionList[myActionIndex],
                            playerOneMotionList,
                            playerTwoMotionList)) {
                        myActionIndex = -1;
                    }
                }

                if (opponentActionIndex == -1) {
                    opponentActionIndex = LaunchSetting.rng.nextInt(opponentActionInformation.maxIndex);
                    if (!MCTSTree.ableAction(
                            !this.playerNumber,
                            currentState.getCharacter(!this.playerNumber),
                            opponentActionInformation.actionList[opponentActionIndex],
                            playerOneMotionList,
                            playerTwoMotionList)) {
                        opponentActionIndex = -1;
                    }
                }
                missCount++;

                if (missCount >= 1000) {
                    System.out.println("MISS");
                    System.out.println(currentState.getCharacter(this.playerNumber).getState().name());
                    System.out.println(currentState.getCharacter(!this.playerNumber).getState().name());
                    if (myActionIndex == -1) {
                        myActionIndex = 0;
                    }

                    if (opponentActionIndex == -1) {
                        opponentActionIndex = 0;
                    }
                }
            }

            this.activePlayerActions.clear();
            this.opponentPlayerActions.clear();

            Action randomAction = myActionInformation.actionList[myActionIndex];
            Action opponentAction = opponentActionInformation.actionList[opponentActionIndex];
            int maxFrameDuration = this.getMaxFrames(selectedMotionList[randomAction.ordinal()], myCharacterData);

            activePlayerActions.add(randomAction);
            opponentPlayerActions.add(opponentAction);

            currentState = simulator.simulate(
                    currentState,
                    playerNumber,
                    activePlayerActions,
                    opponentPlayerActions,
                    maxFrameDuration);

            currentDepth += 1;
        }

        return getReward(currentState);
    }

    private double getReward(FrameData currentState) {
        // MCTSAgent.writeToBuffer(String.format("Getting reward for state"));
        // r = win + hp_reward + distance_reward + time_reward
        double maxReward = this.weights.distance
                + Math.max(this.weights.hitPoints.agentWeight, this.weights.hitPoints.opponentWeight);

        // MCTSAgent.writeToBuffer(String.format("Max Reward: %f", maxReward));

        CharacterData playerOneCharacterData = currentState.getCharacter(true);
        CharacterData playerTwoCharacterData = currentState.getCharacter(false);

        // Maximize reward if killing move
        if (playerOneCharacterData.getHp() <= 0) {
            // MCTSAgent.writeToBuffer(String.format("Player one lost"));
            return this.playerNumber
                    ? maxReward * -1
                    : maxReward;
        }

        if (playerTwoCharacterData.getHp() <= 0) {
            // MCTSAgent.writeToBuffer(String.format("Player one lost"));
            return this.playerNumber
                    ? maxReward
                    : maxReward * -1;
        }

        return calculateHpReward(playerOneCharacterData, playerTwoCharacterData)
                + calculateDistanceReward(playerOneCharacterData, playerTwoCharacterData);
    }

    // TODO: Broken, for some reason, distance is 0
    private double calculateDistanceReward(
            CharacterData playerOneCharacterData,
            CharacterData playerTwoCharacterData) {

        // MCTSAgent.writeToBuffer(String.format("Character distance: %f",
        // Math.abs((double) (playerOneCharacterData.getCenterX() -
        // playerTwoCharacterData.getCenterX()))
        // / GameSetting.STAGE_WIDTH));

        return this.weights.distance
                * (Math.abs((double) (playerOneCharacterData.getCenterX() - playerTwoCharacterData.getCenterX()))
                        / GameSetting.STAGE_WIDTH);
    }

    private double calculateHpReward(
            CharacterData playerOneCharacterData,
            CharacterData playerTwoCharacterData) {

        CharacterData previousPlayerOneCharacterData = this.parent.state.getCharacter(true);
        CharacterData previousPlayerTwoCharacterData = this.parent.state.getCharacter(false);

        // Normally never reaches max range, we can think about a better solution for
        // this later
        double playerOneDiff = ((double) playerOneCharacterData.getHp() - previousPlayerOneCharacterData.getHp())
                / LaunchSetting.maxHp[0];
        double playerTwoDiff = ((double) playerTwoCharacterData.getHp() - previousPlayerTwoCharacterData.getHp())
                / LaunchSetting.maxHp[1];

        // MCTSAgent.writeToBuffer(String.format("HP DIFF:%nP1: %f%nP2: %f",
        // playerOneDiff, playerTwoDiff));

        if (this.playerNumber) {
            return this.weights.hitPoints.agentWeight * playerOneDiff
                    - this.weights.hitPoints.opponentWeight * playerTwoDiff;
        }

        return this.weights.hitPoints.opponentWeight * playerTwoDiff
                - this.weights.hitPoints.agentWeight * playerOneDiff;
    }

    public void backPropagation(double value, boolean headNodePlayerNumber) {
        // MCTSAgent.writeToBuffer(
        // String.format("Current node backprop: %d (%s)", this.depth,
        // this.resultingAction.name()));
        MCTSNode currentNode = this;

        while (currentNode != null) {
            currentNode.visits += 1;
            // MCTSAgent.writeToBuffer(String.format("Current node: %d",
            // currentNode.depth));
            // MCTSAgent.writeToBuffer(String.format("up visits to: %d",
            // currentNode.visits));

            // We could simplify logic, but imma do it this way for readability
            if (currentNode.playerNumber == headNodePlayerNumber) {
                // MCTSAgent.writeToBuffer(String.format("Positive reward"));
                currentNode.totalReward += value;
            } else {
                // MCTSAgent.writeToBuffer(String.format("Negatve reward"));
                currentNode.totalReward -= value;
            }

            currentNode = currentNode.parent;
            // MCTSAgent.writeToBuffer(String.format("Moving to parent"));
        }
    }

    private static boolean isTerminal(FrameData state) {
        return state.getRemainingFramesNumber() <= 0
                || state.getCharacter(true).getHp() <= 0
                || state.getCharacter(false).getHp() <= 0;
    }

    // Redacted, pretty slow method
    // TODO: See if we can do this better one day
    // public static int[] getRandomActionsFastShuffle() {
    // int[] actionIndices = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
    // 16, 17, 18, 19, 20, 21, 22, 23,
    // 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42,
    // 43, 44, 45, 46, 47, 48, 49,
    // 50, 51, 52, 53, 54, 55 };

    // for (int i = actionIndices.length - 1; i > 0; i--) {
    // int index = LaunchSetting.rng.nextInt(i + 1);

    // // Simple swap
    // int temp = actionIndices[index];
    // actionIndices[index] = actionIndices[i];
    // actionIndices[i] = temp;
    // }
    // return actionIndices;
    // }

    public double getAverageScore() {
        if (this.visits == 0) {
            return 0;
        }

        return this.totalReward / this.visits;
    }
}
