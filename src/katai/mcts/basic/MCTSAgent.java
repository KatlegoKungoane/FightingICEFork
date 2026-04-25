package katai.mcts.basic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import aiinterface.AIInterface;
import aiinterface.CommandCenter;
import enumerate.Action;
import enumerate.State;
import fighting.Motion;
import simulator.Simulator;
import struct.FrameData;
import struct.GameData;
import struct.Key;

public class MCTSAgent implements AIInterface {
    private GameData gameData;
    private boolean playerNumber;
    private FrameData frameData;
    private CommandCenter commandCenter;
    private ArrayList<Motion> playerOneMotionList;
    private ArrayList<Motion> playerTwoMotionList;
    private ArrayList<Integer> iterationCounts;
    public Simulator simulator;
    public Common playerOneCommon;
    public Common playerTwoCommon;

    public static List<Action> downActions = Arrays.asList(
            Action.AIR_RECOV,
            Action.STAND_RECOV,
            Action.CROUCH_RECOV,
            Action.CHANGE_DOWN,
            Action.DOWN);

    @Override
    public int initialize(GameData gameData, boolean playerNumber) {
        this.gameData = gameData;
        this.playerNumber = playerNumber;

        this.commandCenter = new CommandCenter();
        this.playerOneMotionList = this.gameData.getMotion(true);
        this.playerTwoMotionList = this.gameData.getMotion(false);

        this.iterationCounts = new ArrayList<>();
        this.simulator = new Simulator(gameData);

        this.playerOneCommon = new Common(this.playerOneMotionList, true);
        this.playerTwoCommon = new Common(this.playerTwoMotionList, false);
        return 0;
    }

    @Override
    public void getInformation(FrameData frameData, boolean isControl) {
        this.frameData = frameData;
        this.commandCenter.setFrameData(frameData, this.playerNumber);
    }

    @Override
    public void processing() {
        // System.out.println("In processing");
        if (this.frameData.getEmptyFlag() || this.frameData.getRemainingTimeMilliseconds() <= 0) {
            return;
        }

        // if (!this.commandCenter.getSkillFlag() ||
        // !this.frameData.getCharacter(this.playerNumber).isControl()) {
        // Action currentAction =
        // this.frameData.getCharacter(this.playerNumber).getAction();
        // if (MCTSAgent.downActions.contains(currentAction)) {
        // commandCenter.skillCancel();
        // }
        // }

        if (!this.commandCenter.getSkillFlag() && this.frameData.getCharacter(this.playerNumber).isControl()) {
            Action bestAction = this.runMCTS(0);
            this.commandCenter.commandCall(bestAction.name());
        }

        // if (!this.commandCenter.getSkillFlag()) {
        // System.out.println("skill key not empty" +
        // String.valueOf(this.commandCenter.skillKey.size()));
        // for (Key k : this.commandCenter.skillKey) {
        // System.out.println(k.toString());
        // }
        // }
    }

    @Override
    public Key input() {
        Key key = this.commandCenter.getSkillKey();
        return key;
        // Key k = new Key();
        // if (this.frameData.getFramesNumber() % 60 < 30) {
        // k.A = true;
        // }
        // return k;
    }

    @Override
    public void close() {
        // Nothing I can think of at the moment
        double averageIterationCount = 0;
        for (int iterationCount : this.iterationCounts) {
            averageIterationCount += iterationCount;
        }

        System.out.println("Average iteration count: " +
                String.valueOf(averageIterationCount / this.iterationCounts.size()));
    }

    private Action runMCTS(int maxDepth) {
        long startTime = System.nanoTime();
        long timeBudget = 10 * 1_000_000L + startTime;

        MCTSTree tree = new MCTSTree(
                maxDepth,
                this.simulator,
                new MCTSNode(this.frameData, null, this.playerNumber, null),
                this.playerOneCommon,
                this.playerTwoCommon,
                this.playerOneMotionList,
                this.playerTwoMotionList);

        long runTime = System.nanoTime();

        int iterationCount = 0;
        while (runTime < timeBudget) {
            tree.iteration(maxDepth);
            iterationCount++;
            runTime = System.nanoTime();
        }

        this.iterationCounts.add(iterationCount);
        return tree.getBestAction();
    }

}
