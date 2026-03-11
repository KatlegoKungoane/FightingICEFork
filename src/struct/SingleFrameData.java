package struct;

import java.util.Deque;

import setting.ResearchSettings;

/**
 * This is going to be based on what I mentioned in my proposal.
 * Will investigate the other information like the remaining frames and all
 * that.
 * 
 */
public class SingleFrameData {
    public int frame;
    public int[] hitPoints;
    public int[] energy;
    public HitArea[] attackHitBoxes;
    public Vector[] characterSpeeds;
    public ProjectileInformation[] projectileInformation;
    public HitArea[] characterHitBoxes;

    public SingleFrameData(FrameData frameData) {
        this.frame = frameData.getFramesNumber();

        CharacterData playerOne = frameData.getCharacter(0);
        CharacterData playerTwo = frameData.getCharacter(1);

        this.hitPoints = new int[] {
                playerOne.getHp(),
                playerTwo.getHp()
        };

        this.energy = new int[] {
                playerOne.getEnergy(),
                playerTwo.getEnergy()
        };

        this.characterSpeeds = new Vector[] {
                new Vector(playerOne.getSpeedX(), playerOne.getSpeedY()),
                new Vector(playerTwo.getSpeedX(), playerTwo.getSpeedY()),
        };

        this.characterHitBoxes = new HitArea[] {
                new HitArea(playerOne.getHitArea()),
                new HitArea(playerTwo.getHitArea())
        };

        this.attackHitBoxes = new HitArea[] { null, null };
        if (playerOne.getAttack() != null && playerOne.getAttack().isLive()) {
            attackHitBoxes[0] = new HitArea(playerOne.getAttack().getCurrentHitArea());
        }
        if (playerTwo.getAttack() != null && playerTwo.getAttack().isLive()) {
            attackHitBoxes[1] = new HitArea(playerTwo.getAttack().getCurrentHitArea());
        }

        this.projectileInformation = new ProjectileInformation[ResearchSettings.PROJECTILE_COUNT * 2];

        int count = 0;
        Deque<AttackData> playerOneProjectiles = frameData.getProjectilesByP1();
        for (AttackData projectile : playerOneProjectiles) {
            this.projectileInformation[count++] = new ProjectileInformation(
                    0,
                    projectile.getCurrentHitArea(),
                    new Vector(projectile.getSpeedX(), projectile.getSpeedY()));

            if (count >= ResearchSettings.PROJECTILE_COUNT) {
                break;
            }
        }

        count = 0;
        Deque<AttackData> playerTwoProjectiles = frameData.getProjectilesByP2();
        for (AttackData projectile : playerTwoProjectiles) {
            this.projectileInformation[ResearchSettings.PROJECTILE_COUNT + count++] = new ProjectileInformation(
                    1,
                    projectile.getCurrentHitArea(),
                    new Vector(projectile.getSpeedX(), projectile.getSpeedY()));

            if (count >= ResearchSettings.PROJECTILE_COUNT) {
                break;
            }
        }
    }
}