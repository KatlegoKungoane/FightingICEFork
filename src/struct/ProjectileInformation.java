package struct;

public class ProjectileInformation {
    public int playerNumber;
    public HitArea hitArea;
    public Vector speed;

    public ProjectileInformation(int playerNumber, HitArea hitArea, Vector speed) {
        this.playerNumber = playerNumber;
        this.hitArea = new HitArea(hitArea);
        this.speed = new Vector(speed);
    }
}