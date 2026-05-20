package katai.mcts.mx_mcts_acc;

import aiinterface.AIInterface;
import enumerate.Action;
import katai.mcts.mx_mcts_acc.MCTSNode.Weights;
import katai.mcts.Common;
import katai.mcts.mx_mcts_acc.MCTSNode.AgentConfig;
import katai.mcts.mx_mcts_acc.MCTSNode.HitPointsWeights;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import aiinterface.CommandCenter;
import fighting.Motion;
import simulator.Simulator;
import struct.FrameData;
import struct.GameData;
import struct.Key;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import com.google.gson.Gson;

public class MCTSAgent implements AIInterface {

    private GameData gameData;
    private boolean playerNumber;
    private FrameData frameData;
    private CommandCenter commandCenter;
    private ArrayList<Motion> playerOneMotionList;
    private ArrayList<Motion> playerTwoMotionList;
    private ArrayList<Integer> iterationCounts;
    private Simulator simulator;
    private Common playerOneCommon;
    private Common playerTwoCommon;
    private AgentConfig agentConfig;

    public static List<Action> downActions = Arrays.asList(
            Action.AIR_RECOV,
            Action.STAND_RECOV,
            Action.CROUCH_RECOV,
            Action.CHANGE_DOWN,
            Action.DOWN);

    public static StringBuffer logBuffer;
    public static boolean shouldWriteToBuffer;

    public static void writeToBuffer(String value) {
        if (MCTSAgent.shouldWriteToBuffer) {
            MCTSAgent.logBuffer.append(value);
            MCTSAgent.logBuffer.append("\n");
        }
    }

    @Override
    public int initialize(GameData gameData, boolean playerNumber) {
        // MCTSAgent.logBuffer = new StringBuffer(64_000_000);
        MCTSAgent.logBuffer = new StringBuffer();
        MCTSAgent.shouldWriteToBuffer = false;

        this.gameData = gameData;
        this.playerNumber = playerNumber;

        this.commandCenter = new CommandCenter();
        this.playerOneMotionList = this.gameData.getMotion(true);
        this.playerTwoMotionList = this.gameData.getMotion(false);

        this.iterationCounts = new ArrayList<>();
        this.simulator = new Simulator(gameData);

        this.playerOneCommon = new Common(this.playerOneMotionList, true);
        this.playerTwoCommon = new Common(this.playerTwoMotionList, false);

        this.agentConfig = new AgentConfig();
        String playerNumberStr = this.playerNumber
                ? "P1"
                : "P2";
        String jsonPayload = System.getenv("AGENT_CONFIG_" + playerNumberStr);
        System.out.println("JSON PAYLOAD");
        System.out.println(jsonPayload);
        if (jsonPayload != null && !jsonPayload.isEmpty()) {
            try {
                String parsedJSON = new Gson().fromJson(jsonPayload, String.class);
                this.agentConfig = new Gson().fromJson(parsedJSON, AgentConfig.class);
            } catch (Exception e) {
                System.out.println("Error parsing AGENT_CONFIG" + playerNumberStr);
                e.printStackTrace();
                this.agentConfig = new AgentConfig();
            }
        }

        return 0;
    }

    @Override
    public void getInformation(FrameData frameData, boolean isControl) {
        this.frameData = frameData;
        this.commandCenter.setFrameData(frameData, this.playerNumber);
    }

    @Override
    public void processing() {
        // MCTSAgent.writeToBuffer("In Processing");
        // System.out.println("In processing");
        if (this.frameData.getEmptyFlag() || this.frameData.getRemainingTimeMilliseconds() <= 0) {
            // MCTSAgent.writeToBuffer("Empty, skip");
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

        // TODO: AI told me to not worry about not being in control
        // I guess not that bad because I can just cancel the skill next time I come
        // if (!this.commandCenter.getSkillFlag() &&
        // this.frameData.getCharacter(this.playerNumber).isControl()) {
        if (!this.commandCenter.getSkillFlag()) {
            // MCTSAgent.writeToBuffer("Can do action, running MCTS");
            this.commandCenter.skillCancel();
            Action bestAction = this.runMCTS(this.agentConfig.maxDepth);
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
        return this.commandCenter.getSkillKey();
    }

    @Override
    public void close() {
        if (MCTSAgent.shouldWriteToBuffer) {
            // Writing log buffer to file
            try (BufferedWriter writer = new BufferedWriter(new FileWriter("MCTSAgent_DetailedLog.txt"))) {
                writer.write(MCTSAgent.logBuffer.toString());
                System.out.println("Detailed logs successfully written to MCTSAgent_DetailedLog.txt");
            } catch (IOException e) {
                System.err.println("Failed to write MCTS logs.");
                e.printStackTrace();
            }
        }

        // Nothing I can think of at the moment
        double averageIterationCount = 0;
        for (int iterationCount : this.iterationCounts) {
            averageIterationCount += iterationCount;
        }

        System.out.println("Average iteration count: " +
                String.valueOf(averageIterationCount / this.iterationCounts.size()));
    }

    protected Action runMCTS(int maxDepth) {
        long startTime = System.nanoTime();
        long timeBudget = 16_500_000L + startTime;
        Weights weightsConfig = new Weights(
                new HitPointsWeights(this.agentConfig.hitPointWeightP1, this.agentConfig.hitPointWeightP2),
                0,
                0);

        FrameData newRootNode = this.simulator.simulate(this.frameData, playerNumber, null, null, 14);

        MCTSTree tree = new MCTSTree(
                maxDepth,
                this.simulator,
                new MCTSNode(
                        newRootNode,
                        null,
                        this.playerNumber,
                        Action.NEUTRAL,
                        weightsConfig,
                        this.agentConfig),
                this.playerOneCommon,
                this.playerTwoCommon,
                this.playerOneMotionList,
                this.playerTwoMotionList,
                weightsConfig);

        long runTime = System.nanoTime();

        int iterationCount = 0;
        while (runTime < timeBudget) {
            tree.iteration(maxDepth);
            iterationCount++;
            runTime = System.nanoTime();
        }

        this.iterationCounts.add(iterationCount);
        MCTSNode bestChild = tree.getBestChildNode();

        if (bestChild == null) {
            return Action.NEUTRAL;
        } else {
            return bestChild.actionSequence[0];
        }
    }

}
