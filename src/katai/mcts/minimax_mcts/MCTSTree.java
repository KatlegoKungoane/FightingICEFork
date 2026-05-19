package katai.mcts.minimax_mcts;

import java.util.ArrayDeque;
import java.util.ArrayList;

import enumerate.Action;
import enumerate.State;
import fighting.Motion;
import katai.mcts.Common;
import katai.mcts.Common.FilteredActionList;
import katai.mcts.minimax_mcts.MCTSNode.Weights;
import setting.LaunchSetting;
import simulator.Simulator;
import struct.CharacterData;

public class MCTSTree {
    int maxDepth;
    MCTSNode headNode;
    private Motion[] playerOneMotionList;
    private Motion[] playerTwoMotionList;
    ArrayDeque<Action> activePlayerActions;
    ArrayDeque<Action> opponentPlayerActions;
    private Simulator simulator;
    private Common playerOneCommon;
    private Common playerTwoCommon;
    private Weights weightsConfig;

    public MCTSTree(
            int maxDepth,
            Simulator simulator,
            MCTSNode headNode,
            Common playerOneCommon,
            Common playerTwoCommon,
            ArrayList<Motion> playerOneMotionList,
            ArrayList<Motion> playerTwoMotionList,
            Weights weightsConfig) {
        this.headNode = headNode;
        this.maxDepth = maxDepth;

        this.playerOneMotionList = playerOneMotionList.toArray(new Motion[playerOneMotionList.size()]);
        this.playerTwoMotionList = playerTwoMotionList.toArray(new Motion[playerTwoMotionList.size()]);

        this.activePlayerActions = new ArrayDeque<>();
        this.opponentPlayerActions = new ArrayDeque<>();

        this.simulator = simulator;
        this.playerOneCommon = playerOneCommon;
        this.playerTwoCommon = playerTwoCommon;

        this.weightsConfig = weightsConfig;
    }

    public MCTSNode getBestChildNode() {
        int bestActionIndex = -1;
        int mostVisitedActionValue = -1;

        int counter = 0;
        for (MCTSNode childNode : this.headNode.children) {
            if (bestActionIndex == -1 || childNode.visits > mostVisitedActionValue) {
                bestActionIndex = counter;
                mostVisitedActionValue = childNode.visits;
            }

            counter++;
        }

        if (bestActionIndex == -1) {
            return null;
        }

        return this.headNode.children.get(bestActionIndex);
    }

    public void iteration(int maxDepth) {
        MCTSNode currentNode = this.treeTraversal(this.headNode);
        CharacterData currentCharacter = currentNode.rootFrameData.getCharacter(currentNode.playerNumber);
        Common selectedCommon = currentNode.playerNumber
                ? this.playerOneCommon
                : this.playerTwoCommon;

        // If already visited, add all its kids to the tree and the current node is the
        // first in the tree
        if (currentNode.children.isEmpty() && currentNode.visits >= 20
                && currentNode.depth < 6
                || (currentNode == this.headNode && this.headNode.visits == 0)) {

            // TODO: This does mean that you cant really look that far into the future.
            // Since you dont know what state you are probably in.
            FilteredActionList actionInformation = selectedCommon.getFilteredActions(currentNode.rootFrameData);
            for (int actionIndex = 0; actionIndex < actionInformation.maxIndex; actionIndex++) {
                Action action = actionInformation.actionList[actionIndex];

                if (MCTSTree.ableAction(
                        currentNode.playerNumber,
                        currentCharacter,
                        action,
                        this.playerOneMotionList,
                        this.playerTwoMotionList)) {

                    currentNode.children.add(
                            new MCTSNode(
                                    currentNode.rootFrameData,
                                    currentNode,
                                    currentNode.rootPlayerNumber,
                                    action,
                                    this.weightsConfig));
                }
            }

            if (!currentNode.children.isEmpty()) {
                // TODO: Could look into sampling from the back. Such that you do more energy intensive moves first.
                currentNode = currentNode.children.get(LaunchSetting.rng.nextInt(currentNode.children.size()));
            }
        }

        double value = currentNode.rollout(
                maxDepth,
                this.simulator,
                this.playerOneCommon,
                this.playerTwoCommon,
                this.playerOneMotionList,
                this.playerTwoMotionList);

        currentNode.backPropagation(value);
    }

    private MCTSNode treeTraversal(MCTSNode node) {
        if (node.children.isEmpty()) {
            // Is a leaf node
            return node;
        } else {
            // Select child node that maximizes UCB
            int bestNodeIndex = -1;
            double bestNodeUcb1 = Integer.MIN_VALUE;
            int counter = 0;
            for (MCTSNode childNode : node.children) {
                double childNodeUCB1 = childNode.ucb1();

                if (childNodeUCB1 == Double.POSITIVE_INFINITY) {
                    return childNode;
                }

                if (bestNodeIndex == -1 || childNodeUCB1 > bestNodeUcb1) {
                    bestNodeIndex = counter;
                    bestNodeUcb1 = childNodeUCB1;
                }

                counter++;
            }

            return treeTraversal(node.children.get(bestNodeIndex));
        }
    }

    public static boolean ableAction(
            boolean playerNumber,
            CharacterData character,
            Action nextAction,
            Motion[] playerOneMotionList,
            Motion[] playerTwoMotionList) {

        Motion nextMotion = playerNumber
                ? playerOneMotionList[nextAction.ordinal()]
                : playerTwoMotionList[nextAction.ordinal()];
        Motion nowMotion = playerNumber
                ? playerOneMotionList[character.getAction().ordinal()]
                : playerTwoMotionList[character.getAction().ordinal()];

        if (character.getEnergy() < -nextMotion.getAttackStartAddEnergy()) {
            return false;
        } else if (character.isControl()) {
            return true;
        } else {
            boolean checkFrame = nowMotion.getCancelAbleFrame() <= nowMotion.getFrameNumber()
                    - character.getRemainingFrame();
            boolean checkAction = nowMotion.getCancelAbleMotionLevel() >= nextMotion.getMotionLevel();

            return character.isHitConfirm() && checkFrame && checkAction;
        }
    }

    // TODO: Look into completing at a later stage, error prone atm
    private boolean canDoActionInState(State characterState, Action action) {
        String actionName = action.name();

        if (action == Action.NEUTRAL) {
            return true;
        }

        switch (characterState) {
            case State.AIR:
                return actionName.startsWith("AIR");
            case State.DOWN:
                return false;
            case State.CROUCH:
                return actionName.startsWith("AIR");
            default:
                return !actionName.startsWith("AIR");
        }
    }
}
