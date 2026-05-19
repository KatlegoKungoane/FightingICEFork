package katai.mcts.minimax_mcts;

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
    boolean rootPlayerNumber;
    ArrayList<MCTSNode> children;
    int visits;
    int depth;
    double totalReward;
    Action[] actionSequence;
    ArrayDeque<Action> p1Actions;
    ArrayDeque<Action> p2Actions;
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
            boolean rootPlayerNumber,
            Action action,
            Weights weights) {
        this.rootFrameData = frameData;
        this.parent = parent;
        this.rootPlayerNumber = rootPlayerNumber;
        this.children = new ArrayList<>();
        this.visits = 0;
        this.totalReward = 0;
        this.depth = this.parent != null ? this.parent.depth + 1 : 0;
        this.actionSequence = this.parent == null
                ? new Action[0]
                : Arrays.copyOf(this.parent.actionSequence, depth);

        this.playerNumber = actionSequence.length % 2 == 0
                ? this.rootPlayerNumber
                : !this.rootPlayerNumber;

        if (this.parent != null) {
            this.actionSequence[depth - 1] = action;
        }

        this.p1Actions = new ArrayDeque<>();
        this.p2Actions = new ArrayDeque<>();

        this.weights = weights;
    }

    public double ucb1() {
        // double c = Math.sqrt(2) / 8 .0;
        double c = 3;

        if (this.visits == 0) {
            return Double.POSITIVE_INFINITY;
        }

        // double sign = this.playerNumber == this.rootPlayerNumber
        // ? 1
        // : -1;

        return -(this.totalReward / this.visits)
                + c * Math.sqrt(Math.log(this.parent.visits) / this.visits);
    }

    public double rollout(
            int maxDepth,
            Simulator simulator,
            Common playerOneCommon,
            Common playerTwoCommon,
            Motion[] playerOneMotionList,
            Motion[] playerTwoMotionList) {

        FilteredActionList leadingActionList;
        FilteredActionList followerActionList;

        this.p1Actions.clear();
        this.p2Actions.clear();

        // TODO: Not the smartest rollout, since we are just rolling out x actions over
        // 1 second. Could need more though, who knows.
        // TODO: Might be worth simulating one action at a time, because long horizon
        // actions kind of make your next action useless.
        // TODO: Imagine you do an air move, after that move is done, you cant do ground
        // actions, hence you waste time there.
        ArrayDeque<Action> leadingActions;
        ArrayDeque<Action> followerActions;

        if (this.rootPlayerNumber) {
            leadingActions = this.p1Actions;
            followerActions = this.p2Actions;

            leadingActionList = playerOneCommon.getFilteredActions(this.rootFrameData);
            followerActionList = playerTwoCommon.getFilteredActions(this.rootFrameData);
        } else {
            leadingActions = this.p2Actions;
            followerActions = this.p1Actions;

            leadingActionList = playerTwoCommon.getFilteredActions(this.rootFrameData);
            followerActionList = playerOneCommon.getFilteredActions(this.rootFrameData);
        }

        for (int actionIndex = 0; actionIndex < this.actionSequence.length + maxDepth; actionIndex++) {
            if (actionIndex % 2 == 0) {
                leadingActions.add(
                        actionIndex < this.actionSequence.length
                                ? this.actionSequence[actionIndex]
                                : leadingActionList.actionList[LaunchSetting.rng
                                        .nextInt(leadingActionList.maxIndex)]);
            } else {
                followerActions.add(
                        actionIndex < this.actionSequence.length
                                ? this.actionSequence[actionIndex]
                                : followerActionList.actionList[LaunchSetting.rng
                                        .nextInt(followerActionList.maxIndex)]);
            }
        }

        FrameData currentState = simulator.simulate(
                this.rootFrameData,
                this.rootPlayerNumber,
                this.p1Actions,
                this.p2Actions,
                90);

        return getReward(currentState);
    }

    // NB: Reward is from the POV of the rootPlayerNumber
    // Such that we can we can be consistent with how the rewards move and all
    private double getReward(FrameData currentState) {
        // r = win + hp_reward + distance_reward + time_reward
        // double maxReward = this.weights.distance
        // + Math.max(this.weights.hitPoints.playerOneWeights,
        // this.weights.hitPoints.playerTwoWeights);
        // Added 50 for down punishment
        double maxReward = 450;

        CharacterData playerOneCharacterData = currentState.getCharacter(true);
        CharacterData playerTwoCharacterData = currentState.getCharacter(false);

        boolean endEarly = false;
        double earlyReward = 0;

        // Maximize reward if killing move
        if (playerOneCharacterData.getHp() <= 0) {
            endEarly = true;
            earlyReward += this.rootPlayerNumber
                    ? maxReward * -1
                    : maxReward;
        }

        if (playerTwoCharacterData.getHp() <= 0) {
            endEarly = true;
            earlyReward += this.rootPlayerNumber
                    ? maxReward
                    : maxReward * -1;
        }

        if (endEarly) {
            return earlyReward;
        }

        return calculateHpReward(playerOneCharacterData, playerTwoCharacterData)
                + calculateDistanceReward(playerOneCharacterData, playerTwoCharacterData)
                + downPunishment(playerOneCharacterData, playerTwoCharacterData);
    }

    private double downPunishment(
            CharacterData playerOneCharacterData,
            CharacterData playerTwoCharacterData) {
        double downMaxReward = 50;
        double downReward = 0;

        if (playerOneCharacterData.getState() == State.DOWN) {
            downReward += this.rootPlayerNumber
                    ? -downMaxReward
                    : downMaxReward;
        }

        if (playerTwoCharacterData.getState() == State.DOWN) {
            downReward += this.rootPlayerNumber
                    ? downMaxReward
                    : -downMaxReward;
        }

        return downReward;
    }

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

        if (this.rootPlayerNumber) {
            return this.weights.hitPoints.playerOneWeights * playerOneDiff
                    - this.weights.hitPoints.playerTwoWeights * playerTwoDiff;
        }

        return this.weights.hitPoints.playerTwoWeights * playerTwoDiff
                - this.weights.hitPoints.playerOneWeights * playerOneDiff;
    }

    public void backPropagation(double value) {
        MCTSNode currentNode = this;

        while (currentNode != null) {
            currentNode.visits += 1;

            // We could simplify logic, but imma do it this way for readability
            if (currentNode.playerNumber == this.rootPlayerNumber) {
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
