package MctsAi23i;

import aiinterface.CommandCenter;
import enumerate.Action;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Random;
import simulator.Simulator;
import struct.CharacterData;
import struct.FrameData;
import struct.GameData;

public class Node {
  public static final int UCT_TIME = 16500000;

  public static final int ITERATION_LIMIT = 23;

  public static final double UCB_C = 3.0D;

  public static final int UCT_TREE_DEPTH = 2;

  public static final int UCT_CREATE_NODE_THRESHOULD = 10;

  public static final int SIMULATION_TIME = 60;

  private Random rnd;

  private Node parent;

  private Node[] children;

  private int depth;

  private int games;

  private double ucb;

  private double score;

  private LinkedList<Action> myActions;

  private LinkedList<Action> oppActions;

  private Simulator simulator;

  private LinkedList<Action> selectedMyActions;

  private int myOriginalHp;

  private int oppOriginalHp;

  private FrameData frameData;

  private boolean playerNumber;

  private CommandCenter commandCenter;

  private GameData gameData;

  private boolean isCreateNode;

  Deque<Action> mAction;

  Deque<Action> oppAction;

  public Node(FrameData frameData, Node parent, LinkedList<Action> myActions, LinkedList<Action> oppActions, GameData gameData, boolean playerNumber, CommandCenter commandCenter, LinkedList<Action> selectedMyActions) {
    this(frameData, parent, myActions, oppActions, gameData, playerNumber, commandCenter);
    this.selectedMyActions = selectedMyActions;
  }

  public Node(FrameData frameData, Node parent, LinkedList<Action> myActions, LinkedList<Action> oppActions, GameData gameData, boolean playerNumber, CommandCenter commandCenter) {
    this.frameData = frameData;
    this.parent = parent;
    this.myActions = myActions;
    this.oppActions = oppActions;
    this.gameData = gameData;
    this.simulator = new Simulator(gameData);
    this.playerNumber = playerNumber;
    this.commandCenter = commandCenter;
    this.selectedMyActions = new LinkedList<>();
    this.rnd = new Random(42L);
    this.mAction = new LinkedList<>();
    this.oppAction = new LinkedList<>();
    CharacterData myCharacter = frameData.getCharacter(playerNumber);
    CharacterData oppCharacter = frameData.getCharacter(!playerNumber);
    this.myOriginalHp = myCharacter.getHp();
    this.oppOriginalHp = oppCharacter.getHp();
    if (this.parent != null) {
      this.depth = this.parent.depth + 1;
    } else {
      this.depth = 0;
    }
  }

  public Action mcts() {
    long start = System.nanoTime();
    for (int i = 0; System.nanoTime() - start <= 16500000L && i < 23; i++)
      uct();
    return getBestVisitAction();
  }

  public double playout() {
    this.mAction.clear();
    this.oppAction.clear();
    int i;
    for (i = 0; i < this.selectedMyActions.size(); i++)
      this.mAction.add(this.selectedMyActions.get(i));
    for (i = 0; i < 5 - this.selectedMyActions.size(); i++)
      this.mAction.add(this.myActions.get(this.rnd.nextInt(this.myActions.size())));
    for (i = 0; i < 5; i++)
      this.oppAction.add(this.oppActions.get(this.rnd.nextInt(this.oppActions.size())));
    FrameData nFrameData =
      this.simulator.simulate(this.frameData, this.playerNumber, this.mAction, this.oppAction, 60);
    return getScore(nFrameData);
  }

  public double uct() {
    Node selectedNode = null;
    double bestUcb = -99999.0D;
    byte b;
    int i;
    Node[] arrayOfNode;
    for (i = (arrayOfNode = this.children).length, b = 0; b < i; ) {
      Node child = arrayOfNode[b];
      if (child.games == 0) {
        child.ucb = (9999 + this.rnd.nextInt(50));
      } else {
        child.ucb = getUcb(child.score / child.games, this.games, child.games);
      }
      if (bestUcb < child.ucb) {
        selectedNode = child;
        bestUcb = child.ucb;
      }
      b++;
    }
    double score = 0.0D;
    if (selectedNode.games == 0) {
      score = selectedNode.playout();
    } else if (selectedNode.children == null) {
      if (selectedNode.depth < 2) {
        if (10 <= selectedNode.games) {
          selectedNode.createNode();
          selectedNode.isCreateNode = true;
          score = selectedNode.uct();
        } else {
          score = selectedNode.playout();
        }
      } else {
        score = selectedNode.playout();
      }
    } else if (selectedNode.depth < 2) {
      score = selectedNode.uct();
    } else {
      selectedNode.playout();
    }
    selectedNode.games++;
    selectedNode.score += score;
    if (this.depth == 0)
      this.games++;
    return score;
  }

  public void createNode() {
    this.children = new Node[this.myActions.size()];
    for (int i = 0; i < this.children.length; i++) {
      LinkedList<Action> my = new LinkedList<>();
      for (Action act : this.selectedMyActions)
        my.add(act);
      my.add(this.myActions.get(i));
      this.children[i] =
        new Node(this.frameData, this, this.myActions, this.oppActions, this.gameData, this.playerNumber, this.commandCenter,
          my);
    }
  }

  public Action getBestVisitAction() {
    int selected = -1;
    double bestGames = -9999.0D;
    for (int i = 0; i < this.children.length; i++) {
      if (bestGames < (this.children[i]).games) {
        bestGames = (this.children[i]).games;
        selected = i;
      }
    }
    return this.myActions.get(selected);
  }

  public Action getBestScoreAction() {
    int selected = -1;
    double bestScore = -9999.0D;
    for (int i = 0; i < this.children.length; i++) {
      double meanScore = (this.children[i]).score / (this.children[i]).games;
      if (bestScore < meanScore) {
        bestScore = meanScore;
        selected = i;
      }
    }
    if (selected == -1) {
        return Action.NEUTRAL;
    }
    return this.myActions.get(selected);
  }

  public int getScore(FrameData fd) {
    return fd.getCharacter(this.playerNumber).getHp() - this.myOriginalHp - fd.getCharacter(!this.playerNumber).getHp() - this.oppOriginalHp;
  }

  public double getUcb(double score, int n, int ni) {
    return score + 3.0D * Math.sqrt(2.0D * Math.log(n) / ni);
  }

  public void printNode(Node node) {
    System.out.println("chinese" + node.games);
    int i;
    for (i = 0; i < node.children.length; i++)
      System.out.println(String.valueOf(i) + ",chineese" + (node.children[i]).games + ",chineese" + (node.children[i]).depth +
          ",score:" + ((node.children[i]).score / (node.children[i]).games) + ",ucb:" +
          (node.children[i]).ucb);
    System.out.println("");
    for (i = 0; i < node.children.length; i++) {
      if ((node.children[i]).isCreateNode)
        printNode(node.children[i]);
    }
  }
}

