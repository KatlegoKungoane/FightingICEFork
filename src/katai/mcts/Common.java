package katai.mcts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import enumerate.Action;
import enumerate.State;
import fighting.Motion;
import struct.FrameData;

public class Common {

    public static class ActionCost {
        public final Action action;
        public final int cost;

        public ActionCost(Action action, int cost) {
            this.action = action;
            this.cost = cost;
        }
    }

    public static class FilteredActionList {
        public final Action[] actionList;
        public final int maxIndex;

        public FilteredActionList(Action[] actionList, int maxIndex) {
            this.actionList = actionList;
            this.maxIndex = maxIndex;
        }
    }

    private Action[] airActions = { Action.AIR_GUARD, Action.AIR_A, Action.AIR_B, Action.AIR_DA, Action.AIR_DB,
            Action.AIR_FA, Action.AIR_FB, Action.AIR_UA, Action.AIR_UB, Action.AIR_D_DF_FA,
            Action.AIR_D_DF_FB, Action.AIR_F_D_DFA, Action.AIR_F_D_DFB, Action.AIR_D_DB_BA,
            Action.AIR_D_DB_BB };

    private Action[] groundActions = { Action.STAND_D_DB_BA, Action.BACK_STEP, Action.FORWARD_WALK, Action.DASH,
            Action.JUMP, Action.FOR_JUMP, Action.BACK_JUMP, Action.STAND_GUARD,
            Action.CROUCH_GUARD, Action.THROW_A, Action.THROW_B, Action.STAND_A, Action.STAND_B,
            Action.CROUCH_A, Action.CROUCH_B, Action.STAND_FA, Action.STAND_FB, Action.CROUCH_FA,
            Action.CROUCH_FB, Action.STAND_D_DF_FA, Action.STAND_D_DF_FB, Action.STAND_F_D_DFA,
            Action.STAND_F_D_DFB, Action.STAND_D_DB_BB, Action.STAND_D_DF_FC };

    private Action[] sortedAirActions;
    private Action[] sortedGroundActions;

    private ActionCost[] sortedAirActionCost;
    private ActionCost[] sortedGroundActionCost;

    private boolean playerNumber;

    public Common(ArrayList<Motion> playerMotions, boolean playerNumber) {
        this.sortedAirActionCost = buildSortedArray(airActions, playerMotions);
        this.sortedGroundActionCost = buildSortedArray(groundActions, playerMotions);

        sortedAirActions = new Action[sortedAirActionCost.length];
        sortedGroundActions = new Action[sortedGroundActionCost.length];

        for (int index = 0; index < sortedAirActionCost.length; index++) {
            this.sortedAirActions[index] = this.sortedAirActionCost[index].action;
        }

        for (int index = 0; index < sortedGroundActionCost.length; index++) {
            this.sortedGroundActions[index] = this.sortedGroundActionCost[index].action;
        }

        this.playerNumber = playerNumber;
    }

    private ActionCost[] buildSortedArray(Action[] source, ArrayList<Motion> motions) {
        ActionCost[] pairs = new ActionCost[source.length];
        for (int i = 0; i < source.length; i++) {
            Action act = source[i];
            int cost = Math.abs(motions.get(act.ordinal()).getAttackStartAddEnergy());
            pairs[i] = new ActionCost(act, cost);
        }

        Arrays.sort(pairs, Comparator.comparingInt(a -> a.cost));
        return pairs;
    }

    public FilteredActionList getFilteredActions(FrameData frameData) {
        State state = frameData.getCharacter(this.playerNumber).getState();
        return getFilteredActions(state, frameData.getCharacter(this.playerNumber).getEnergy());
    }

    public FilteredActionList getFilteredActions(State state, int currentEnergy) {
        Action[] sortedActionList = (state == State.AIR)
                ? this.sortedAirActions
                : this.sortedGroundActions;

        ActionCost[] actionList = (state == State.AIR)
                ? this.sortedAirActionCost
                : this.sortedGroundActionCost;

        int index = Arrays.binarySearch(
                actionList,
                new ActionCost(null, currentEnergy),
                Comparator.comparingInt(a -> a.cost));

        // If exact match not found, binarySearch returns (-(insertion point) - 1)
        if (index < 0) {
            index = -(index + 1) - 1;
        } else {
            // If exact match found, find the last occurrence of that cost
            while (index + 1 < actionList.length && actionList[index + 1].cost <= currentEnergy)  {
                index++;
            }
        }

        // Failsafe to always give neutral action even if they cant do anything else
        if (index == -1) {
            return new FilteredActionList(new Action[] { Action.NEUTRAL }, 1);
        }

        return new FilteredActionList(sortedActionList, index + 1);
    }
}
