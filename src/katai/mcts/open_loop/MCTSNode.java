package katai.mcts.open_loop;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import fighting.Fighting;
import fighting.Motion;
import katai.mcts.Common;
import katai.mcts.Common.FilteredActionList;
import setting.GameSetting;
import setting.LaunchSetting;
import simulator.Simulator;
import struct.FrameData;
import struct.CharacterData;

import enumerate.Action;
import enumerate.State;

public class MCTSNode extends Fighting {
    FrameData rootFrameData;
    MCTSNode parent;
    boolean playerNumber;
    ArrayList<MCTSNode> children;
    int visits;
    int depth;
    double totalReward;
    Action[] actionSequence;
    ArrayDeque<Action> activePlayerActions;
    ArrayDeque<Action> opponentPlayerActions;
    Weights weights;

    public static class HitPointsWeights {
        public double playerOneWeights;
        public double playerTwoWeights;

        public HitPointsWeights(double playerOneWeight, double playerTwoWeight) {
            this.playerOneWeights = playerOneWeight;
            this.playerTwoWeights = playerTwoWeight;
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
        this.rootFrameData = frameData;
        this.parent = parent;
        this.playerNumber = playerNumber;
        this.children = new ArrayList<>();
        this.visits = 0;
        this.totalReward = 0;
        this.depth = this.parent != null ? this.parent.depth + 1 : 0;
        this.actionSequence = this.parent == null
                ? new Action[0]
                : Arrays.copyOf(this.parent.actionSequence, depth);

        if (this.parent != null) {
            this.actionSequence[depth - 1] = action;
        }

        this.activePlayerActions = new ArrayDeque<>();
        this.opponentPlayerActions = new ArrayDeque<>();

        this.weights = weights;
    }

    public double ucb1() {
        // double c = Math.sqrt(2) / 8.0;
        double c = 3;

        if (this.visits == 0) {
            return Double.POSITIVE_INFINITY;
        }

        return (this.totalReward / this.visits)
                + c * Math.sqrt(Math.log(this.parent.visits) / this.visits);
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

        Common myCommon;
        Common opponentCommon;

        if (this.playerNumber) {
            myCommon = playerOneCommon;
            opponentCommon = playerTwoCommon;
        } else {
            myCommon = playerTwoCommon;
            opponentCommon = playerOneCommon;
        }

        FilteredActionList myActionInformation = myCommon.getFilteredActions(this.rootFrameData);
        FilteredActionList opponentActionInformation = opponentCommon.getFilteredActions(this.rootFrameData);

        CharacterData myCharacterData = this.rootFrameData.getCharacter(this.playerNumber);

        this.activePlayerActions.clear();
        this.opponentPlayerActions.clear();

        // TODO: this isnt the most intelligent method.
        // We are simply going to simulate our x actions without checking the result
        // after each action, and the same with the opponent
        // But its some open loop stuff, so maybe it will work. Who knows
        for (Action action : this.actionSequence) {
            this.activePlayerActions.add(action);
        }

        for (int myActionCount = 0; myActionCount < this.depth + maxDepth; myActionCount++) {
            this.activePlayerActions
                    .add(myActionInformation.actionList[LaunchSetting.rng
                            .nextInt(myActionInformation.maxIndex)]);
        }

        for (int opponentActionCount = 0; opponentActionCount < this.depth + maxDepth; opponentActionCount++) {
            this.opponentPlayerActions
                    .add(opponentActionInformation.actionList[LaunchSetting.rng
                            .nextInt(opponentActionInformation.maxIndex)]);
        }

        FrameData currentState = simulator.simulate(
                this.rootFrameData,
                playerNumber,
                this.activePlayerActions,
                this.opponentPlayerActions,
                60);

        return getReward(currentState);
    }

    private double getReward(FrameData currentState) {
        // r = win + hp_reward + distance_reward + time_reward
        // double maxReward = this.weights.distance
                // + Math.max(this.weights.hitPoints.playerOneWeights, this.weights.hitPoints.playerTwoWeights);
        double maxReward = 400;

        CharacterData playerOneCharacterData = currentState.getCharacter(true);
        CharacterData playerTwoCharacterData = currentState.getCharacter(false);

        // Maximize reward if killing move
        if (playerOneCharacterData.getHp() <= 0) {
            return this.playerNumber
                    ? maxReward * -1
                    : maxReward;
        }

        if (playerTwoCharacterData.getHp() <= 0) {
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

        return this.weights.distance
                * (Math.abs((double) (playerOneCharacterData.getCenterX() - playerTwoCharacterData.getCenterX()))
                        / GameSetting.STAGE_WIDTH);
    }

    private double calculateHpReward(
            CharacterData playerOneCharacterData,
            CharacterData playerTwoCharacterData) {

        CharacterData previousPlayerOneCharacterData = this.rootFrameData.getCharacter(true);
        CharacterData previousPlayerTwoCharacterData = this.rootFrameData.getCharacter(false);

        // Normally never reaches max range, we can think about a better solution for
        // this later
        double playerOneDiff = ((double) playerOneCharacterData.getHp() - previousPlayerOneCharacterData.getHp());
                // / LaunchSetting.maxHp[0];
        double playerTwoDiff = ((double) playerTwoCharacterData.getHp() - previousPlayerTwoCharacterData.getHp());
                // / LaunchSetting.maxHp[1];

        if (this.playerNumber) {
            return this.weights.hitPoints.playerOneWeights * playerOneDiff
                    - this.weights.hitPoints.playerTwoWeights * playerTwoDiff;
        }

        return this.weights.hitPoints.playerTwoWeights * playerTwoDiff
                - this.weights.hitPoints.playerOneWeights * playerOneDiff;
    }

    public void backPropagation(double value, boolean headNodePlayerNumber) {
        MCTSNode currentNode = this;

        while (currentNode != null) {
            currentNode.visits += 1;

            // We could simplify logic, but imma do it this way for readability
            if (currentNode.playerNumber == headNodePlayerNumber) {
                currentNode.totalReward += value;
            } else {
                currentNode.totalReward -= value;
            }

            currentNode = currentNode.parent;
        }
    }

    public double getAverageScore() {
        if (this.visits == 0) {
            return 0;
        }

        return this.totalReward / this.visits;
    }
}
