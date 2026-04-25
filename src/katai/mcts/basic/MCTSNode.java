package katai.mcts.basic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Random;

import fighting.Fighting;
import fighting.Motion;
import katai.mcts.basic.Common.FilteredActionList;
import simulator.Simulator;
import struct.FrameData;

import java.util.concurrent.ThreadLocalRandom;

import enumerate.Action;
import enumerate.State;

public class MCTSNode extends Fighting {
    FrameData state;
    MCTSNode parent;
    boolean playerNumber;
    boolean justCreated;
    ArrayList<MCTSNode> children;
    int visits;
    double totalReward;
    Action resultingAction;
    ArrayDeque<Action> activePlayerActions;
    ArrayDeque<Action> opponentPlayerActions;

    public MCTSNode(FrameData frameData, MCTSNode parent, boolean playerNumber, Action action) {
        this.state = frameData;
        this.parent = parent;
        this.resultingAction = action;
        this.playerNumber = playerNumber;
        this.children = new ArrayList<>();
        this.visits = 0;
        this.totalReward = 0;

        this.activePlayerActions = new ArrayDeque<>();
        this.opponentPlayerActions = new ArrayDeque<>();

        this.justCreated = parent != null;

        // System.out.println("Node created");
    }

    public double ucb1() {
        double c = 2;

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

        Motion[] opponentMotionList = this.playerNumber
                ? playerTwoMotionList
                : playerOneMotionList;

        // We aren't going to simulate every node on creation, so this is the workaround
        if (this.justCreated) {
            this.activePlayerActions.clear();
            this.opponentPlayerActions.clear();

            this.activePlayerActions.add(this.resultingAction);

            this.state = simulator.simulate(
                    this.state,
                    !this.playerNumber,
                    this.activePlayerActions,
                    this.opponentPlayerActions,
                    opponentMotionList[this.resultingAction.ordinal()].getFrameNumber());

            this.justCreated = false;
        }

        int currentDepth = 0;
        FrameData currentState = this.state;

        while (!MCTSNode.isTerminal(currentState) && currentDepth < maxDepth) {
            Common myCommon;
            Common opponentCommon;

            if (this.playerNumber) {
                myCommon = playerOneCommon;
                opponentCommon = playerTwoCommon;
            }
            else {
                myCommon = playerTwoCommon;
                opponentCommon = playerOneCommon;
            }

            FilteredActionList myActionInformation = myCommon.getFilteredActions(this.state);
            FilteredActionList opponentActionInformation = opponentCommon.getFilteredActions(this.state);

            int myActionIndex = this.state.getCharacter(this.playerNumber).getState() == State.DOWN
                    || myActionInformation.maxIndex <= 0
                            ? 0
                            : -1;
            int opponentActionIndex = this.state.getCharacter(!this.playerNumber).getState() == State.DOWN
                    || opponentActionInformation.maxIndex <= 0
                            ? 0
                            : -1;

            // TODO, lazy method, need to find fool proof method for making a move,
            // sometimes gets stuck
            int missCount = 0;
            while (myActionIndex == -1 || opponentActionIndex == -1) {
                if (myActionIndex == -1) {
                    myActionIndex = ThreadLocalRandom.current().nextInt(myActionInformation.maxIndex);
                    if (!MCTSTree.ableAction(
                            this.playerNumber,
                            this.state.getCharacter(this.playerNumber),
                            myActionInformation.actionList[myActionIndex],
                            playerOneMotionList,
                            playerTwoMotionList)) {
                        myActionIndex = -1;
                    }
                }

                if (opponentActionIndex == -1) {
                    opponentActionIndex = ThreadLocalRandom.current().nextInt(opponentActionInformation.maxIndex);
                    if (!MCTSTree.ableAction(
                            !this.playerNumber,
                            this.state.getCharacter(this.playerNumber),
                            opponentActionInformation.actionList[opponentActionIndex],
                            playerOneMotionList,
                            playerTwoMotionList)) {
                        opponentActionIndex = -1;
                    }
                }
                missCount++;

                if (missCount >= 1000) {
                    System.out.println("MISS");
                    System.out.println(this.state.getCharacter(this.playerNumber).getState().name());
                    System.out.println(this.state.getCharacter(!this.playerNumber).getState().name());
                    if (myActionIndex == -1) {
                        myActionIndex = Action.STAND.ordinal();
                    }

                    if (opponentActionIndex == -1) {
                        opponentActionIndex = Action.STAND.ordinal();
                    }
                }
            }

            this.activePlayerActions.clear();
            this.opponentPlayerActions.clear();

            Action randomAction = myActionInformation.actionList[myActionIndex];
            Action opponentAction = opponentActionInformation.actionList[opponentActionIndex];
            int maxFrameDuration = selectedMotionList[myActionIndex].getFrameNumber()
                    + opponentMotionList[opponentActionIndex].getFrameNumber();

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

        return ((double) currentState.getCharacter(this.playerNumber).getHp())
                - ((double) currentState.getCharacter(!this.playerNumber).getHp());
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
    // int index = ThreadLocalRandom.current().nextInt(i + 1);

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
