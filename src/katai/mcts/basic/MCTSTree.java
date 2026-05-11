package katai.mcts.basic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

import enumerate.Action;
import enumerate.State;
import fighting.Motion;
import katai.mcts.basic.Common.FilteredActionList;
import katai.mcts.basic.MCTSNode.Weights;
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

    public Action getBestAction() {
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

        return this.headNode.children.get(bestActionIndex).resultingAction;
    }

    public void iteration(int maxDepth) {
        MCTSNode currentNode = this.treeTraversal(this.headNode);
        CharacterData currentCharacter = currentNode.state.getCharacter(currentNode.playerNumber);

        // If already visited, add all its kids to the tree and the current node is the
        // first in the tree
        if (currentNode.children.isEmpty() && currentNode.visits != 0
                || (currentNode == this.headNode && this.headNode.visits == 0)) {
            Common common = currentNode.playerNumber
                    ? this.playerOneCommon
                    : this.playerTwoCommon;

            FilteredActionList actionInformation = common.getFilteredActions(currentNode.state);
            for (int actionIndex = 0; actionIndex < actionInformation.maxIndex; actionIndex++) {
                Action action = actionInformation.actionList[actionIndex];

                this.activePlayerActions.clear();
                this.opponentPlayerActions.clear();

                if (MCTSTree.ableAction(
                        currentNode.playerNumber,
                        currentCharacter,
                        action,
                        this.playerOneMotionList,
                        this.playerTwoMotionList)) {

                    currentNode.children.add(new MCTSNode(
                            currentNode.state,
                            currentNode,
                            currentNode.playerNumber,
                            action,
                            currentNode.state.getCharacter(true),
                            currentNode.state.getCharacter(false),
                            this.weightsConfig));
                }
            }

            if (!currentNode.children.isEmpty()) {
                currentNode = currentNode.children.get(ThreadLocalRandom.current().nextInt(currentNode.children.size()));
            }
        }

        double value = currentNode.rollout(
                maxDepth,
                this.simulator,
                this.playerOneCommon,
                this.playerTwoCommon,
                this.playerOneMotionList,
                this.playerTwoMotionList);

        currentNode.backPropagation(value, this.headNode.playerNumber);
    }

    private MCTSNode treeTraversal(MCTSNode node) {
        if (node.children.isEmpty()) {
            // Is a leaf node
            return node;
        } else {
            // Select child node that maximizes UCB
            int bestNodeIndex = -1;
            double bestNodeUcb1 = -1;
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
