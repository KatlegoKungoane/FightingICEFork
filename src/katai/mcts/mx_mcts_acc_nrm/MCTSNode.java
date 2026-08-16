package katai.mcts.mx_mcts_acc_nrm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;

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
    public static class AgentConfig {
        int maxDepth = 15;
        double hitPointsWeight = 0.75;
        double ucbConstant = 0.25;
        int rolloutDuration = 60;
        int childCreationSimulationLimit = 60;
        int maxTreeDepth = 6;
        int minVisitCountBeforeRollout = 20;
        boolean usedReversedActionList = true;
    }

    FrameData rootFrameData;
    MCTSNode parent;
    boolean playerNumber;
    boolean rootPlayerNumber;
    boolean deadKids;
    ArrayList<MCTSNode> children;
    int visits;
    int depth;
    double totalReward;
    Action[] actionSequence;
    public ArrayDeque<Action> p1Actions;
    public ArrayDeque<Action> p2Actions;
    Weights weights;
    State p1State;
    State p2State;
    int p1Energy;
    int p2Energy;
    AgentConfig agentConfig;

    public static class HitPointsWeights {
        public double playerOneWeights;
        public double playerTwoWeights;

        public HitPointsWeights(double playerOneWeight, double playerTwoWeight) {
            this.playerOneWeights = playerOneWeight;
            this.playerTwoWeights = playerTwoWeight;
        }
    }

    public static class Weights {
        public double hitPoints;
        public double time;
        public double distance;
        public double downState;

        public Weights(
                double hitPoints,
                double time,
                double distance,
                double downState) {

            double totalWeights = Math.abs(hitPoints) + Math.abs(time) + Math.abs(distance) + Math.abs(downState);

            this.hitPoints = hitPoints / totalWeights;
            this.time = time / totalWeights;
            this.distance = distance / totalWeights;
            this.downState = downState / totalWeights;
        }
    }

    public MCTSNode(
            FrameData frameData,
            MCTSNode parent,
            boolean rootPlayerNumber,
            Action action,
            Weights weights,
            AgentConfig agentConfig) {
        this.rootFrameData = frameData;
        this.parent = parent;
        this.rootPlayerNumber = rootPlayerNumber;
        this.agentConfig = agentConfig;
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
        this.deadKids = false;

        this.p1State = null;
        this.p2State = null;

        this.p1Energy = -1;
        this.p2Energy = -1;
    }

    public double ucb1() {
        double c = this.agentConfig.ucbConstant;

        if (this.visits == 0) {
            return Double.POSITIVE_INFINITY;
        }

        return -(this.totalReward / this.visits)
                + c * Math.sqrt(Math.log(this.parent.visits) / this.visits);
    }

    public void getActionSequence(Common p1Common, Common p2Common, int maxDepth) {
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

        FilteredActionList leadingActionList;
        FilteredActionList followerActionList;

        if (this.rootPlayerNumber) {
            leadingActions = this.p1Actions;
            followerActions = this.p2Actions;

            leadingActionList = p1Common.getFilteredActions(this.rootFrameData);
            followerActionList = p2Common.getFilteredActions(this.rootFrameData);
        } else {
            leadingActions = this.p2Actions;
            followerActions = this.p1Actions;

            leadingActionList = p2Common.getFilteredActions(this.rootFrameData);
            followerActionList = p1Common.getFilteredActions(this.rootFrameData);
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
    }

    public double rollout(
            int maxDepth,
            Simulator simulator,
            Common playerOneCommon,
            Common playerTwoCommon,
            Motion[] playerOneMotionList,
            Motion[] playerTwoMotionList) {

        this.getActionSequence(playerOneCommon, playerTwoCommon, maxDepth);

        FrameData currentState = simulator.simulate(
                this.rootFrameData,
                this.rootPlayerNumber,
                this.p1Actions,
                this.p2Actions,
                this.agentConfig.rolloutDuration);

        return getReward(currentState);
    }

    // NB: Reward is from the POV of the rootPlayerNumber
    // Such that we can we can be consistent with how the rewards move and all
    private double getReward(FrameData currentState) {
        CharacterData playerOneCharacterData = currentState.getCharacter(true);
        CharacterData playerTwoCharacterData = currentState.getCharacter(false);

        if (playerOneCharacterData.getHp() <= 0) {
            return this.rootPlayerNumber ? 0 : 1;
        }

        if (playerTwoCharacterData.getHp() <= 0) {
            return this.rootPlayerNumber ? 1 : 0;
        }

        // All rewards are normalized between 0 and 1
        double hpReward = calculateHpReward(playerOneCharacterData, playerTwoCharacterData);
        double distanceReward = calculateDistanceReward(playerOneCharacterData, playerTwoCharacterData);
        double downReward = downPunishment(playerOneCharacterData, playerTwoCharacterData);

        hpReward *= this.weights.hitPoints;
        distanceReward *= this.weights.distance;
        downReward *= this.weights.downState;

        if (hpReward < 0) {
            hpReward = 1 + hpReward;
        }

        if (distanceReward < 0) {
            distanceReward = 1 + distanceReward;
        }
        if (downReward < 0) {
            downReward = 1 + downReward;
        }

        return hpReward + distanceReward + downReward;
    }

    // TODO: Maybe look into adding a flag for if we should punish down or not.
    private double downPunishment(
            CharacterData playerOneCharacterData,
            CharacterData playerTwoCharacterData) {
        double downReward = 0;

        if (playerOneCharacterData.getState() == State.DOWN) {
            downReward += this.rootPlayerNumber ? 0 : 1;
        }

        if (playerTwoCharacterData.getState() == State.DOWN) {
            downReward += this.rootPlayerNumber ? 1 : 0;
        }

        return downReward;
    }

    private double calculateDistanceReward(
            CharacterData playerOneCharacterData,
            CharacterData playerTwoCharacterData) {

        return (Math.abs((double) (playerOneCharacterData.getCenterX() - playerTwoCharacterData.getCenterX()))
                / GameSetting.STAGE_WIDTH);
    }

    private double calculateHpReward(
            CharacterData playerOneCharacterData,
            CharacterData playerTwoCharacterData) {

        CharacterData previousPlayerOneCharacterData = this.rootFrameData.getCharacter(true);
        CharacterData previousPlayerTwoCharacterData = this.rootFrameData.getCharacter(false);

        double playerOneDiff = ((double) playerOneCharacterData.getHp() - previousPlayerOneCharacterData.getHp());
        double playerTwoDiff = ((double) playerTwoCharacterData.getHp() - previousPlayerTwoCharacterData.getHp());

        playerOneDiff /= 150;
        playerTwoDiff /= 150;

        double hpReward = this.rootPlayerNumber
                ? playerOneDiff - playerTwoDiff
                : playerTwoDiff - playerOneDiff;

        /**
         * Your attacks can never realistically give you hp
         * Lets look from player 1's perspective
         * We first get the hp lost
         * Then we apply the player one weight.
         * Then we normalize between [-1,1]
         * Worse case, we got hit as hard as we possible could have, so thats -1.
         * Best case, we hit them as hard as possible, thats +1
         * Meaning hpReward is between the range -1 and 1.
         * So, to normalize between 0 and 1, we would need to (x+1)/2
         */

        return (hpReward + 1.0) / 2.0;

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
