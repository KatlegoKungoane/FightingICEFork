package MctsAi23i;

import aiinterface.AIInterface;
import aiinterface.CommandCenter;
import enumerate.Action;
import enumerate.State;
import java.util.ArrayList;
import java.util.LinkedList;
import simulator.Simulator;
import struct.CharacterData;
import struct.FrameData;
import struct.GameData;
import struct.Key;
import struct.MotionData;

public class MctsAi23i implements AIInterface {
  private Simulator simulator;

  private Key key;

  private CommandCenter commandCenter;

  private boolean playerNumber;

  private GameData gameData;

  private FrameData frameData;

  private FrameData simulatorAheadFrameData;

  private LinkedList<Action> myActions;

  private LinkedList<Action> oppActions;

  private CharacterData myCharacter;

  private CharacterData oppCharacter;

  private static final int FRAME_AHEAD = 14;

  private ArrayList<MotionData> myMotion;

  private ArrayList<MotionData> oppMotion;

  private Action[] actionAir;

  private Action[] actionGround;

  private Action spSkill;

  private Node rootNode;

  public static final boolean DEBUG_MODE = false;

  public void close() {}

  public int initialize(GameData gameData, boolean playerNumber) {
    this.playerNumber = playerNumber;
    this.gameData = gameData;
    this.key = new Key();
    this.frameData = new FrameData();
    this.commandCenter = new CommandCenter();
    this.myActions = new LinkedList<>();
    this.oppActions = new LinkedList<>();
    this.simulator = gameData.getSimulator();
    this.actionAir = new Action[] {
        Action.AIR_GUARD, Action.AIR_A, Action.AIR_B, Action.AIR_DA, Action.AIR_DB,
        Action.AIR_FA, Action.AIR_FB, Action.AIR_UA, Action.AIR_UB, Action.AIR_D_DF_FA,
        Action.AIR_D_DF_FB,
        Action.AIR_F_D_DFA, Action.AIR_F_D_DFB, Action.AIR_D_DB_BA, Action.AIR_D_DB_BB };
    this.actionGround = new Action[] {
        Action.STAND_D_DB_BA, Action.BACK_STEP, Action.FORWARD_WALK, Action.DASH,
        Action.JUMP, Action.FOR_JUMP, Action.BACK_JUMP, Action.STAND_GUARD, Action.CROUCH_GUARD, Action.THROW_A,
        Action.THROW_B, Action.STAND_A, Action.STAND_B, Action.CROUCH_A, Action.CROUCH_B, Action.STAND_FA,
        Action.STAND_FB, Action.CROUCH_FA, Action.CROUCH_FB, Action.STAND_D_DF_FA,
        Action.STAND_D_DF_FB,
        Action.STAND_F_D_DFA, Action.STAND_F_D_DFB, Action.STAND_D_DB_BB };
    this.spSkill = Action.STAND_D_DF_FC;
    this.myMotion = gameData.getMotionData(this.playerNumber);
    this.oppMotion = gameData.getMotionData(!this.playerNumber);
    return 0;
  }

  public Key input() {
    return this.key;
  }

  public void processing() {
    if (canProcessing())
      if (this.commandCenter.getSkillFlag()) {
        this.key = this.commandCenter.getSkillKey();
      } else {
        this.key.empty();
        this.commandCenter.skillCancel();
        mctsPrepare();
        this.rootNode = new Node(this.simulatorAheadFrameData, null, this.myActions, this.oppActions, this.gameData, this.playerNumber,
            this.commandCenter);
        this.rootNode.createNode();
        Action bestAction = this.rootNode.mcts();
        this.commandCenter.commandCall(bestAction.name());
      }
  }

  public boolean canProcessing() {
    return (!this.frameData.getEmptyFlag() && this.frameData.getRemainingFramesNumber() > 0);
  }

  public void mctsPrepare() {
    this.simulatorAheadFrameData = this.simulator.simulate(this.frameData, this.playerNumber, null, null, 14);
    this.myCharacter = this.simulatorAheadFrameData.getCharacter(this.playerNumber);
    this.oppCharacter = this.simulatorAheadFrameData.getCharacter(!this.playerNumber);
    setMyAction();
    setOppAction();
  }

  public void setMyAction() {
    this.myActions.clear();
    int energy = this.myCharacter.getEnergy();
    if (this.myCharacter.getState() == State.AIR) {
      for (int i = 0; i < this.actionAir.length; i++) {
        if (Math.abs(((MotionData)this.myMotion.get(Action.valueOf(this.actionAir[i].name()).ordinal()))
            .getAttackStartAddEnergy()) <= energy)
          this.myActions.add(this.actionAir[i]);
      }
      if (this.myActions.isEmpty())
        this.myActions.add(Action.AIR_GUARD);
    } else {
      if (Math.abs((
          (MotionData)this.myMotion.get(Action.valueOf(this.spSkill.name()).ordinal())).getAttackStartAddEnergy()) <= energy)
        this.myActions.add(this.spSkill);
      for (int i = 0; i < this.actionGround.length; i++) {
        if (Math.abs(((MotionData)this.myMotion.get(Action.valueOf(this.actionGround[i].name()).ordinal()))
            .getAttackStartAddEnergy()) <= energy)
          this.myActions.add(this.actionGround[i]);
      }
      if (this.myActions.isEmpty())
        this.myActions.add(Action.STAND_GUARD);
    }
  }

  public void setOppAction() {
    this.oppActions.clear();
    int energy = this.oppCharacter.getEnergy();
    if (this.oppCharacter.getState() == State.AIR) {
      for (int i = 0; i < this.actionAir.length; i++) {
        if (Math.abs(((MotionData)this.oppMotion.get(Action.valueOf(this.actionAir[i].name()).ordinal()))
            .getAttackStartAddEnergy()) <= energy)
          this.oppActions.add(this.actionAir[i]);
      }
      if (this.oppActions.isEmpty())
        this.oppActions.add(Action.AIR_GUARD);
    } else {
      if (Math.abs(((MotionData)this.oppMotion.get(Action.valueOf(this.spSkill.name()).ordinal()))
          .getAttackStartAddEnergy()) <= energy)
        this.oppActions.add(this.spSkill);
      for (int i = 0; i < this.actionGround.length; i++) {
        if (Math.abs(((MotionData)this.oppMotion.get(Action.valueOf(this.actionGround[i].name()).ordinal()))
            .getAttackStartAddEnergy()) <= energy)
          this.oppActions.add(this.actionGround[i]);
      }
      if (this.oppActions.isEmpty())
        this.oppActions.add(Action.STAND_GUARD);
    }
  }

  public void roundEnd(int p1Hp, int p2Hp, int frames) {}

  public void getInformation(FrameData frameData, boolean arg1) {
    this.frameData = frameData;
    this.commandCenter.setFrameData(this.frameData, this.playerNumber);
    this.myCharacter = frameData.getCharacter(this.playerNumber);
    this.oppCharacter = frameData.getCharacter(!this.playerNumber);
  }

  public void getInformation(FrameData arg0) {}
}
